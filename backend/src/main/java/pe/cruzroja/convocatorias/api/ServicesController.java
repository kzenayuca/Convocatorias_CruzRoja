package pe.cruzroja.convocatorias.api;

import static pe.cruzroja.convocatorias.api.Store.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pe.cruzroja.convocatorias.domain.ServiceRules;

@RestController
@RequestMapping("/api/v1")
public class ServicesController {
    private final JdbcTemplate db;
    private final ServiceMaintenance maintenance;
    public ServicesController(JdbcTemplate db, ServiceMaintenance maintenance) { this.db=db; this.maintenance=maintenance; }

    public record Callout(@NotBlank @Size(max=150) String title, @NotBlank @Size(max=5000) String description,
                          @NotNull LocalDateTime start, @NotNull LocalDateTime end, @NotNull LocalDateTime deadline,
                          @NotBlank @Size(max=200) String location, @NotBlank @Size(max=5000) String requirements,
                          @NotBlank String level, @Min(1) int capacity, @Min(1) int minimum,
                          @NotBlank String kind, @NotBlank @Size(max=120) String area,
                          @NotBlank @Size(max=200) String contact) { }
    public record Status(@NotBlank String status) { }
    public record EnrollmentRequest(boolean requirementsAccepted, UUID replacementFor) { }
    public record Review(boolean approved, @NotBlank @Size(max=2000) String comment) { }
    public record Material(@NotBlank @Size(max=160) String name, @Min(1) int quantity,
                           @NotBlank @Size(max=3000) String details) { }
    public record Operation(@NotBlank String action, @NotBlank @Size(max=3000) String observations) { }

    private static final String CALLS = """
        SELECT c.id,c.title,c.description,c.start_at,c.end_at,c.deadline,c.location,c.requirements,c.minimum_level,
        c.capacity,c.minimum_participants,c.status,c.kind,c.area,c.contact,c.responsible_id,c.opened_at,c.closed_at,c.observations,
        (SELECT count(*) FROM enrollments e WHERE e.callout_id=c.id AND e.status='CONFIRMADA') AS enrolled
        FROM callouts c
        """;

    Map<String,Object> locked(UUID id) { return one(db, "SELECT * FROM callouts WHERE id=? FOR UPDATE", id); }
    void manage(Map<String,Object> c) {
        require(chief() || manager() && actor().id().equals(c.get("responsible_id")), "Solo jefatura o el reclutador graduado responsable pueden gestionar este servicio.");
    }
    void visible(Map<String,Object> c) {
        boolean participant = db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND volunteer_id=?", Integer.class, c.get("id"), actor().id()) > 0;
        boolean past = !time(c,"end_at").isAfter(now());
        boolean attended = db.queryForObject("SELECT count(*) FROM attendance_records WHERE callout_id=? AND volunteer_id=? AND check_in IS NOT NULL", Integer.class, c.get("id"), actor().id()) > 0;
        require(chief() || actor().id().equals(c.get("responsible_id")) || (past ? attended : participant || !"BORRADOR".equals(c.get("status"))), "No tienes acceso a esta convocatoria.");
    }

    @GetMapping("/callouts")
    public List<Map<String,Object>> callouts(@RequestParam(defaultValue="false") boolean history) {
        maintenance.refresh();
        var rows = db.queryForList(CALLS + " ORDER BY c.start_at,c.id");
        return rows.stream().filter(c -> {
            if (history != !time(c,"end_at").isAfter(now())) return false;
            if (chief() || actor().id().equals(c.get("responsible_id"))) return true;
            if (history) return db.queryForObject("SELECT count(*) FROM attendance_records WHERE callout_id=? AND volunteer_id=? AND check_in IS NOT NULL", Integer.class, c.get("id"), actor().id()) > 0;
            return !c.get("status").equals("BORRADOR") && compatible(c, actor().id());
        }).toList();
    }

    @GetMapping("/callouts/{id}") public Map<String,Object> detail(@PathVariable UUID id) {
        maintenance.refresh(); var c=one(db,CALLS+" WHERE c.id=?",id); visible(c); return c;
    }

    boolean compatible(Map<String,Object> c, UUID volunteer) {
        var rows=db.queryForList("SELECT training_level FROM volunteer_profiles WHERE user_id=?",volunteer);
        var levels=List.of("BASICO","INTERMEDIO","AVANZADO");
        return !rows.isEmpty() && levels.indexOf(rows.get(0).get("training_level")) >= levels.indexOf(c.get("minimum_level"));
    }

    @PostMapping("/callouts") @Transactional
    public Map<String,UUID> create(@Valid @RequestBody Callout r) {
        require(manager(),"No puedes crear convocatorias."); validateCall(r);
        var id=UUID.randomUUID();
        db.update("""
            INSERT INTO callouts(id,title,description,start_at,end_at,deadline,location,requirements,minimum_level,
            capacity,minimum_participants,status,kind,area,contact,responsible_id)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,'BORRADOR',?,?,?,?)
            """,id,r.title(),r.description(),r.start(),r.end(),r.deadline(),r.location(),r.requirements(),r.level(),r.capacity(),r.minimum(),r.kind(),r.area(),r.contact(),actor().id());
        return Map.of("id",id);
    }
    private void validateCall(Callout r) {
        valid(r.end().isAfter(r.start()) && r.start().isAfter(now()),"El horario debe ser futuro y la salida posterior al inicio.");
        valid(!r.deadline().isAfter(r.start()) && r.deadline().isAfter(now()),"El cierre de inscripciones debe ser futuro y no posterior al inicio.");
        valid(r.minimum()<=r.capacity(),"El mínimo no puede superar las vacantes.");
        valid(Set.of("BASICO","INTERMEDIO","AVANZADO").contains(r.level()),"Nivel no válido.");
        valid(Set.of("SERVICIO","INGRESO").contains(r.kind()),"Tipo no válido.");
    }
    @PutMapping("/callouts/{id}") @Transactional
    public void edit(@PathVariable UUID id,@Valid @RequestBody Callout r) {
        var c=locked(id); manage(c); validateCall(r);
        valid(Set.of("BORRADOR","PUBLICADA","CONFIRMADA").contains(c.get("status")) && time(c,"start_at").isAfter(now()),"Solo se edita una convocatoria futura que no esté cancelada.");
        int participants=db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND status IN ('CONFIRMADA','REEMPLAZADA','PENDIENTE')",Integer.class,id);
        if(participants>0) valid(r.start().equals(time(c,"start_at")) && r.end().equals(time(c,"end_at")) && r.kind().equals(c.get("kind")) && r.level().equals(c.get("minimum_level")) && r.requirements().equals(c.get("requirements")),"Con postulantes no se cambia horario, tipo ni requisitos. Cancela y crea otra convocatoria para esos cambios.");
        int confirmed=db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND status='CONFIRMADA'",Integer.class,id);
        valid(r.capacity()>=confirmed,"Las vacantes no pueden ser menores que los confirmados.");
        valid(!text(c,"status").equals("CONFIRMADA") || r.minimum()<=confirmed,"El nuevo mínimo dejaría el servicio confirmado sin participantes suficientes.");
        db.update("UPDATE callouts SET title=?,description=?,start_at=?,end_at=?,deadline=?,location=?,requirements=?,minimum_level=?,capacity=?,minimum_participants=?,kind=?,area=?,contact=? WHERE id=?",
                r.title(),r.description(),r.start(),r.end(),r.deadline(),r.location(),r.requirements(),r.level(),r.capacity(),r.minimum(),r.kind(),r.area(),r.contact(),id);
        for(var e:db.queryForList("SELECT volunteer_id FROM enrollments WHERE callout_id=? AND status IN ('PENDIENTE','CONFIRMADA')",id))
            Store.notify(db,id(e,"volunteer_id"),"CONVOCATORIA","Datos actualizados: "+r.title(),"/services");
    }
    @PostMapping("/callouts/{id}/status") @Transactional
    public void status(@PathVariable UUID id,@Valid @RequestBody Status r) {
        var c=locked(id); manage(c); var previous=text(c,"status");
        valid(time(c,"end_at").isAfter(now()),"El servicio ya terminó.");
        if (r.status().equals("PUBLICADA")) valid(previous.equals("BORRADOR"),"Solo se publica un borrador.");
        else if (r.status().equals("CONFIRMADA")) {
            valid(previous.equals("PUBLICADA"),"Solo se confirma una convocatoria publicada.");
            long count=db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND status='CONFIRMADA'",Long.class,id);
            valid(count>=((Number)c.get("minimum_participants")).intValue(),"Faltan " + (((Number)c.get("minimum_participants")).intValue()-count) + " participantes confirmados.");
        } else valid(r.status().equals("CANCELADA") && Set.of("BORRADOR","PUBLICADA","CONFIRMADA").contains(previous),"Transición de estado no válida.");
        db.update("UPDATE callouts SET status=? WHERE id=?",r.status(),id);
        for(var e:db.queryForList("SELECT * FROM enrollments WHERE callout_id=? AND status IN ('PENDIENTE','CONFIRMADA')",id))
            Store.notify(db,id(e,"volunteer_id"),"CONVOCATORIA",text(c,"title")+": "+r.status(),"/services");
        if(r.status().equals("CANCELADA")) db.update("UPDATE enrollments SET status='CANCELADA',comment='Servicio cancelado' WHERE callout_id=? AND status IN ('PENDIENTE','CONFIRMADA','REEMPLAZADA')",id);
    }

    @PostMapping("/callouts/{id}/enrollments") @Transactional
    public void enroll(@PathVariable UUID id,@RequestBody EnrollmentRequest r) {
        var c=locked(id);
        valid(r.requirementsAccepted(),"Debes aceptar todos los requisitos.");
        valid(Set.of("PUBLICADA","CONFIRMADA").contains(c.get("status")),"La convocatoria no admite solicitudes.");
        valid(time(c,"start_at").isAfter(now()) && (time(c,"deadline")==null || time(c,"deadline").isAfter(now())),"El plazo de inscripción terminó.");
        valid(compatible(c,actor().id()),"Tu nivel no cumple los requisitos.");
        valid(r.replacementFor()!=null || db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND status='CONFIRMADA'",Integer.class,id)<((Number)c.get("capacity")).intValue(),"No quedan vacantes.");
        valid(db.queryForObject("SELECT count(*) FROM enrollments e JOIN callouts c ON c.id=e.callout_id WHERE e.volunteer_id=? AND e.status='CONFIRMADA' AND c.start_at<? AND c.end_at>?",Integer.class,actor().id(),time(c,"end_at"),time(c,"start_at"))==0,"Tienes otro servicio confirmado en ese horario.");
        if(r.replacementFor()!=null) {
            var target=one(db,"SELECT * FROM enrollments WHERE id=? AND callout_id=? AND status='CONFIRMADA'",r.replacementFor(),id);
            valid(!actor().id().equals(target.get("volunteer_id")),"No puedes reemplazarte a ti mismo.");
        }
        var old=db.queryForList("SELECT * FROM enrollments WHERE volunteer_id=? AND callout_id=?",actor().id(),id);
        valid(old.isEmpty() || Set.of("EXPIRADA","RECHAZADA","CANCELADA").contains(old.get(0).get("status")),"Ya tienes una solicitud en esta convocatoria.");
        if(old.isEmpty()) db.update("INSERT INTO enrollments(id,volunteer_id,callout_id,status,replacement_for) VALUES (?,?,?,'PENDIENTE',?)",UUID.randomUUID(),actor().id(),id,r.replacementFor());
        else db.update("UPDATE enrollments SET status='PENDIENTE',created_at=?,reviewed_at=NULL,comment=NULL,replacement_for=? WHERE id=?",now(),r.replacementFor(),old.get(0).get("id"));
        Store.notify(db,actor().id(),"INSCRIPCION","Solicitud pendiente: "+c.get("title"),"/services");
    }

    @GetMapping("/enrollments") public List<Map<String,Object>> mine() {
        maintenance.refresh();
        return db.queryForList("SELECT e.*,c.title,c.start_at,c.end_at,c.kind FROM enrollments e JOIN callouts c ON c.id=e.callout_id WHERE e.volunteer_id=? ORDER BY c.start_at DESC",actor().id());
    }
    @GetMapping("/callouts/{id}/enrollments") public List<Map<String,Object>> applicants(@PathVariable UUID id) {
        var c=one(db,"SELECT * FROM callouts WHERE id=?",id); manage(c); maintenance.refresh();
        return db.queryForList("SELECT e.*,u.name,u.surnames,p.training_level FROM enrollments e JOIN users u ON u.id=e.volunteer_id LEFT JOIN volunteer_profiles p ON p.user_id=u.id WHERE e.callout_id=? ORDER BY e.created_at,e.id",id);
    }
    @PostMapping("/enrollments/{id}/review") @Transactional
    public void review(@PathVariable UUID id,@Valid @RequestBody Review r) {
        var initial=one(db,"SELECT callout_id FROM enrollments WHERE id=?",id);
        var c=locked(id(initial,"callout_id")); manage(c);
        var e=one(db,"SELECT * FROM enrollments WHERE id=? FOR UPDATE",id);
        valid(e.get("status").equals("PENDIENTE"),"La solicitud ya fue resuelta.");
        valid(now().isBefore(time(c,"start_at")) && now().isBefore(ServiceRules.reviewDeadline(time(e,"created_at"))),"El plazo de revisión terminó.");
        if(r.approved()) {
            valid(Set.of("PUBLICADA","CONFIRMADA").contains(c.get("status")),"Convocatoria no disponible.");
            valid(db.queryForObject("SELECT status='ACTIVO' FROM users WHERE id=?",Boolean.class,e.get("volunteer_id")),"La cuenta no está activa.");
            valid(compatible(c,id(e,"volunteer_id")),"El perfil no es compatible.");
            if(e.get("replacement_for")!=null) {
                var target=one(db,"SELECT * FROM enrollments WHERE id=? AND callout_id=?",e.get("replacement_for"),c.get("id"));
                valid(text(target,"status").equals("CONFIRMADA"),"La inscripción a reemplazar ya no está confirmada.");
            } else valid(db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND status='CONFIRMADA'",Integer.class,c.get("id"))<((Number)c.get("capacity")).intValue(),"No quedan vacantes.");
            // Serializa aprobaciones del mismo voluntario en distintos servicios.
            one(db,"SELECT user_id FROM volunteer_profiles WHERE user_id=? FOR UPDATE",e.get("volunteer_id"));
            valid(db.queryForObject("SELECT count(*) FROM enrollments e JOIN callouts c ON c.id=e.callout_id WHERE e.volunteer_id=? AND e.status='CONFIRMADA' AND c.start_at<? AND c.end_at>?",Integer.class,e.get("volunteer_id"),time(c,"end_at"),time(c,"start_at"))==0,"El voluntario tiene un cruce de horario.");
            if(e.get("replacement_for")!=null) valid(db.queryForObject("SELECT count(*) FROM enrollments WHERE replacement_for=? AND status='CONFIRMADA'",Integer.class,e.get("replacement_for"))==0,"El participante ya tiene un reemplazo confirmado.");
        }
        db.update("UPDATE enrollments SET status=?,comment=?,reviewed_at=? WHERE id=?",r.approved()?"CONFIRMADA":"RECHAZADA",r.comment(),now(),id);
        if(r.approved() && e.get("replacement_for")!=null) db.update("UPDATE enrollments SET status='REEMPLAZADA',comment='Reemplazo aceptado por el responsable' WHERE id=?",e.get("replacement_for"));
        Store.notify(db,id(e,"volunteer_id"),"INSCRIPCION",c.get("title")+": "+(r.approved()?"CONFIRMADA":"RECHAZADA")+". "+r.comment(),"/services");
    }

    @PostMapping("/callouts/{id}/operation") @Transactional
    public void operation(@PathVariable UUID id,@Valid @RequestBody Operation r) {
        var c=locked(id); manage(c);
        valid(text(c,"kind").equals("SERVICIO"),"Las convocatorias de ingreso no registran horas.");
        if(r.action().equals("ABRIR")) {
            valid(c.get("team_photo")!=null,"Adjunta primero la foto del equipo.");
            valid(c.get("opened_at")==null && c.get("closed_at")==null && Set.of("PUBLICADA","CONFIRMADA").contains(c.get("status")),"El servicio no puede abrirse.");
            valid(!now().isBefore(time(c,"start_at").minusHours(1)) && now().isBefore(time(c,"end_at")),"Abre el servicio dentro de la hora previa a su inicio.");
            valid(db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND status='CONFIRMADA'",Integer.class,id)>=((Number)c.get("minimum_participants")).intValue(),"No se alcanzó el mínimo de participantes.");
            db.update("UPDATE callouts SET opened_at=?,status='CONFIRMADA',observations=? WHERE id=?",now(),r.observations(),id);
        } else {
            valid(r.action().equals("CERRAR") && c.get("opened_at")!=null && c.get("closed_at")==null,"Acción no válida.");
            valid(!now().isBefore(time(c,"end_at")),"No se cierra antes de terminar el horario autorizado.");
            db.update("UPDATE callouts SET closed_at=?,status='FINALIZADA',observations=? WHERE id=?",now(),r.observations(),id);
        }
    }

    @PostMapping("/callouts/{id}/photo") @Transactional
    public void photo(@PathVariable UUID id,@RequestParam org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        var c=locked(id); manage(c); valid(c.get("closed_at")==null,"El servicio ya se cerró.");
        byte[] bytes=file.getBytes();
        valid(bytes.length>8 && bytes.length<=2*1024*1024,"La foto debe pesar como máximo 2 MB.");
        boolean png=bytes[0]==(byte)137 && bytes[1]==80 && bytes[2]==78 && bytes[3]==71;
        boolean jpg=bytes[0]==(byte)255 && bytes[1]==(byte)216 && bytes[2]==(byte)255;
        valid(png || jpg,"Solo se admiten imágenes JPG o PNG.");
        valid(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes))!=null,"La imagen no es válida.");
        db.update("UPDATE callouts SET team_photo=?,photo_type=? WHERE id=?",bytes,png?"image/png":"image/jpeg",id);
    }
    @GetMapping("/callouts/{id}/photo")
    public org.springframework.http.ResponseEntity<byte[]> photo(@PathVariable UUID id) {
        var c=one(db,"SELECT * FROM callouts WHERE id=?",id); manage(c);
        if(c.get("team_photo")==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"No hay foto del equipo.");
        return org.springframework.http.ResponseEntity.ok().header("Cache-Control","no-store").contentType(org.springframework.http.MediaType.parseMediaType(text(c,"photo_type"))).body((byte[])c.get("team_photo"));
    }

    @GetMapping("/callouts/{id}/materials") public List<Map<String,Object>> materials(@PathVariable UUID id) {
        var c=one(db,"SELECT * FROM callouts WHERE id=?",id); manage(c);
        return db.queryForList("SELECT * FROM material_requests WHERE callout_id=? ORDER BY created_at",id);
    }
    @PostMapping("/callouts/{id}/materials") @Transactional
    public void material(@PathVariable UUID id,@Valid @RequestBody Material r) {
        var c=locked(id); manage(c); valid(time(c,"end_at").isAfter(now()),"El servicio ya terminó.");
        db.update("INSERT INTO material_requests(id,callout_id,requested_by,name,quantity,details) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),id,actor().id(),r.name(),r.quantity(),r.details());
    }
}
