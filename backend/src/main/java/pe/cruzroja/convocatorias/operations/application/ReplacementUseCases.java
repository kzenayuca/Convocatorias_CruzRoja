package pe.cruzroja.convocatorias.operations.application;

import java.util.*;
import pe.cruzroja.convocatorias.operations.domain.*;
import pe.cruzroja.convocatorias.shared.application.*;
import static pe.cruzroja.convocatorias.shared.application.Projection.*;
import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

/** Un único cambio transaccional: resolución, relevo de la plaza, permisos y avisos. */
public class ReplacementUseCases extends UseCaseSupport {
    private final AttendanceRepository requests;
    private final EnrollmentRepository enrollments;
    private final CalloutWorkflow workflow;
    private final MonthlyLedger ledger;
    private final ImageInspector images;
    public ReplacementUseCases(AttendanceRepository requests,EnrollmentRepository enrollments,CalloutWorkflow workflow,MonthlyLedger ledger,ImageInspector images,ExecutionContext context,NotificationPublisher notifications) {
        super(context,notifications); this.requests=requests; this.enrollments=enrollments; this.workflow=workflow; this.ledger=ledger; this.images=images;
    }
    public record Request(String reason,String proposedReplacementName) {
        public Request { required(reason,3000,"Motivo y sustento"); if(proposedReplacementName!=null) required(proposedReplacementName,281,"Nombre completo del reemplazo propuesto"); }
    }
    public record Resolution(boolean approved,String comment,UUID replacementUserId,boolean requirementsVerified) {
        public Resolution { required(comment,3000,"Motivo de la resolución"); }
    }
    public Map<String,UUID> submit(UUID enrollment,Request r,byte[] bytes) {
        require(!chief(),"Jefatura no ocupa plazas de voluntario.");
        var initial=enrollments.find(enrollment,false); var c=workflow.locked(id(initial,"callout_id")); var e=enrollments.find(enrollment,true);
        require(actor().id().equals(e.get("volunteer_id")),"Solo puedes solicitar tu propio reemplazo.");
        valid("CONFIRMADA".equals(e.get("status")) && "SERVICIO".equals(c.get("kind")) && c.get("opened_at")==null && Set.of("ABIERTA","CERRADA").contains(c.get("status")),"La inscripción no admite una solicitud de reemplazo.");
        ReplacementRequest.submit(r.reason(),r.proposedReplacementName(),time(c,"start_at"),now()); ledger.ensureOpen(c);
        valid(requests.justifications(actor().id()).stream().noneMatch(j -> enrollment.equals(j.get("enrollment_id")) && "REEMPLAZO".equals(j.get("kind")) && "PENDIENTE".equals(j.get("status"))),"Ya tienes una solicitud pendiente para esta inscripción.");
        var evidence=EvidenceFile.inspect(bytes,images); UUID request=UUID.randomUUID();
        requests.requestReplacement(request,enrollment,actor().id(),r.reason().trim(),r.proposedReplacementName()==null?null:r.proposedReplacementName().trim(),now()); requests.evidence(request,evidence);
        enrollments.cancellation(enrollment,r.reason(),now());
        var j=requests.justification(request,false); var recipients=new LinkedHashSet<>(requests.chiefs());
        if(c.get("responsible_id")!=null && !actor().id().equals(c.get("responsible_id"))) recipients.add(id(c,"responsible_id"));
        String link="/justifications?request="+request;
        for(var recipient:recipients) notifyUser(recipient,"REEMPLAZO",j.get("name")+" "+j.get("surnames")+" solicita reemplazo en "+c.get("title")+(r.proposedReplacementName()==null?". Solicita que Jefatura busque y asigne a la persona.":". Persona propuesta: "+r.proposedReplacementName())+". Solo Jefatura puede asignar y resolver.",link);
        notifyUser(actor().id(),"REEMPLAZO","Solicitud recibida. Conservas tu plaza hasta que Jefatura asigne y apruebe el reemplazo.",link);
        return Map.of("id",request);
    }
    public Map<String,Object> detail(UUID id) {
        var j=requests.justification(id,false);
        if(chief() || actor().id().equals(j.get("volunteer_id"))) { j.put("history",requests.justificationHistory(id)); return j; }
        // Un responsable recibe solo el resumen operativo de una solicitud puntual, nunca el listado ni el sustento privado.
        var c=workflow.locked(id(j,"callout_id"));
        require("REEMPLAZO".equals(j.get("kind")) && actor().id().equals(c.get("responsible_id")) && workflow.canOperate(c),"No puedes consultar esta justificación.");
        var summary=new LinkedHashMap<String,Object>();
        for(String key:List.of("id","name","surnames","title","start_at","end_at","kind","status","proposed_replacement_name","assigned_replacement_name","submitted_at")) summary.put(key,j.get(key));
        summary.put("operational_only",true); return summary;
    }
    public EvidenceFile evidence(UUID id) {
        var j=requests.justification(id,false); require(chief() || actor().id().equals(j.get("volunteer_id")),"La evidencia es privada: solo el solicitante y Jefatura pueden verla."); return requests.evidence(id);
    }
    public List<Map<String,Object>> candidates(UUID id) {
        require(chief(),"Solo Jefatura puede consultar y asignar reemplazos."); var j=requests.justification(id,false);
        valid("REEMPLAZO".equals(j.get("kind")),"Esta justificación no solicita un reemplazo.");
        var e=enrollments.find(id(j,"enrollment_id"),false); var c=workflow.locked(id(j,"callout_id"));
        if(!"PENDIENTE".equals(j.get("status")) || !"CONFIRMADA".equals(e.get("status")) || c.get("opened_at")!=null || !time(c,"start_at").isAfter(now()) || !Set.of("ABIERTA","CERRADA").contains(c.get("status"))) return List.of();
        return enrollments.candidates(id(c,"id")).stream().filter(u -> !u.get("id").equals(e.get("volunteer_id")) && compatible(c,e,id(u,"id"))).toList();
    }
    private boolean compatible(Map<String,Object> c,Map<String,Object> e,UUID user) {
        return workflow.qualified(c,user,text(e,"position")) && !workflow.overlap(c,user) && workflow.canAdmit(c,user,text(e,"position"),id(e,"volunteer_id"));
    }
    public void resolve(UUID id,Resolution r) {
        require(chief(),"Solo Jefatura asigna reemplazos y resuelve justificaciones."); var initial=requests.justification(id,false); var c=workflow.locked(id(initial,"callout_id")); var j=requests.justification(id,true);
        valid("REEMPLAZO".equals(j.get("kind")) && "PENDIENTE".equals(j.get("status")),"La solicitud de reemplazo ya fue resuelta o no corresponde a este trámite.");
        var e=enrollments.find(id(j,"enrollment_id"),true); UUID selected=r.replacementUserId();
        if(r.approved()) {
            ReplacementRequest.approve(id(e,"volunteer_id"),selected,r.requirementsVerified(),time(c,"start_at"),now()); ledger.ensureOpen(c);
            valid("CONFIRMADA".equals(e.get("status")) && c.get("opened_at")==null && Set.of("ABIERTA","CERRADA").contains(c.get("status")),"El servicio o la inscripción ya no admiten el reemplazo.");
            enrollments.lockVolunteer(selected); valid(compatible(c,e,selected),"El reemplazo no cumple la graduación, habilitaciones, cupos reservados o disponibilidad horaria.");
            var own=enrollments.byUser(id(c,"id"),selected,true);
            valid(own.isEmpty() || Set.of("PENDIENTE","EXPIRADA").contains(own.get(0).get("status")),"La persona ya tiene una inscripción o resolución en este servicio.");
            valid(enrollments.pendingReplacements(id(e,"id"))==0,"Hay un reemplazo histórico pendiente: resuélvelo primero.");
            // El bloqueo de convocatoria protege la plaza durante todo el cambio, incluso al relevar al responsable.
            enrollments.cancelReplaced(id(e,"id"));
            enrollments.save(own.isEmpty()?null:id(own.get(0),"id"),selected,id(c,"id"),text(e,"position"),id(e,"id"),actor().id(),now());
            var replacement=enrollments.byUser(id(c,"id"),selected,true).get(0);
            enrollments.review(id(replacement,"id"),true,r.comment(),now()); requests.assignedReplacement(id,selected);
            workflow.assignResponsible(c); workflow.synchronize(c);
            notifyUser(selected,"REEMPLAZO","Jefatura te asignó como reemplazo de "+j.get("name")+" "+j.get("surnames")+" en "+c.get("title")+". Puesto: "+e.get("position"),"/callouts/"+c.get("id"));
        } else enrollments.clearCancellation(id(e,"id"));
        requests.decision(new Justification(id,text(j,"status")).resolve(r.approved(),r.comment(),actor().id(),now(),0,0));
        String link="/justifications?request="+id;
        notifyUser(id(j,"volunteer_id"),"REEMPLAZO","Solicitud de reemplazo "+(r.approved()?"aprobada; se canceló tu inscripción sin generar inasistencia":"rechazada; sigues inscrito")+". "+r.comment(),link);
        if(c.get("responsible_id")!=null && !Objects.equals(c.get("responsible_id"),j.get("volunteer_id"))) notifyUser(id(c,"responsible_id"),"REEMPLAZO","Solicitud de "+j.get("name")+" "+j.get("surnames")+": "+(r.approved()?"APROBADA":"RECHAZADA"),link);
    }
}
