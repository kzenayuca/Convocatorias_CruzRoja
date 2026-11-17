package pe.cruzroja.convocatorias.operations.application;

import static pe.cruzroja.convocatorias.shared.application.Projection.*;
import static pe.cruzroja.convocatorias.shared.domain.Rules.valid;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import pe.cruzroja.convocatorias.operations.domain.Enrollment;
import pe.cruzroja.convocatorias.operations.domain.EnrollmentRepository;
import pe.cruzroja.convocatorias.operations.domain.EnrollmentStatus;
import pe.cruzroja.convocatorias.operations.domain.Position;
import pe.cruzroja.convocatorias.shared.application.ExecutionContext;
import pe.cruzroja.convocatorias.shared.application.NotificationPublisher;
import pe.cruzroja.convocatorias.shared.application.UseCaseSupport;

/** Coordinates admission; Callout owns capacity and Enrollment owns commitment transitions. */
public class EnrollmentUseCases extends UseCaseSupport {
    private final CalloutRepository callouts;
    private final EnrollmentRepository enrollments;
    private final CalloutWorkflow workflow;

    public EnrollmentUseCases(
            CalloutRepository callouts,
            EnrollmentRepository enrollments,
            CalloutWorkflow workflow,
            ExecutionContext context,
            NotificationPublisher notifications) {
        super(context, notifications);
        this.callouts = callouts;
        this.enrollments = enrollments;
        this.workflow = workflow;
    }

    public void enroll(UUID id, ServicesUseCases.EnrollmentRequest request) {
        enrollFor(id, actor().id(), request, false);
    }

    public void assign(UUID id, ServicesUseCases.Assignment request) {
        require(chief(), "Solo jefatura puede inscribir directamente a otra persona.");
        valid(
                request.requirementsVerified(),
                "Jefatura debe verificar los requisitos antes de asignar.");
        enrollFor(
                id,
                request.userId(),
                new ServicesUseCases.EnrollmentRequest(true, request.position(), null),
                true);
    }

    public List<Map<String, Object>> candidates(UUID id, String position) {
        require(chief(), "Solo jefatura consulta candidatos para la inscripción directa.");
        valid(
                Set.of("SOCORRISTA", "GRADUADO", "RADIOPERADOR", "PILOTO").contains(position),
                "Puesto no válido para inscripción directa.");
        var callout = callouts.snapshot(id, false);
        if (!Set.of("ABIERTA", "CERRADA").contains(callout.get("status"))
                || callout.get("opened_at") != null
                || !time(callout, "start_at").isAfter(now())
                || !time(callout, "deadline").isAfter(now())) {
            return List.of();
        }
        return enrollments.candidates(id).stream()
                .filter(user -> workflow.qualified(callout, id(user, "id"), position))
                .filter(user -> !workflow.overlap(callout, id(user, "id")))
                .filter(user -> workflow.canAdmit(callout, id(user, "id"), position, null))
                .toList();
    }

    private void enrollFor(
            UUID id, UUID user, ServicesUseCases.EnrollmentRequest request, boolean assigned) {
        valid(
                request.replacementFor() == null,
                "Los reemplazos se solicitan en Justificaciones y solo Jefatura puede asignarlos.");
        Position.parse(request.position()).requireSelfEnrollment();
        var callout = workflow.locked(id);
        workflow.model(callout)
                .requireEnrollment(request.requirementsAccepted(), assigned, false, now());
        enrollments.lockVolunteer(user);
        valid(
                workflow.qualified(callout, user, request.position()),
                "Se requiere cuenta activa, condición/habilitación e inscripción anual del año del servicio.");
        valid(!workflow.overlap(callout, user), "La persona tiene otro servicio en ese horario.");

        var previous = enrollments.byUser(id, user, true);
        if (!previous.isEmpty()) {
            commitment(previous.get(0)).requireNewRequest();
        }
        valid(
                workflow.canAdmit(callout, user, request.position(), null),
                "No hay cupo compatible: se reserva espacio para graduados, piloto o radioperador. No hay lista de espera.");
        enrollments.save(
                previous.isEmpty() ? null : id(previous.get(0), "id"),
                user,
                id,
                request.position(),
                null,
                assigned ? actor().id() : null,
                now());
        workflow.assignResponsible(callout);
        workflow.synchronize(callout);
        var saved = enrollments.byUser(id, user, false).get(0);
        notifyUser(
                user,
                "INSCRIPCION",
                callout.get("title")
                        + ": "
                        + (assigned
                                ? "Jefatura te inscribió directamente en "
                                        + request.position()
                                        + ". "
                                : "")
                        + "ACEPTADA",
                "/services?enrollment=" + saved.get("id"));
    }

    private Enrollment commitment(Map<String, Object> row) {
        return new Enrollment(
                id(row, "id"),
                id(row, "volunteer_id"),
                id(row, "callout_id"),
                EnrollmentStatus.valueOf(text(row, "status")),
                Position.parse(text(row, "position")));
    }
}
