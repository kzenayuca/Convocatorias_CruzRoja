package pe.cruzroja.convocatorias.operations.application;

import java.time.*;
import java.util.*;
import pe.cruzroja.convocatorias.operations.domain.*;
import pe.cruzroja.convocatorias.shared.application.*;
import static pe.cruzroja.convocatorias.shared.application.Projection.*;
import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

public class ServicesUseCases extends UseCaseSupport {
    private final CalloutRepository repository;
    private final EnrollmentRepository enrollments;
    private final ServiceMaintenance maintenance;
    private final CalloutWorkflow workflow;
    private final MonthlyLedger ledger;
    private final ImageInspector images;
    public ServicesUseCases(CalloutRepository repository,EnrollmentRepository enrollments,ServiceMaintenance maintenance,CalloutWorkflow workflow,MonthlyLedger ledger,ImageInspector images,ExecutionContext context,NotificationPublisher notifications) {
        super(context,notifications); this.repository=repository; this.enrollments=enrollments; this.maintenance=maintenance; this.workflow=workflow; this.ledger=ledger; this.images=images;
    }
    public record Callout(String title,String description,LocalDateTime start,LocalDateTime end,LocalDateTime deadline,String location,String requirements,Integer totalVolunteers,int radioSlots,int pilotSlots,String kind,String area,String code,String serviceType,int graduateSlots) {
        public Callout {
            required(title,150,"Título"); required(description,5000,"Descripción"); required(location,200,"Lugar"); required(requirements,5000,"Requisitos"); required(kind,30,"Tipo"); required(area,120,"Área"); required(serviceType,100,"Tipo de servicio");
            valid(code==null || code.length()<=40,"Código demasiado largo."); valid(totalVolunteers!=null && totalVolunteers>=1 && totalVolunteers<=10000,"Total no válido.");
            new TeamCapacity(totalVolunteers,graduateSlots,radioSlots,pilotSlots); valid(radioSlots<=1 && pilotSlots<=1,"Solo se admite un piloto y un radioperador."); new ServiceWindow(start,end,deadline);
            valid(Set.of("SERVICIO","INGRESO").contains(kind),"Tipo no válido."); valid(kind.equals("SERVICIO") || radioSlots==0 && pilotSlots==0,"El ingreso no requiere radioperador ni piloto.");
        }
    }
    public record Responsible(UUID userId) { }
    public record ResponsibleChange(UUID expectedResponsibleId,UUID userId,String reason) { public ResponsibleChange { valid(expectedResponsibleId!=null,"Indica el responsable actual."); required(reason,2000,"Motivo"); } }
    public record Status(String status) { public Status { required(status,30,"Estado"); } }
    public record EnrollmentRequest(boolean requirementsAccepted,String position,UUID replacementFor) { public EnrollmentRequest { required(position,30,"Puesto"); } }
    public record Assignment(UUID userId,String position,boolean requirementsVerified) { public Assignment { valid(userId!=null,"Selecciona una persona."); required(position,30,"Puesto"); } }
    public record Review(boolean approved,String comment) { public Review { required(comment,2000,"Motivo"); } }
    public record Reason(String reason) { public Reason { required(reason,2000,"Motivo"); } }
    public record Operation(String action,String observations) { public Operation { required(action,30,"Acción"); required(observations,3000,"Observaciones"); } }
    private Map<String,Object> presentation(Map<String,Object> c) {
        c.put("total_volunteers",workflow.total(c)); c.put("positions",workflow.positions(c)); c.put("can_edit",workflow.canEdit(c)); c.put("can_operate",workflow.canOperate(c)); c.put("can_view_team",workflow.canEdit(c)); c.put("can_upload_photo",workflow.canOperate(c));
        var replacements=repository.replacementPositions(id(c,"id"));
        c.put("eligible_positions",CalloutWorkflow.POSITIONS.stream().filter(p -> !p.equals("RESPONSABLE") && (!p.equals("FORMACION") || replacements.contains(p)) && (!p.equals("GRADUADO") || workflow.capacity(c,"GRADUADO")>0 || replacements.contains(p)) && workflow.qualified(c,actor().id(),p)).toList());
        var own=enrollments.byUser(id(c,"id"),actor().id(),false); c.put("my_enrollment",own.isEmpty()?null:Map.of("id",own.get(0).get("id"),"status",own.get(0).get("status"),"position",own.get(0).get("position")));
        c.put("contact","Consultar a jefatura");
        if((chief() || !own.isEmpty() && "CONFIRMADA".equals(own.get(0).get("status"))) && c.get("responsible_id")!=null) { String phone=repository.responsiblePhone(id(c,"id"),id(c,"responsible_id")); if(phone!=null && !phone.isBlank()) c.put("contact",phone); }
        c.put("coverage",workflow.complete(c)?"COMPLETA":"PENDIENTE"); c.put("execution",text(c,"status").equals("CANCELADA")?"CANCELADO":c.get("closed_at")!=null || text(c,"status").equals("FINALIZADA")?"FINALIZADO":c.get("opened_at")!=null?"EN CURSO":"PROGRAMADO"); return c;
    }
    public List<Map<String,Object>> callouts(boolean history) {
        maintenance.refresh(); return repository.views().stream().filter(c -> {
            if(history != !time(c,"end_at").isAfter(now())) return false; if(workflow.canEdit(c)) return true;
            if(history) return repository.attended(id(c,"id"),actor().id());
            return Set.of("ABIERTA","CERRADA").contains(c.get("status")) && workflow.compatible(c,actor().id());
        }).map(this::presentation).toList();
    }
    public Map<String,Object> detail(UUID id) {
        maintenance.refresh(); var c=repository.view(id); boolean own=!enrollments.byUser(id,actor().id(),false).isEmpty();
        require(workflow.canEdit(c) || own || (!time(c,"end_at").isAfter(now())?repository.attended(id,actor().id()):Set.of("ABIERTA","CERRADA").contains(c.get("status")) && workflow.compatible(c,actor().id())),"No tienes acceso a esta convocatoria."); return presentation(c);
    }
    private String code(String value) { return value==null || value.isBlank()?null:value.trim().toUpperCase(Locale.ROOT); }
    public Map<String,UUID> create(Callout r) {
        require(chief(),"No puedes crear convocatorias."); new ServiceWindow(r.start(),r.end(),r.deadline()).validatePlanning(now()); UUID id=UUID.randomUUID(); String code=code(r.code());
        repository.create(id,r,actor().id(),code); repository.recordCode(id,code,r.serviceType(),actor().id()); return Map.of("id",id);
    }
    public void edit(UUID id,Callout r) {
        var c=workflow.locked(id); workflow.editPermission(c); new ServiceWindow(r.start(),r.end(),r.deadline()).validatePlanning(now());
        valid(Set.of("BORRADOR","ABIERTA","CERRADA").contains(c.get("status")) && c.get("opened_at")==null,"No se edita un servicio iniciado, cancelado o finalizado.");
        var team=enrollments.applicants(id); boolean participants=team.stream().anyMatch(e -> Set.of("CONFIRMADA","PENDIENTE_REEMPLAZO").contains(e.get("status")));
        if(participants) valid(r.kind().equals(c.get("kind")),"Con participantes no se cambia el tipo de convocatoria.");
        if(!r.start().equals(time(c,"start_at")) || !r.end().equals(time(c,"end_at"))) {
            var proposed=new HashMap<>(c); proposed.put("start_at",r.start()); proposed.put("end_at",r.end());
            for(var e:team.stream().filter(e -> "CONFIRMADA".equals(e.get("status"))).sorted(Comparator.comparing(e -> id(e,"volunteer_id"))).toList()) { enrollments.lockVolunteer(id(e,"volunteer_id")); valid(!workflow.overlap(proposed,id(e,"volunteer_id")),"El nuevo horario se cruza con otro servicio de un participante."); }
        }
        var proposed=new HashMap<>(c); proposed.put("total_volunteers",r.totalVolunteers()); proposed.put("graduate_slots",r.graduateSlots()); proposed.put("radio_slots",r.radioSlots()); proposed.put("pilot_slots",r.pilotSlots());
        valid(workflow.fits(proposed),"Los nuevos cupos no admiten al equipo inscrito o dejan sin espacio las funciones y los graduados requeridos."); String code=code(r.code());
        require(chief() || Objects.equals(code,c.get("service_code")),"Solo jefatura modifica el código."); valid(c.get("service_code")==null || code!=null,"No retires un código ya asignado.");
        repository.edit(id,r,code); if(!Objects.equals(code,c.get("service_code"))) repository.recordCode(id,code,r.serviceType(),actor().id());
        workflow.synchronize(workflow.locked(id)); workflow.announce(c,"Datos actualizados: revisa horario, lugar y requisitos. "+r.location()+" · "+r.start());
    }
    public void status(UUID id,Status r) {
        var c=workflow.locked(id); workflow.editPermission(c); valid(time(c,"start_at").isAfter(now()) && c.get("opened_at")==null,"La convocatoria ya comenzó.");
        if(r.status().equals("ABIERTA")) {
            valid(c.get("service_code")!=null,"Jefatura debe asignar un código antes de publicar."); valid(text(c,"status").equals("BORRADOR") && time(c,"deadline").isAfter(now()),"Solo se publica un borrador con plazo vigente.");
            repository.status(id,"ABIERTA"); c.put("status","ABIERTA"); workflow.synchronize(c);
            for(var user:repository.activeUsers()) if(workflow.compatible(c,user)) notifyUser(user,"CONVOCATORIA","Nueva convocatoria: "+c.get("title"),"/callouts/"+id);
        } else { valid(r.status().equals("CANCELADA") && Set.of("BORRADOR","ABIERTA","CERRADA").contains(c.get("status")),"Transición no permitida."); workflow.cancel(c,"Cancelación por el coordinador o jefatura."); }
    }
    public void enroll(UUID id,EnrollmentRequest r) { enrollFor(id,actor().id(),r,false); }
    public void assign(UUID id,Assignment r) { require(chief(),"Solo jefatura puede inscribir directamente a otra persona."); valid(r.requirementsVerified(),"Jefatura debe verificar todos los requisitos declarados antes de asignar."); enrollFor(id,r.userId(),new EnrollmentRequest(true,r.position(),null),true); }
    public List<Map<String,Object>> assignmentCandidates(UUID id,String position) {
        require(chief(),"Solo jefatura consulta candidatos para la inscripción directa."); valid(Set.of("SOCORRISTA","GRADUADO","RADIOPERADOR","PILOTO").contains(position),"Puesto no válido para inscripción directa."); var c=repository.snapshot(id,false);
        if(!Set.of("ABIERTA","CERRADA").contains(c.get("status")) || c.get("opened_at")!=null || !time(c,"start_at").isAfter(now()) || !time(c,"deadline").isAfter(now())) return List.of();
        return enrollments.candidates(id).stream().filter(u -> workflow.qualified(c,id(u,"id"),position) && !workflow.overlap(c,id(u,"id")) && workflow.canAdmit(c,id(u,"id"),position,null)).toList();
    }
    private void enrollFor(UUID id,UUID user,EnrollmentRequest r,boolean assigned) {
        var c=workflow.locked(id); valid(!r.position().equals("RESPONSABLE"),"El responsable lo asigna jefatura y cuenta dentro del total."); valid(!r.position().equals("FORMACION") || r.replacementFor()!=null,"Voluntario incluye graduados y formación; no hay nuevas plazas exclusivas para formación.");
        workflow.model(c).requireEnrollment(r.requirementsAccepted(),assigned,r.replacementFor()!=null,now()); enrollments.lockVolunteer(user);
        valid(workflow.qualified(c,user,r.position()),"La persona no es voluntaria activa o no cumple la graduación o la habilitación del puesto."); valid(!workflow.overlap(c,user),"La persona tiene otro servicio en ese horario.");
        var previous=enrollments.byUser(id,user,true); valid(previous.isEmpty() || Set.of("PENDIENTE","EXPIRADA").contains(previous.get(0).get("status")),"Ya tienes una inscripción o una resolución en esta convocatoria.");
        UUID replacing=null;
        if(r.replacementFor()!=null) { var target=enrollments.find(r.replacementFor(),false); valid(id.equals(target.get("callout_id")) && text(target,"status").equals("CONFIRMADA") && target.get("cancellation_requested_at")!=null && r.position().equals(target.get("position")),"El titular debe solicitar cancelación; el reemplazo ocupa exactamente su puesto."); valid(!user.equals(target.get("volunteer_id")),"No puedes reemplazarte."); valid(enrollments.pendingReplacements(r.replacementFor())==0,"Ya hay un reemplazo pendiente de revisión para esta plaza."); replacing=id(target,"volunteer_id"); }
        valid(workflow.canAdmit(c,user,r.position(),replacing),"No hay cupo compatible: se reserva espacio para los graduados, piloto o radioperador requeridos. No hay lista de espera.");
        enrollments.save(previous.isEmpty()?null:id(previous.get(0),"id"),user,id,r.position(),r.replacementFor(),assigned?actor().id():null,now());
        if(r.replacementFor()==null) { workflow.assignResponsible(c); workflow.synchronize(c); }
        notifyUser(user,"INSCRIPCION",c.get("title")+": "+(assigned?"Jefatura te inscribió directamente en "+r.position()+". ":"")+(r.replacementFor()==null?"CONFIRMADA":"PENDIENTE_REEMPLAZO"),"/services");
    }
    public void cancellation(UUID id,Reason r) {
        var e=enrollments.find(id,false); var c=workflow.locked(id(e,"callout_id")); e=enrollments.find(id,true);
        require(actor().id().equals(e.get("volunteer_id")),"Solo puedes solicitar tu cancelación."); valid(text(e,"status").equals("CONFIRMADA") && c.get("opened_at")==null && !text(c,"status").equals("CANCELADA"),"La inscripción no se puede cancelar."); valid(ServiceRules.canRequestCancellation(time(c,"start_at"),now()),"La solicitud requiere al menos 48 horas de anticipación. Contacta a jefatura para una excepción.");
        enrollments.cancellation(id,r.reason(),now()); notifyUser(actor().id(),"INSCRIPCION","Cancelación solicitada. Sigues inscrito hasta que se acepte tu reemplazo.","/services");
    }
    public List<Map<String,Object>> mine() { maintenance.refresh(); return enrollments.mine(actor().id()); }
    public List<Map<String,Object>> applicants(UUID id) { maintenance.refresh(); workflow.teamPermission(repository.snapshot(id,false)); return enrollments.applicants(id); }
    public void review(UUID id,Review r) {
        var initial=enrollments.find(id,false); var c=workflow.locked(id(initial,"callout_id")); var e=enrollments.find(id,true);
        valid(time(c,"start_at").isAfter(now()) && Set.of("ABIERTA","CERRADA").contains(c.get("status")),"El servicio no admite revisiones de inscripción.");
        if(text(e,"status").equals("PENDIENTE_REEMPLAZO")) { workflow.operationPermission(c); if(r.approved()) {
            var target=enrollments.find(id(e,"replacement_for"),true); valid(text(target,"status").equals("CONFIRMADA") && target.get("cancellation_requested_at")!=null,"La plaza original ya no está disponible para reemplazo.");
            enrollments.lockVolunteer(id(e,"volunteer_id")); valid(workflow.qualified(c,id(e,"volunteer_id"),text(e,"position")) && !workflow.overlap(c,id(e,"volunteer_id")),"El reemplazo ya no cumple los requisitos o tiene un cruce."); valid(workflow.canAdmit(c,id(e,"volunteer_id"),text(e,"position"),id(target,"volunteer_id")),"El reemplazo no conserva el mínimo de graduados o los cupos reservados.");
            enrollments.cancelReplaced(id(target,"id")); notifyUser(id(target,"volunteer_id"),"INSCRIPCION","Cancelación aceptada con reemplazo. No se generará inasistencia.","/services");
        }} else { require(chief(),"Solo jefatura puede anular una inscripción aceptada automáticamente."); valid(!r.approved() && text(e,"status").equals("CONFIRMADA"),"La inscripción ya está aceptada o resuelta."); }
        enrollments.review(id,r.approved(),r.comment(),now()); workflow.assignResponsible(c); workflow.synchronize(c); notifyUser(id(e,"volunteer_id"),"INSCRIPCION",c.get("title")+": "+(r.approved()?"CONFIRMADA":"RECHAZADA")+". "+r.comment(),"/services");
    }
    public void operation(UUID id,Operation r) { var c=workflow.locked(id); workflow.operationPermission(c); workflow.model(c).requireOperation(r.action(),now()); repository.operation(id,r.action(),r.observations(),now()); }
    public void photo(UUID id,byte[] bytes) { var c=workflow.locked(id); workflow.operationPermission(c); valid(bytes!=null && bytes.length<=10*1024*1024,"La foto debe pesar como máximo 10 MB."); String type=images.validate(bytes); repository.photo(id,bytes,type,actor().id(),now()); }
    public CalloutRepository.TeamPhoto photo(UUID id) { detail(id); return repository.photo(id); }
    public List<Map<String,Object>> codes() { require(chief(),"Solo jefatura consulta el historial de códigos."); return repository.codes(); }
    public List<Map<String,Object>> responsibleCandidates(UUID id) { require(chief(),"Solo jefatura asigna responsables."); var c=repository.snapshot(id,false); return repository.responsibleCandidates(id).stream().filter(u -> !workflow.overlap(c,id(u,"id"))).toList(); }
    public void responsible(UUID id,Responsible r) { require(chief(),"Solo jefatura asigna responsables."); var c=workflow.locked(id); workflow.model(c).requireInitialResponsible(r.userId()); setResponsible(c,r.userId(),"Asignación inicial por jefatura"); }
    public void changeResponsible(UUID id,ResponsibleChange r) { require(chief(),"Solo jefatura cambia o retira responsables."); var c=workflow.locked(id); workflow.model(c).requireResponsibleChange(r.expectedResponsibleId(),r.userId(),r.reason()); setResponsible(c,r.userId(),r.reason()); }
    public List<Map<String,Object>> responsibleHistory(UUID id) { require(chief(),"Solo jefatura consulta el historial de responsables."); return repository.responsibleHistory(id); }
    private void setResponsible(Map<String,Object> c,UUID user,String reason) {
        UUID id=id(c,"id"); ledger.ensureOpen(c); valid(user==null || text(c,"kind").equals("SERVICIO"),"El ingreso no requiere un responsable de servicio."); valid(time(c,"start_at").isAfter(now()) && c.get("opened_at")==null && !Set.of("CANCELADA","FINALIZADA").contains(c.get("status")),"Asigna al responsable antes del servicio.");
        if(user!=null) {
            enrollments.lockVolunteer(user); valid(workflow.qualified(c,user,"RESPONSABLE"),"El responsable debe ser un voluntario graduado activo.");
            var own=enrollments.byUser(id,user,false); valid(own.size()==1 && "CONFIRMADA".equals(own.get(0).get("status")) && Set.of("SOCORRISTA","GRADUADO","FORMACION").contains(own.get(0).get("position")),"Primero inscribe a la persona como voluntario general en esta convocatoria.");
            valid(!workflow.overlap(c,user),"El responsable tiene otro servicio en ese horario."); valid(workflow.canAdmit(c,user,"RESPONSABLE",user),"El responsable debe caber dentro del total sin ocupar el espacio reservado para piloto o radioperador."); valid(!enrollments.replacementRequested(id,user),"Resuelve primero la solicitud de reemplazo de esta persona.");
        }
        repository.changeResponsible(id,id(c,"responsible_id"),user,actor().id(),now(),reason); workflow.synchronize(workflow.locked(id)); workflow.announce(workflow.locked(id),"Responsable actualizado por jefatura.");
    }
    public List<Map<String,Object>> schedule(LocalDate date) { maintenance.refresh(); return repository.schedule(date,chief()?null:actor().id()).stream().map(this::presentation).toList(); }
    public Document schedulePdf(LocalDate date) {
        require(chief(),"Solo jefatura puede descargar la programación en PDF."); var lines=new ArrayList<String>();
        for(var c:schedule(date)) { lines.add(Objects.toString(c.get("service_code"),"Sin código")+" - "+c.get("title")); lines.add(c.get("service_type")+" | "+time(c,"start_at")+" a "+time(c,"end_at")); lines.add("Lugar: "+c.get("location")+" | Responsable: "+Objects.toString(c.get("responsible_name"),"Sin asignar")); lines.add("Cobertura: "+c.get("coverage")+" | Ejecución: "+c.get("execution")); lines.add("Total de voluntarios: "+workflow.total(c)+" | Mínimo de graduados dentro del total: "+c.get("graduate_slots")); for(var e:applicants(id(c,"id"))) lines.add(e.get("position")+": "+e.get("name")+" "+e.get("surnames")+" - "+e.get("status")); lines.add(""); }
        if(lines.isEmpty()) lines.add("No hay servicios para esta fecha."); return new Document("programacion-"+date+".pdf","Programación general - "+date,lines);
    }
    public List<Map<String,Object>> myPhotos() { return repository.photos(actor().id()); }
}
