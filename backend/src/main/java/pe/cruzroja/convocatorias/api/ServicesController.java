package pe.cruzroja.convocatorias.api;

import static pe.cruzroja.convocatorias.api.Store.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ServicesController {
    private final JdbcTemplate db;
    private final ServiceMaintenance maintenance;
    private final CalloutWorkflow workflow;
    public ServicesController(JdbcTemplate db, ServiceMaintenance maintenance, CalloutWorkflow workflow) {
        this.db=db; this.maintenance=maintenance; this.workflow=workflow;
    }
    public record Callout(@NotBlank @Size(max=150) String title, @NotBlank @Size(max=5000) String description,
                          @NotNull LocalDateTime start, @NotNull LocalDateTime end, @NotNull LocalDateTime deadline,
                          @NotBlank @Size(max=200) String location, @NotBlank @Size(max=5000) String requirements,
                          @NotBlank String level, @Min(1) @Max(10000) int socorristaSlots,
                          @Min(0) @Max(1) int radioSlots, @Min(0) @Max(1) int pilotSlots,
                          @NotBlank String kind, @NotBlank @Size(max=120) String area,
                          @NotBlank @Size(max=200) String contact, @NotBlank @Size(max=40) String code,
                          @NotBlank @Size(max=100) String serviceType) { }
    public record Status(@NotBlank String status) { }
    public record EnrollmentRequest(boolean requirementsAccepted, @NotBlank String position, UUID replacementFor) { }
    public record Review(boolean approved, @NotBlank @Size(max=2000) String comment) { }
    public record Reason(@NotBlank @Size(max=2000) String reason) { }
    public record Operation(@NotBlank String action, @NotBlank @Size(max=3000) String observations) { }

    private static final String CALLS="""
        SELECT c.id,c.title,c.description,c.start_at,c.end_at,c.deadline,c.location,c.requirements,c.minimum_level,
        c.status,c.kind,c.area,c.contact,c.responsible_id,c.created_by,c.opened_at,c.closed_at,c.observations,
        c.service_code,c.service_type,c.socorrista_slots,c.radio_slots,c.pilot_slots,c.planned_hours,
        u.name AS responsible_name FROM callouts c LEFT JOIN users u ON u.id=c.responsible_id
        """;
    private Map<String,Object> presentation(Map<String,Object> c) {
        c.put("positions",workflow.positions(c));
        c.put("can_edit",workflow.canEdit(c)); c.put("can_operate",workflow.canOperate(c));
        c.put("can_view_team",workflow.canEdit(c)||workflow.canOperate(c));
        c.put("eligible_positions",CalloutWorkflow.POSITIONS.stream().filter(p -> workflow.qualified(c,actor().id(),p)).toList());
        return c;
    }
    private boolean participated(UUID id) {
        return db.queryForObject("SELECT count(*) FROM attendance_records WHERE callout_id=? AND volunteer_id=? AND check_in IS NOT NULL",Integer.class,id,actor().id())>0;
    }
    @GetMapping("/callouts")
    public List<Map<String,Object>> callouts(@RequestParam(defaultValue="false") boolean history) {
        maintenance.refresh();
        return db.queryForList(CALLS+" ORDER BY c.start_at,c.id").stream().filter(c -> {
            if(history != !time(c,"end_at").isAfter(now())) return false;
            if(chief() || actor().id().equals(c.get("created_by")) || actor().id().equals(c.get("responsible_id"))) return true;
            if(history) return participated(id(c,"id"));
            return Set.of("ABIERTA","CERRADA").contains(c.get("status")) && workflow.compatible(c,actor().id());
        }).map(this::presentation).toList();
    }
    @GetMapping("/callouts/{id}") public Map<String,Object> detail(@PathVariable UUID id) {
        maintenance.refresh(); var c=one(db,CALLS+" WHERE c.id=?",id);
        boolean own=db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND volunteer_id=?",Integer.class,id,actor().id())>0;
        boolean privileged=chief() || actor().id().equals(c.get("created_by")) || actor().id().equals(c.get("responsible_id"));
        require(privileged || (!time(c,"end_at").isAfter(now()) ? participated(id) : own || Set.of("ABIERTA","CERRADA").contains(c.get("status")) && workflow.compatible(c,actor().id())),"No tienes acceso a esta convocatoria.");
        return presentation(c);
    }
    private void validateCall(Callout r) {
        valid(r.start().isAfter(now()) && r.end().isAfter(r.start()),"El servicio debe ser futuro y terminar después del inicio.");
        valid(Duration.between(r.start(),r.end()).compareTo(Duration.ofHours(48))<=0,"El servicio no puede durar más de 48 horas.");
        valid(r.deadline().isAfter(now()) && !r.deadline().isAfter(r.start()),"El cierre de inscripciones debe ser futuro y anterior o igual al inicio.");
        valid(Set.of("BASICO","INTERMEDIO","AVANZADO").contains(r.level()),"Nivel no válido.");
        valid(Set.of("SERVICIO","INGRESO").contains(r.kind()),"Tipo no válido.");
        valid(r.kind().equals("SERVICIO") || r.radioSlots()==0 && r.pilotSlots()==0,"El ingreso no requiere radioperador ni piloto.");
    }
    @PostMapping("/callouts") @Transactional
    public Map<String,UUID> create(@Valid @RequestBody Callout r) {
        require(manager(),"No puedes crear convocatorias."); validateCall(r); var id=UUID.randomUUID();
        int total=r.socorristaSlots()+r.radioSlots()+r.pilotSlots()+(r.kind().equals("SERVICIO")?1:0);
        db.update("""
            INSERT INTO callouts(id,title,description,start_at,end_at,deadline,location,requirements,minimum_level,
            capacity,minimum_participants,status,kind,area,contact,created_by,service_code,service_type,
            socorrista_slots,radio_slots,pilot_slots) VALUES (?,?,?,?,?,?,?,?,?,?,?,'BORRADOR',?,?,?,?,?,?,?,?,?)
            """,id,r.title(),r.description(),r.start(),r.end(),r.deadline(),r.location(),r.requirements(),r.level(),total,total,
                r.kind(),r.area(),r.contact(),actor().id(),r.code().trim().toUpperCase(Locale.ROOT),r.serviceType(),r.socorristaSlots(),r.radioSlots(),r.pilotSlots());
        return Map.of("id",id);
    }
    @PutMapping("/callouts/{id}") @Transactional
    public void edit(@PathVariable UUID id,@Valid @RequestBody Callout r) {
        var c=workflow.locked(id); workflow.editPermission(c); validateCall(r);
        valid(Set.of("BORRADOR","ABIERTA","CERRADA").contains(c.get("status")) && c.get("opened_at")==null,"No se edita un servicio iniciado, cancelado o finalizado.");
        boolean participants=db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND status IN ('CONFIRMADA','PENDIENTE_REEMPLAZO')",Integer.class,id)>0;
        if(participants) valid(r.start().equals(time(c,"start_at")) && r.end().equals(time(c,"end_at")) && r.kind().equals(c.get("kind")) && r.level().equals(c.get("minimum_level")) && r.requirements().equals(c.get("requirements")),"Con participantes no se cambia horario, tipo ni requisitos; cancela y crea otro servicio.");
        valid(r.socorristaSlots()>=workflow.occupied(id,"SOCORRISTA") && r.radioSlots()>=workflow.occupied(id,"RADIOPERADOR") && r.pilotSlots()>=workflow.occupied(id,"PILOTO"),"No puedes eliminar puestos ocupados.");
        int total=r.socorristaSlots()+r.radioSlots()+r.pilotSlots()+(r.kind().equals("SERVICIO")?1:0);
        db.update("""
            UPDATE callouts SET title=?,description=?,start_at=?,end_at=?,deadline=?,location=?,requirements=?,minimum_level=?,
            capacity=?,minimum_participants=?,kind=?,area=?,contact=?,service_code=?,service_type=?,socorrista_slots=?,radio_slots=?,pilot_slots=? WHERE id=?
            """,r.title(),r.description(),r.start(),r.end(),r.deadline(),r.location(),r.requirements(),r.level(),total,total,r.kind(),r.area(),r.contact(),r.code().trim().toUpperCase(Locale.ROOT),r.serviceType(),r.socorristaSlots(),r.radioSlots(),r.pilotSlots(),id);
        workflow.synchronize(workflow.locked(id)); workflow.announce(c,"Datos actualizados.");
    }
    @PostMapping("/callouts/{id}/status") @Transactional
    public void status(@PathVariable UUID id,@Valid @RequestBody Status r) {
        var c=workflow.locked(id); workflow.editPermission(c);
        valid(time(c,"start_at").isAfter(now()) && c.get("opened_at")==null,"La convocatoria ya comenzó.");
        if(r.status().equals("ABIERTA")) {
            valid(text(c,"status").equals("BORRADOR") && time(c,"deadline").isAfter(now()),"Solo se publica un borrador con plazo vigente.");
            db.update("UPDATE callouts SET status='ABIERTA' WHERE id=?",id);
            c.put("status","ABIERTA"); workflow.synchronize(c);
            for(var u:db.queryForList("SELECT id FROM users WHERE status='ACTIVO'"))
                if(workflow.compatible(c,id(u,"id"))) Store.notify(db,id(u,"id"),"CONVOCATORIA","Nueva convocatoria: "+c.get("title"),"/callouts/"+id);
        } else {
            valid(r.status().equals("CANCELADA") && Set.of("BORRADOR","ABIERTA","CERRADA").contains(c.get("status")),"Transición no permitida.");
            workflow.cancel(c,"Cancelación por el coordinador o jefatura.");
        }
    }
    @PostMapping("/callouts/{id}/enrollments") @Transactional
    public void enroll(@PathVariable UUID id,@Valid @RequestBody EnrollmentRequest r) {
        var c=workflow.locked(id);
        valid(r.requirementsAccepted(),"Debes aceptar los requisitos.");
        valid(Set.of("ABIERTA","CERRADA").contains(c.get("status")) && time(c,"start_at").isAfter(now()),"La convocatoria no admite inscripciones.");
        valid(r.replacementFor()!=null || time(c,"deadline").isAfter(now()),"Terminó el plazo de inscripción.");
        // Un perfil se bloquea para evitar que dos servicios simultáneos acepten al mismo voluntario.
        one(db,"SELECT user_id FROM volunteer_profiles WHERE user_id=? FOR UPDATE",actor().id());
        valid(workflow.qualified(c,actor().id(),r.position()),"No tienes el nivel o la habilitación para ese puesto.");
        valid(!workflow.overlap(c,actor().id()),"Tienes otro servicio en ese horario.");
        var previous=db.queryForList("SELECT * FROM enrollments WHERE callout_id=? AND volunteer_id=? FOR UPDATE",id,actor().id());
        valid(previous.isEmpty() || Set.of("PENDIENTE","EXPIRADA").contains(previous.get(0).get("status")),"Ya tienes una inscripción o una resolución en esta convocatoria.");
        if(r.replacementFor()!=null) {
            var target=one(db,"SELECT * FROM enrollments WHERE id=? AND callout_id=?",r.replacementFor(),id);
            valid(text(target,"status").equals("CONFIRMADA") && target.get("cancellation_requested_at")!=null && r.position().equals(target.get("position")),"El titular debe solicitar cancelación; el reemplazo ocupa exactamente su puesto.");
            valid(!actor().id().equals(target.get("volunteer_id")),"No puedes reemplazarte.");
            valid(db.queryForObject("SELECT count(*) FROM enrollments WHERE replacement_for=? AND status='PENDIENTE_REEMPLAZO'",Integer.class,r.replacementFor())==0,"Ya hay un reemplazo pendiente de revisión para esta plaza.");
        } else valid(workflow.occupied(id,r.position())<workflow.capacity(c,r.position()),"Ese puesto ya está completo. No hay lista de espera.");
        String status=r.replacementFor()==null?"CONFIRMADA":"PENDIENTE_REEMPLAZO";
        if(previous.isEmpty()) db.update("INSERT INTO enrollments(id,volunteer_id,callout_id,status,position,replacement_for,reviewed_at) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),actor().id(),id,status,r.position(),r.replacementFor(),r.replacementFor()==null?now():null);
        else db.update("UPDATE enrollments SET status=?,position=?,replacement_for=?,reviewed_at=?,created_at=?,comment=NULL WHERE id=?",status,r.position(),r.replacementFor(),r.replacementFor()==null?now():null,now(),previous.get(0).get("id"));
        if(r.replacementFor()==null) { workflow.assignResponsible(c); workflow.synchronize(c); }
        Store.notify(db,actor().id(),"INSCRIPCION",c.get("title")+": "+status,"/services");
    }
    @PostMapping("/enrollments/{id}/cancel-request") @Transactional
    public void cancellation(@PathVariable UUID id,@Valid @RequestBody Reason r) {
        var e=one(db,"SELECT * FROM enrollments WHERE id=?",id); var c=workflow.locked(id(e,"callout_id"));
        e=one(db,"SELECT * FROM enrollments WHERE id=? FOR UPDATE",id);
        require(actor().id().equals(e.get("volunteer_id")),"Solo puedes solicitar tu cancelación.");
        valid(text(e,"status").equals("CONFIRMADA") && c.get("opened_at")==null && !text(c,"status").equals("CANCELADA"),"La inscripción no se puede cancelar.");
        valid(pe.cruzroja.convocatorias.domain.ServiceRules.canRequestCancellation(time(c,"start_at"),now()),"La solicitud requiere al menos 48 horas de anticipación. Contacta a jefatura para una excepción.");
        db.update("UPDATE enrollments SET cancellation_requested_at=?,cancellation_reason=? WHERE id=?",now(),r.reason(),id);
        Store.notify(db,actor().id(),"INSCRIPCION","Cancelación solicitada. Sigues inscrito hasta que se acepte tu reemplazo.","/services");
    }
    @GetMapping("/enrollments") public List<Map<String,Object>> mine() {
        maintenance.refresh(); return db.queryForList("SELECT e.*,c.title,c.start_at,c.end_at,c.kind,c.service_code FROM enrollments e JOIN callouts c ON c.id=e.callout_id WHERE e.volunteer_id=? ORDER BY c.start_at DESC",actor().id());
    }
    @GetMapping("/callouts/{id}/enrollments") public List<Map<String,Object>> applicants(@PathVariable UUID id) {
        maintenance.refresh(); var c=one(db,"SELECT * FROM callouts WHERE id=?",id); workflow.teamPermission(c);
        String filter=chief() || workflow.canEdit(c)?"":" AND e.status IN ('CONFIRMADA','PENDIENTE_REEMPLAZO')";
        return db.queryForList("SELECT e.*,u.name,u.surnames,p.training_level FROM enrollments e JOIN users u ON u.id=e.volunteer_id LEFT JOIN volunteer_profiles p ON p.user_id=u.id WHERE e.callout_id=?"+filter+" ORDER BY e.created_at,e.id",id);
    }
    @PostMapping("/enrollments/{id}/review") @Transactional
    public void review(@PathVariable UUID id,@Valid @RequestBody Review r) {
        var initial=one(db,"SELECT callout_id FROM enrollments WHERE id=?",id); var c=workflow.locked(id(initial,"callout_id"));
        var e=one(db,"SELECT * FROM enrollments WHERE id=? FOR UPDATE",id);
        valid(time(c,"start_at").isAfter(now()) && Set.of("ABIERTA","CERRADA").contains(c.get("status")),"El servicio no admite revisiones de inscripción.");
        if(text(e,"status").equals("PENDIENTE_REEMPLAZO")) {
            workflow.operationPermission(c);
            if(r.approved()) {
                var target=one(db,"SELECT * FROM enrollments WHERE id=? FOR UPDATE",e.get("replacement_for"));
                valid(text(target,"status").equals("CONFIRMADA") && target.get("cancellation_requested_at")!=null,"La plaza original ya no está disponible para reemplazo.");
                one(db,"SELECT user_id FROM volunteer_profiles WHERE user_id=? FOR UPDATE",e.get("volunteer_id"));
                valid(workflow.qualified(c,id(e,"volunteer_id"),text(e,"position")) && !workflow.overlap(c,id(e,"volunteer_id")),"El reemplazo ya no cumple los requisitos o tiene un cruce.");
                db.update("UPDATE enrollments SET status='CANCELADA',comment='Cancelada con reemplazo confirmado' WHERE id=?",target.get("id"));
                Store.notify(db,id(target,"volunteer_id"),"INSCRIPCION","Cancelación aceptada con reemplazo. No se generará inasistencia.","/services");
            }
        } else {
            require(chief(),"Solo jefatura puede anular una inscripción aceptada automáticamente.");
            valid(!r.approved() && text(e,"status").equals("CONFIRMADA"),"La inscripción ya está aceptada o resuelta.");
        }
        db.update("UPDATE enrollments SET status=?,comment=?,reviewed_at=? WHERE id=?",r.approved()?"CONFIRMADA":"RECHAZADA",r.comment(),now(),id);
        workflow.assignResponsible(c); workflow.synchronize(c);
        Store.notify(db,id(e,"volunteer_id"),"INSCRIPCION",c.get("title")+": "+(r.approved()?"CONFIRMADA":"RECHAZADA")+". "+r.comment(),"/services");
    }
    @PostMapping("/callouts/{id}/operation") @Transactional
    public void operation(@PathVariable UUID id,@Valid @RequestBody Operation r) {
        var c=workflow.locked(id); workflow.operationPermission(c);
        valid(text(c,"kind").equals("SERVICIO"),"El ingreso no registra asistencia de servicio.");
        if(r.action().equals("ABRIR")) {
            valid(c.get("team_photo")!=null,"Adjunta primero la foto del equipo.");
            valid(c.get("opened_at")==null && text(c,"status").equals("CERRADA") && workflow.complete(c),"Faltan puestos obligatorios o el servicio ya se abrió/canceló.");
            valid(!now().isBefore(time(c,"start_at").minusHours(1)) && now().isBefore(time(c,"end_at")),"Abre dentro de la hora previa al inicio o durante el servicio.");
            db.update("UPDATE callouts SET opened_at=?,observations=? WHERE id=?",now(),r.observations(),id);
        } else {
            valid(r.action().equals("CERRAR") && c.get("opened_at")!=null && c.get("closed_at")==null && !text(c,"status").equals("CANCELADA"),"Acción no permitida.");
            valid(!now().isBefore(time(c,"end_at")),"No se cierra antes de terminar el horario.");
            db.update("UPDATE callouts SET closed_at=?,status='FINALIZADA',observations=? WHERE id=?",now(),r.observations(),id);
        }
    }
    @PostMapping("/callouts/{id}/photo") @Transactional
    public void photo(@PathVariable UUID id,@RequestParam org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        var c=workflow.locked(id); workflow.operationPermission(c);
        valid(c.get("closed_at")==null && !text(c,"status").equals("CANCELADA"),"El servicio ya está cerrado o cancelado.");
        byte[] bytes=file.getBytes(); valid(bytes.length>8 && bytes.length<=2*1024*1024,"La foto debe pesar como máximo 2 MB.");
        boolean png=bytes[0]==(byte)137 && bytes[1]==80 && bytes[2]==78 && bytes[3]==71;
        boolean jpg=bytes[0]==(byte)255 && bytes[1]==(byte)216 && bytes[2]==(byte)255;
        valid((png || jpg) && javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes))!=null,"Solo se admiten fotos JPG o PNG válidas.");
        db.update("UPDATE callouts SET team_photo=?,photo_type=? WHERE id=?",bytes,png?"image/png":"image/jpeg",id);
    }
    @GetMapping("/callouts/{id}/photo") public org.springframework.http.ResponseEntity<byte[]> photo(@PathVariable UUID id) {
        var c=one(db,"SELECT * FROM callouts WHERE id=?",id); workflow.operationPermission(c);
        if(c.get("team_photo")==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"No hay foto del equipo.");
        return org.springframework.http.ResponseEntity.ok().header("Cache-Control","no-store").contentType(org.springframework.http.MediaType.parseMediaType(text(c,"photo_type"))).body((byte[])c.get("team_photo"));
    }
}
