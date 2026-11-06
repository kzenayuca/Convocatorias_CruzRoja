package pe.cruzroja.convocatorias.membership.application;

import java.util.*;
import pe.cruzroja.convocatorias.membership.domain.AnnualEnrollment;
import pe.cruzroja.convocatorias.shared.application.*;
import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

public class AnnualEnrollmentUseCases extends UseCaseSupport {
    private final AnnualEnrollmentRepository repository;
    private final VolunteerRepository volunteers;
    public AnnualEnrollmentUseCases(AnnualEnrollmentRepository repository,VolunteerRepository volunteers,ExecutionContext context,NotificationPublisher notifications) { super(context,notifications); this.repository=repository; this.volunteers=volunteers; }
    public record Request(int year) { public Request { new AnnualEnrollment(year); } }
    public Map<String,Object> mine() { return detail(actor().id()); }
    public Map<String,Object> detail(UUID user) {
        require(chief() || actor().id().equals(user),"Solo puedes consultar tu inscripción anual.");
        var p=volunteers.profile(user); return Map.of("applicable","VOLUNTARIO".equals(p.get("role")) && Boolean.TRUE.equals(p.get("graduated")),"current_year",now().getYear(),"enrollments",repository.list(user));
    }
    public void register(UUID user,Request r) {
        require(chief() || actor().id().equals(user),"Solo Jefatura puede inscribir anualmente a otra persona.");
        volunteers.lock(user); var p=volunteers.profile(user);
        valid("VOLUNTARIO".equals(p.get("role")) && "ACTIVO".equals(p.get("status")) && Boolean.TRUE.equals(p.get("graduated")),"La inscripción anual corresponde a voluntarios graduados activos.");
        valid(r.year()>=now().getYear(),"No se crean inscripciones anuales retroactivas.");
        if(repository.register(user,r.year(),actor().id(),now())) notifyUser(user,"CICLO_ANUAL","Inscripción al ciclo enero-diciembre "+r.year()+" registrada. Tus horas anteriores se conservan.","/profile?section=annual");
    }
}
