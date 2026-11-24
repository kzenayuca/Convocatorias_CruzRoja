package pe.cruzroja.convocatorias.membership.application;

import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

import java.util.*;
import pe.cruzroja.convocatorias.membership.domain.AnnualEnrollment;
import pe.cruzroja.convocatorias.shared.application.*;

public class AnnualEnrollmentUseCases extends UseCaseSupport {
    private final AnnualEnrollmentRepository repository;
    private final VolunteerRepository volunteers;

    public AnnualEnrollmentUseCases(
            AnnualEnrollmentRepository repository,
            VolunteerRepository volunteers,
            ExecutionContext context,
            NotificationPublisher notifications) {
        super(context, notifications);
        this.repository = repository;
        this.volunteers = volunteers;
    }

    public record Request(int year) {
        public Request {
            new AnnualEnrollment(year);
        }
    }

    public AnnualEnrollmentDetail mine() {
        return detail(actor().id());
    }

    public AnnualEnrollmentDetail detail(UUID user) {
        require(
                chief() || actor().id().equals(user),
                "Solo puedes consultar tu inscripción anual.");
        var p = volunteers.profile(user);
        return new AnnualEnrollmentDetail(
                "VOLUNTARIO".equals(p.role()) && p.graduated(),
                now().getYear(),
                repository.list(user));
    }

    public void register(UUID user, Request r) {
        require(
                chief() || actor().id().equals(user),
                "Solo Jefatura puede inscribir anualmente a otra persona.");
        volunteers.lock(user);
        var p = volunteers.profile(user);
        valid(
                "VOLUNTARIO".equals(p.role()) && "ACTIVO".equals(p.status()) && p.graduated(),
                "La inscripción anual corresponde a voluntarios graduados activos.");
        valid(r.year() >= now().getYear(), "No se crean inscripciones anuales retroactivas.");
        if (repository.register(user, r.year(), actor().id(), now()))
            notifyUser(
                    user,
                    "CICLO_ANUAL",
                    "Inscripción al ciclo enero-diciembre "
                            + r.year()
                            + " registrada. Tus horas anteriores se conservan.",
                    "/profile?section=annual");
    }
}
