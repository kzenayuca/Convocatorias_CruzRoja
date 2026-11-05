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
    private final MonthlyLedger ledger;
    public ServicesController(JdbcTemplate db, ServiceMaintenance maintenance, CalloutWorkflow workflow,MonthlyLedger ledger) {
        this.db=db; this.maintenance=maintenance; this.workflow=workflow; this.ledger=ledger;
    }
    public record Callout(@NotBlank @Size(max=150) String title, @NotBlank @Size(max=5000) String description,
                          @NotNull LocalDateTime start, @NotNull LocalDateTime end, @NotNull LocalDateTime deadline,
                          @NotBlank @Size(max=200) String location, @NotBlank @Size(max=5000) String requirements,
                          @NotBlank String level, @NotNull @Min(1) @Max(10000) Integer totalVolunteers,
                          @Min(0) @Max(1) int radioSlots, @Min(0) @Max(1) int pilotSlots,
                          @NotBlank String kind, @NotBlank @Size(max=120) String area,
                          @NotBlank @Size(max=200) String contact, @Size(max=40) String code,
                          @NotBlank @Size(max=100) String serviceType,
                          @Min(0) @Max(10000) int graduateSlots) { }
    public record Responsible(UUID userId) { }
    public record Status(@NotBlank String status) { }
    public record EnrollmentRequest(boolean requirementsAccepted, @NotBlank String position, UUID replacementFor) { }
    public record Assignment(@NotNull UUID userId,@NotBlank String position,boolean requirementsVerified) { }
    public record Review(boolean approved, @NotBlank @Size(max=2000) String comment) { }
    public record Reason(@NotBlank @Size(max=2000) String reason) { }
    public record Operation(@NotBlank String action, @NotBlank @Size(max=3000) String observations) { }

    private static final String CALLS="""
        SELECT c.id,c.title,c.description,c.start_at,c.end_at,c.deadline,c.location,c.requirements,c.minimum_level,
        c.status,c.kind,c.area,c.contact,c.responsible_id,c.created_by,c.opened_at,c.closed_at,c.observations,
        c.service_code,c.service_type,c.total_volunteers,c.socorrista_slots,c.graduate_slots,c.trainee_slots,c.radio_slots,c.pilot_slots,c.planned_hours,
        (c.team_photo IS NOT NULL) AS has_photo,c.photo_uploaded_at,
        u.name AS responsible_name FROM callouts c LEFT JOIN users u ON u.id=c.responsible_id
        """;
    private Map<String,Object> presentation(Map<String,Object> c) {
        c.put("total_volunteers",workflow.total(c));
        c.put("positions",workflow.positions(c));
        c.put("can_edit",workflow.canEdit(c)); c.put("can_operate",workflow.canOperate(c));
        c.put("can_view_team",workflow.canEdit(c)||workflow.canOperate(c));
        c.put("can_upload_photo",canUploadPhoto(c));
        var replacementPositions=db.queryForList("SELECT DISTINCT position FROM enrollments WHERE callout_id=? AND status='CONFIRMADA' AND cancellation_requested_at IS NOT NULL",String.class,c.get("id"));
        c.put("eligible_positions",CalloutWorkflow.POSITIONS.stream().filter(p -> !p.equals("RESPONSABLE")
            && (!p.equals("FORMACION") || replacementPositions.contains(p))
            && (!p.equals("GRADUADO") || workflow.capacity(c,"GRADUADO")>0 || replacementPositions.contains(p))
            && workflow.qualified(c,actor().id(),p)).toList());
        var own=db.queryForList("SELECT id,status,position FROM enrollments WHERE callout_id=? AND volunteer_id=?",c.get("id"),actor().id());
        c.put("my_enrollment",own.isEmpty()?null:own.get(0));
        c.put("coverage",workflow.complete(c)?"COMPLETA":"PENDIENTE");
        c.put("execution",text(c,"status").equals("CANCELADA")?"CANCELADO":c.get("closed_at")!=null || text(c,"status").equals("FINALIZADA")?"FINALIZADO":c.get("opened_at")!=null?"EN CURSO":"PROGRAMADO");
        return c;
    }
    private boolean canUploadPhoto(Map<String,Object> c) {
        return workflow.canOperate(c);
    }
    private boolean participated(UUID id) {
        return db.queryForObject("SELECT count(*) FROM attendance_records WHERE callout_id=? AND volunteer_id=? AND check_in IS NOT NULL",Integer.class,id,actor().id())>0;
    }
    @GetMapping("/callouts")
    public List<Map<String,Object>> callouts(@RequestParam(defaultValue="false") boolean history) {
        maintenance.refresh();
        return db.queryForList(CALLS+" ORDER BY c.start_at,c.id").stream().filter(c -> {
            if(history != !time(c,"end_at").isAfter(now())) return false;
            if(workflow.canEdit(c)) return true;
            if(history) return participated(id(c,"id"));
            return Set.of("ABIERTA","CERRADA").contains(c.get("status")) && workflow.compatible(c,actor().id());
        }).map(this::presentation).toList();
    }
    @GetMapping("/callouts/{id}") public Map<String,Object> detail(@PathVariable UUID id) {
        maintenance.refresh(); var c=one(db,CALLS+" WHERE c.id=?",id);
        boolean own=db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND volunteer_id=?",Integer.class,id,actor().id())>0;
        boolean privileged=workflow.canEdit(c);
        require(privileged || own || (!time(c,"end_at").isAfter(now()) ? participated(id) : Set.of("ABIERTA","CERRADA").contains(c.get("status")) && workflow.compatible(c,actor().id())),"No tienes acceso a esta convocatoria.");
        return presentation(c);
    }
    private void validateCall(Callout r) {
        valid(r.start().isAfter(now()) && r.end().isAfter(r.start()),"El servicio debe ser futuro y terminar después del inicio.");
        valid(Duration.between(r.start(),r.end()).compareTo(Duration.ofHours(48))<=0,"El servicio no puede durar más de 48 horas.");
        valid(r.deadline().isAfter(now()) && !r.deadline().isAfter(r.start()),"El cierre de inscripciones debe ser futuro y anterior o igual al inicio.");
        valid(Set.of("BASICO","INTERMEDIO","AVANZADO").contains(r.level()),"Nivel no válido.");
        valid(Set.of("SERVICIO","INGRESO").contains(r.kind()),"Tipo no válido.");
        valid(r.kind().equals("SERVICIO") || r.radioSlots()==0 && r.pilotSlots()==0,"El ingreso no requiere radioperador ni piloto.");
        valid(r.graduateSlots()<=r.totalVolunteers(),"Los graduados requeridos deben estar dentro del total, no sumarse a él.");
        valid(r.radioSlots()+r.pilotSlots()<=r.totalVolunteers(),"Piloto y radioperador deben caber dentro del total de voluntarios.");
    }
    private String code(String value) { return value==null || value.isBlank()?null:value.trim().toUpperCase(Locale.ROOT); }
    private void recordCode(UUID id,String code,String type) {
        if(code!=null) db.update("INSERT INTO service_code_history(id,callout_id,service_code,service_type,assigned_by) VALUES (?,?,?,?,?)",UUID.randomUUID(),id,code,type,actor().id());
    }
    @PostMapping("/callouts") @Transactional
    public Map<String,UUID> create(@Valid @RequestBody Callout r) {
        require(manager(),"No puedes crear convocatorias."); validateCall(r); var id=UUID.randomUUID();
        String code=code(r.code()); require(chief() || code==null,"Solo jefatura asigna el código del servicio.");
        int total=r.totalVolunteers();
        db.update("""
            INSERT INTO callouts(id,title,description,start_at,end_at,deadline,location,requirements,minimum_level,
            capacity,minimum_participants,status,kind,area,contact,created_by,service_code,service_type,
            socorrista_slots,radio_slots,pilot_slots,graduate_slots,total_volunteers) VALUES (?,?,?,?,?,?,?,?,?,?,?,'BORRADOR',?,?,?,?,?,?,?,?,?,?,?)
            """,id,r.title(),r.description(),r.start(),r.end(),r.deadline(),r.location(),r.requirements(),r.level(),total,total,
                r.kind(),r.area(),r.contact(),actor().id(),code,r.serviceType(),total-r.radioSlots()-r.pilotSlots(),r.radioSlots(),r.pilotSlots(),r.graduateSlots(),total);
        recordCode(id,code,r.serviceType());
        return Map.of("id",id);
    }
    @PutMapping("/callouts/{id}") @Transactional
    public void edit(@PathVariable UUID id,@Valid @RequestBody Callout r) {
        var c=workflow.locked(id); workflow.editPermission(c); validateCall(r);
        valid(Set.of("BORRADOR","ABIERTA","CERRADA").contains(c.get("status")) && c.get("opened_at")==null,"No se edita un servicio iniciado, cancelado o finalizado.");
        boolean participants=db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND status IN ('CONFIRMADA','PENDIENTE_REEMPLAZO')",Integer.class,id)>0;
        if(participants) valid(r.kind().equals(c.get("kind")) && r.level().equals(c.get("minimum_level")),"Con participantes no se cambia el tipo ni el nivel mínimo.");
        if(!r.start().equals(time(c,"start_at")) || !r.end().equals(time(c,"end_at"))) {
            var proposed=new HashMap<>(c); proposed.put("start_at",java.sql.Timestamp.valueOf(r.start())); proposed.put("end_at",java.sql.Timestamp.valueOf(r.end()));
            for(var e:db.queryForList("SELECT volunteer_id FROM enrollments WHERE callout_id=? AND status='CONFIRMADA' ORDER BY volunteer_id",id)) {
                one(db,"SELECT user_id FROM volunteer_profiles WHERE user_id=? FOR UPDATE",e.get("volunteer_id"));
                valid(!workflow.overlap(proposed,id(e,"volunteer_id")),"El nuevo horario se cruza con otro servicio de un participante.");
            }
        }
        var proposed=new HashMap<>(c); proposed.put("total_volunteers",r.totalVolunteers()); proposed.put("graduate_slots",r.graduateSlots());
        proposed.put("radio_slots",r.radioSlots()); proposed.put("pilot_slots",r.pilotSlots());
        valid(workflow.fits(proposed),"Los nuevos cupos no admiten al equipo inscrito o dejan sin espacio las funciones y los graduados requeridos.");
        String code=code(r.code()); require(chief() || Objects.equals(code,c.get("service_code")),"Solo jefatura modifica el código.");
        valid(c.get("service_code")==null || code!=null,"No retires un código ya asignado.");
        int total=r.totalVolunteers();
        db.update("""
            UPDATE callouts SET title=?,description=?,start_at=?,end_at=?,deadline=?,location=?,requirements=?,minimum_level=?,
            capacity=?,minimum_participants=?,kind=?,area=?,contact=?,service_code=?,service_type=?,socorrista_slots=?,radio_slots=?,pilot_slots=?,graduate_slots=?,trainee_slots=0,total_volunteers=? WHERE id=?
            """,r.title(),r.description(),r.start(),r.end(),r.deadline(),r.location(),r.requirements(),r.level(),total,total,r.kind(),r.area(),r.contact(),code,r.serviceType(),total-r.radioSlots()-r.pilotSlots(),r.radioSlots(),r.pilotSlots(),r.graduateSlots(),total,id);
        if(!Objects.equals(code,c.get("service_code"))) recordCode(id,code,r.serviceType());
        workflow.synchronize(workflow.locked(id)); workflow.announce(c,"Datos actualizados: revisa horario, lugar y requisitos. "+r.location()+" · "+r.start());
    }
    @PostMapping("/callouts/{id}/status") @Transactional
    public void status(@PathVariable UUID id,@Valid @RequestBody Status r) {
        var c=workflow.locked(id); workflow.editPermission(c);
        valid(time(c,"start_at").isAfter(now()) && c.get("opened_at")==null,"La convocatoria ya comenzó.");
        if(r.status().equals("ABIERTA")) {
            valid(c.get("service_code")!=null,"Jefatura debe asignar un código antes de publicar.");
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
        enrollFor(id,actor().id(),r,false);
    }
    @PostMapping("/callouts/{id}/assignments") @Transactional
    public void assign(@PathVariable UUID id,@Valid @RequestBody Assignment r) {
        require(chief(),"Solo jefatura puede inscribir directamente a otra persona.");
        valid(r.requirementsVerified(),"Jefatura debe verificar todos los requisitos declarados antes de asignar.");
        enrollFor(id,r.userId(),new EnrollmentRequest(true,r.position(),null),true);
    }
    @GetMapping("/callouts/{id}/assignment-candidates")
    public List<Map<String,Object>> assignmentCandidates(@PathVariable UUID id,@RequestParam String position) {
        require(chief(),"Solo jefatura consulta candidatos para la inscripción directa.");
        valid(Set.of("SOCORRISTA","GRADUADO","RADIOPERADOR","PILOTO").contains(position),"Puesto no válido para inscripción directa.");
        var c=one(db,"SELECT "+CalloutWorkflow.FIELDS+" FROM callouts WHERE id=?",id);
        if(!Set.of("ABIERTA","CERRADA").contains(c.get("status")) || c.get("opened_at")!=null || !time(c,"start_at").isAfter(now()) || !time(c,"deadline").isAfter(now())) return List.of();
        return db.queryForList("""
            SELECT u.id,u.name,u.surnames,u.graduated,p.training_level FROM users u
            JOIN volunteer_profiles p ON p.user_id=u.id WHERE u.status='ACTIVO'
            AND NOT EXISTS(SELECT 1 FROM enrollments e WHERE e.callout_id=? AND e.volunteer_id=u.id AND e.status NOT IN ('PENDIENTE','EXPIRADA'))
            ORDER BY u.name,u.surnames,u.id
            """,id).stream().filter(u -> workflow.qualified(c,id(u,"id"),position) && !workflow.overlap(c,id(u,"id"))
                && workflow.canAdmit(c,id(u,"id"),position,null)).toList();
    }
    private void enrollFor(UUID id,UUID user,EnrollmentRequest r,boolean assigned) {
        var c=workflow.locked(id);
        valid(!r.position().equals("RESPONSABLE"),"El responsable lo asigna jefatura y cuenta dentro del total.");
        valid(!r.position().equals("FORMACION") || r.replacementFor()!=null,"Voluntario incluye graduados y formación; no hay nuevas plazas exclusivas para formación.");
        valid(r.requirementsAccepted(),"Debes aceptar los requisitos.");
        valid(Set.of("ABIERTA","CERRADA").contains(c.get("status")) && (!assigned || c.get("opened_at")==null) && time(c,"start_at").isAfter(now()),"La convocatoria no admite inscripciones.");
        valid(r.replacementFor()!=null || time(c,"deadline").isAfter(now()),"Terminó el plazo de inscripción.");
        // Un perfil se bloquea para evitar que dos servicios simultáneos acepten al mismo voluntario.
        one(db,"SELECT user_id FROM volunteer_profiles WHERE user_id=? FOR UPDATE",user);
        valid(workflow.qualified(c,user,r.position()),"La persona no está activa o no cumple el nivel, la graduación o la habilitación del puesto.");
        valid(!workflow.overlap(c,user),"La persona tiene otro servicio en ese horario.");
        var previous=db.queryForList("SELECT * FROM enrollments WHERE callout_id=? AND volunteer_id=? FOR UPDATE",id,user);
        valid(previous.isEmpty() || Set.of("PENDIENTE","EXPIRADA").contains(previous.get(0).get("status")),"Ya tienes una inscripción o una resolución en esta convocatoria.");
        if(r.replacementFor()!=null) {
            var target=one(db,"SELECT * FROM enrollments WHERE id=? AND callout_id=?",r.replacementFor(),id);
            valid(text(target,"status").equals("CONFIRMADA") && target.get("cancellation_requested_at")!=null && r.position().equals(target.get("position")),"El titular debe solicitar cancelación; el reemplazo ocupa exactamente su puesto.");
            valid(!user.equals(target.get("volunteer_id")),"No puedes reemplazarte.");
            valid(db.queryForObject("SELECT count(*) FROM enrollments WHERE replacement_for=? AND status='PENDIENTE_REEMPLAZO'",Integer.class,r.replacementFor())==0,"Ya hay un reemplazo pendiente de revisión para esta plaza.");
            valid(workflow.canAdmit(c,user,r.position(),id(target,"volunteer_id")),"El reemplazo dejaría sin cubrir el mínimo de graduados o una función reservada.");
        } else valid(workflow.canAdmit(c,user,r.position(),null),"No hay cupo compatible: se reserva espacio para los graduados, piloto o radioperador requeridos. No hay lista de espera.");
        String status=r.replacementFor()==null?"CONFIRMADA":"PENDIENTE_REEMPLAZO";
        if(previous.isEmpty()) db.update("INSERT INTO enrollments(id,volunteer_id,callout_id,status,position,replacement_for,reviewed_at) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),user,id,status,r.position(),r.replacementFor(),r.replacementFor()==null?now():null);
        else db.update("UPDATE enrollments SET status=?,position=?,replacement_for=?,reviewed_at=?,created_at=?,comment=NULL WHERE id=?",status,r.position(),r.replacementFor(),r.replacementFor()==null?now():null,now(),previous.get(0).get("id"));
        db.update("UPDATE enrollments SET requirements_accepted=true,assigned_by=?,assigned_at=? WHERE callout_id=? AND volunteer_id=?",assigned?actor().id():null,assigned?now():null,id,user);
        if(r.replacementFor()==null) { workflow.assignResponsible(c); workflow.synchronize(c); }
        Store.notify(db,user,"INSCRIPCION",c.get("title")+": "+(assigned?"Jefatura te inscribió directamente en "+r.position()+". ":"")+status,"/services");
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
        maintenance.refresh(); return db.queryForList("""
            SELECT e.*,c.title,c.start_at,c.end_at,c.kind,c.service_code,c.location,c.service_type,c.opened_at,c.closed_at,
            EXISTS(SELECT 1 FROM monthly_reports m WHERE m.month=date_trunc('month',c.start_at)::date AND m.status='APROBADO') AS month_closed
            FROM enrollments e JOIN callouts c ON c.id=e.callout_id WHERE e.volunteer_id=? ORDER BY c.start_at DESC
            """,actor().id());
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
                valid(workflow.canAdmit(c,id(e,"volunteer_id"),text(e,"position"),id(target,"volunteer_id")),"El reemplazo no conserva el mínimo de graduados o los cupos reservados.");
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
        var c=workflow.locked(id); require(canUploadPhoto(c),"Solo jefatura o el responsable graduado asignado pueden subir fotos.");
        byte[] bytes=file.getBytes(); valid(bytes.length>8 && bytes.length<=50*1024*1024,"La foto debe pesar como máximo 50 MB.");
        boolean png=bytes[0]==(byte)137 && bytes[1]==80 && bytes[2]==78 && bytes[3]==71;
        boolean jpg=bytes[0]==(byte)255 && bytes[1]==(byte)216 && bytes[2]==(byte)255;
        valid(png || jpg,"Solo se admiten fotos JPG o PNG válidas.");
        try(var input=javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            var readers=javax.imageio.ImageIO.getImageReaders(input); valid(readers.hasNext(),"La imagen no es válida.");
            var reader=readers.next();
            try { reader.setInput(input); valid((long)reader.getWidth(0)*reader.getHeight(0)<=40000000,"La foto admite hasta 40 megapíxeles."); valid(reader.read(0)!=null,"Imagen no válida."); }
            finally { reader.dispose(); }
        }
        db.update("INSERT INTO callout_photo_archive(id,callout_id,photo,photo_type,uploaded_by,uploaded_at) SELECT ?,id,team_photo,photo_type,photo_uploaded_by,photo_uploaded_at FROM callouts WHERE id=? AND team_photo IS NOT NULL",UUID.randomUUID(),id);
        db.update("UPDATE callouts SET team_photo=?,photo_type=?,photo_uploaded_by=?,photo_uploaded_at=? WHERE id=?",bytes,png?"image/png":"image/jpeg",actor().id(),now(),id);
    }
    @GetMapping("/callouts/{id}/photo") public org.springframework.http.ResponseEntity<byte[]> photo(@PathVariable UUID id) {
        var c=one(db,"SELECT * FROM callouts WHERE id=?",id);
        if(c.get("team_photo")==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"No hay foto del equipo.");
        return org.springframework.http.ResponseEntity.ok().header("Cache-Control","no-store").contentType(org.springframework.http.MediaType.parseMediaType(text(c,"photo_type"))).body((byte[])c.get("team_photo"));
    }

    @GetMapping("/service-codes") public List<Map<String,Object>> codes() {
        require(chief(),"Solo jefatura consulta el historial de códigos.");
        return db.queryForList("SELECT h.*,u.name AS assigned_name FROM service_code_history h LEFT JOIN users u ON u.id=h.assigned_by ORDER BY h.assigned_at DESC,h.id DESC LIMIT 100");
    }
    @GetMapping("/responsible-candidates") public List<Map<String,Object>> responsibleCandidates() {
        require(chief(),"Solo jefatura asigna responsables.");
        return db.queryForList("SELECT id,name,surnames FROM users WHERE status='ACTIVO' AND role='VOLUNTARIO' AND graduated=true ORDER BY name");
    }
    @PutMapping("/callouts/{id}/responsible") @Transactional
    public void responsible(@PathVariable UUID id,@RequestBody Responsible r) {
        require(chief(),"Solo jefatura asigna responsables."); var c=workflow.locked(id); ledger.ensureOpen(c);
        valid(r.userId()==null || text(c,"kind").equals("SERVICIO"),"El ingreso no requiere un responsable de servicio.");
        valid(time(c,"start_at").isAfter(now()) && c.get("opened_at")==null && !Set.of("CANCELADA","FINALIZADA").contains(c.get("status")),"Asigna al responsable antes del servicio.");
        if(Objects.equals(c.get("responsible_id"),r.userId())) return;
        if(r.userId()!=null) {
            one(db,"SELECT user_id FROM volunteer_profiles WHERE user_id=? FOR UPDATE",r.userId());
            valid(workflow.qualified(c,r.userId(),"RESPONSABLE"),"El responsable debe ser un voluntario graduado activo y cumplir el nivel mínimo.");
            valid(!workflow.overlap(c,r.userId()),"El responsable tiene otro servicio en ese horario.");
            valid(db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND volunteer_id=? AND NOT(position='RESPONSABLE' AND status='CANCELADA') AND NOT(position IN ('SOCORRISTA','GRADUADO','FORMACION') AND status='CONFIRMADA')",Integer.class,id,r.userId())==0,"No se puede convertir un piloto, radioperador o una inscripción resuelta en responsable.");
        }
        db.update("UPDATE enrollments SET status='CANCELADA',comment='Responsabilidad reasignada por jefatura' WHERE callout_id=? AND position='RESPONSABLE' AND status='CONFIRMADA'",id);
        if(r.userId()!=null) {
            valid(workflow.canAdmit(c,r.userId(),"RESPONSABLE",r.userId()),"El responsable debe caber dentro del total sin ocupar el espacio reservado para piloto o radioperador.");
            valid(db.queryForObject("SELECT count(*) FROM enrollments e WHERE e.callout_id=? AND e.volunteer_id=? AND (e.cancellation_requested_at IS NOT NULL OR EXISTS(SELECT 1 FROM enrollments pending WHERE pending.replacement_for=e.id AND pending.status='PENDIENTE_REEMPLAZO'))",Integer.class,id,r.userId())==0,"Resuelve primero la solicitud de reemplazo de esta persona.");
            db.update("INSERT INTO enrollments(id,volunteer_id,callout_id,status,position,reviewed_at,assigned_by,assigned_at,requirements_accepted) VALUES (?,?,?,'CONFIRMADA','RESPONSABLE',?,?,?,true) ON CONFLICT(volunteer_id,callout_id) DO UPDATE SET status='CONFIRMADA',position='RESPONSABLE',reviewed_at=EXCLUDED.reviewed_at,assigned_by=EXCLUDED.assigned_by,assigned_at=EXCLUDED.assigned_at,requirements_accepted=true,comment='Responsabilidad reasignada por jefatura'",UUID.randomUUID(),r.userId(),id,now(),actor().id(),now());
        }
        db.update("UPDATE callouts SET responsible_id=? WHERE id=?",r.userId(),id);
        workflow.synchronize(workflow.locked(id));
        workflow.announce(workflow.locked(id),"Responsable actualizado por jefatura.");
    }
    @GetMapping("/schedule") public List<Map<String,Object>> schedule(@RequestParam java.time.LocalDate date) {
        maintenance.refresh();
        String personal=chief()?"":" AND EXISTS(SELECT 1 FROM enrollments e WHERE e.callout_id=c.id AND e.volunteer_id=? AND e.status='CONFIRMADA')";
        Object[] args=chief()?new Object[]{date.plusDays(1).atStartOfDay(),date.atStartOfDay()}:new Object[]{date.plusDays(1).atStartOfDay(),date.atStartOfDay(),actor().id()};
        var rows=db.queryForList(CALLS+" WHERE c.start_at<? AND c.end_at>?"+personal+" ORDER BY c.start_at,c.id",args);
        return rows.stream().map(this::presentation).toList();
    }
    @GetMapping("/schedule/pdf") public org.springframework.http.ResponseEntity<byte[]> schedulePdf(@RequestParam java.time.LocalDate date) throws java.io.IOException {
        var lines=new ArrayList<String>();
        for(var c:schedule(date)) {
            lines.add(Objects.toString(c.get("service_code"),"Sin código")+" - "+c.get("title"));
            lines.add(c.get("service_type")+" | "+time(c,"start_at")+" a "+time(c,"end_at"));
            lines.add("Lugar: "+c.get("location")+" | Responsable: "+Objects.toString(c.get("responsible_name"),"Sin asignar"));
            lines.add("Cobertura: "+c.get("coverage")+" | Ejecución: "+c.get("execution"));
            lines.add("Total de voluntarios: "+workflow.total(c)+" | Mínimo de graduados dentro del total: "+c.get("graduate_slots"));
            if(chief()) for(var e:applicants(id(c,"id"))) lines.add(e.get("position")+": "+e.get("name")+" "+e.get("surnames")+" - "+e.get("status"));
            else { var e=(Map<?,?>)c.get("my_enrollment"); if(e!=null) lines.add("Mi puesto: "+e.get("position")); }
            lines.add("");
        }
        if(lines.isEmpty()) lines.add("No hay servicios para esta fecha.");
        return PdfDocument.download("programacion-"+date+".pdf",(chief()?"Programación general - ":"Mi programación - ")+date,lines);
    }
    @GetMapping("/profile/photos") public List<Map<String,Object>> myPhotos() {
        return db.queryForList("SELECT c.id,c.title,c.start_at FROM callouts c WHERE c.team_photo IS NOT NULL AND EXISTS(SELECT 1 FROM attendance_records a WHERE a.callout_id=c.id AND a.volunteer_id=? AND a.check_in IS NOT NULL) ORDER BY c.start_at DESC",actor().id());
    }
}
