package pe.cruzroja.convocatorias.operations.application;

import java.util.*;
import pe.cruzroja.convocatorias.operations.domain.*;
import pe.cruzroja.convocatorias.shared.application.*;
import static pe.cruzroja.convocatorias.shared.application.Projection.*;

/** Servicio de aplicación compartido por casos de uso, no un controlador ni repositorio SQL. */
public class CalloutWorkflow extends UseCaseSupport implements ServiceParticipation {
    public static final List<String> POSITIONS=List.of("RESPONSABLE","SOCORRISTA","GRADUADO","FORMACION","RADIOPERADOR","PILOTO");
    private final CalloutRepository repository;
    private final EnrollmentRepository enrollments;
    public CalloutWorkflow(CalloutRepository repository,EnrollmentRepository enrollments,ExecutionContext context,NotificationPublisher notifications) { super(context,notifications); this.repository=repository; this.enrollments=enrollments; }
    public Map<String,Object> locked(UUID id) { return repository.snapshot(id,true); }
    public Callout model(Map<String,Object> c) {
        return new Callout(id(c,"id"),new ServiceWindow(time(c,"start_at"),time(c,"end_at"),time(c,"deadline")),text(c,"kind"),text(c,"status"),id(c,"responsible_id"),time(c,"opened_at"),time(c,"closed_at"),new TeamCapacity(total(c),capacity(c,"GRADUADO"),capacity(c,"RADIOPERADOR"),capacity(c,"PILOTO")),repository.members(id(c,"id")));
    }
    public boolean canEdit(Map<String,Object> c) {
        return chief() || actor().graduated() && actor().role().equals("VOLUNTARIO") && actor().id().equals(c.get("responsible_id")) && repository.members(id(c,"id")).stream().anyMatch(m -> m.volunteer().equals(actor().id()) && m.position().equals("RESPONSABLE"));
    }
    public boolean canOperate(Map<String,Object> c) { return canEdit(c); }
    public void editPermission(Map<String,Object> c) { require(canEdit(c),"Solo jefatura o el responsable graduado asignado pueden gestionar esta convocatoria."); }
    public void operationPermission(Map<String,Object> c) { require(canOperate(c),"Solo jefatura o el encargado asignado pueden operar el servicio."); }
    public void teamPermission(Map<String,Object> c) { require(canEdit(c),"No puedes consultar este equipo."); }
    public int capacity(Map<String,Object> c,String p) { return switch(p) { case "RESPONSABLE" -> "SERVICIO".equals(c.get("kind"))?1:0; case "SOCORRISTA","FORMACION" -> total(c); case "GRADUADO" -> ((Number)c.get("graduate_slots")).intValue(); case "RADIOPERADOR" -> ((Number)c.get("radio_slots")).intValue(); case "PILOTO" -> ((Number)c.get("pilot_slots")).intValue(); default -> 0; }; }
    public int total(Map<String,Object> c) {
        if(c.get("total_volunteers")!=null) return ((Number)c.get("total_volunteers")).intValue();
        return ((Number)c.get("socorrista_slots")).intValue()+((Number)c.get("graduate_slots")).intValue()+((Number)c.get("trainee_slots")).intValue()+((Number)c.get("radio_slots")).intValue()+((Number)c.get("pilot_slots")).intValue()+(int)repository.members(id(c,"id")).stream().filter(m -> m.position().equals("RESPONSABLE")).count();
    }
    public List<Map<String,Object>> positions(Map<String,Object> c) { var n=model(c).counts(null); return List.of(Map.of("position","TOTAL","required",total(c),"filled",n[0]),Map.of("position","GRADUADO","required",capacity(c,"GRADUADO"),"filled",n[1]),Map.of("position","RADIOPERADOR","required",capacity(c,"RADIOPERADOR"),"filled",n[2]),Map.of("position","PILOTO","required",capacity(c,"PILOTO"),"filled",n[3])); }
    public boolean fits(Map<String,Object> c) { return model(c).fits(); }
    public boolean canAdmit(Map<String,Object> c,UUID user,String position,UUID replacing) { return model(c).canAdmit(repository.participant(user).graduated(),position,replacing); }
    public boolean complete(Map<String,Object> c) { return model(c).complete(); }
    public boolean qualified(Map<String,Object> c,UUID user,String p) { return POSITIONS.contains(p) && (Set.of("GRADUADO","FORMACION").contains(p) || capacity(c,p)>0) && repository.participant(user).qualified(p) && repository.annualEligible(user,time(c,"start_at").getYear()); }
    public boolean overlap(Map<String,Object> c,UUID user) { return repository.overlaps(id(c,"id"),user,time(c,"start_at"),time(c,"end_at")); }
    public boolean compatible(Map<String,Object> c,UUID user) { return !overlap(c,user) && POSITIONS.stream().filter(p -> !p.equals("RESPONSABLE")).anyMatch(p -> qualified(c,user,p)); }
    public void synchronize(Map<String,Object> c) {
        if(!Set.of("ABIERTA","CERRADA").contains(c.get("status")) || c.get("opened_at")!=null) return;
        String next=complete(c)?"CERRADA":"ABIERTA";
        if(!next.equals(c.get("status"))) { repository.status(id(c,"id"),next); announce(c,"Estado del servicio: "+next); c.put("status",next); }
    }
    public void announce(Map<String,Object> c,String message) {
        var recipients=new LinkedHashSet<UUID>();
        for(var e:enrollments.applicants(id(c,"id"))) recipients.add(id(e,"volunteer_id"));
        if(c.get("created_by")!=null) recipients.add(id(c,"created_by")); if(c.get("responsible_id")!=null) recipients.add(id(c,"responsible_id"));
        for(var user:recipients) notifyUser(user,"CONVOCATORIA",c.get("title")+": "+message,"/callouts/"+c.get("id"));
    }
    public void cancel(Map<String,Object> c,String reason) { repository.cancel(id(c,"id"),reason); announce(c,"Cancelada. "+reason); }
    public void assignResponsible(Map<String,Object> c) {
        UUID next=repository.members(id(c,"id")).stream().filter(m -> m.position().equals("RESPONSABLE")).map(Callout.Member::volunteer).findFirst().orElse(null);
        repository.synchronizeResponsible(id(c,"id"),id(c,"responsible_id"),next,actor().id(),now());
    }
    public void lockCommitments(UUID user) { for(var id:enrollments.commitments(user)) locked(id); }
    public void reevaluate(UUID user) {
        for(var entry:enrollments.commitments(user)) { var c=locked(entry); if(!time(c,"start_at").isAfter(now())) continue;
            for(var e:enrollments.activeForUser(entry,user)) if(!qualified(c,user,text(e,"position"))) enrollments.cancelIneligible(id(e,"id"));
            assignResponsible(c); synchronize(c);
        }
    }
}
