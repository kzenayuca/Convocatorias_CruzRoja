package pe.cruzroja.convocatorias.operations.application;

import static pe.cruzroja.convocatorias.shared.application.Projection.id;
import static pe.cruzroja.convocatorias.shared.application.Projection.text;
import static pe.cruzroja.convocatorias.shared.application.Projection.time;
import static pe.cruzroja.convocatorias.shared.domain.Rules.valid;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import pe.cruzroja.convocatorias.operations.domain.EnrollmentRepository;
import pe.cruzroja.convocatorias.shared.application.ExecutionContext;
import pe.cruzroja.convocatorias.shared.application.NotificationPublisher;
import pe.cruzroja.convocatorias.shared.application.UseCaseSupport;

/** Asigna, cambia y consulta responsables de convocatorias. */
public class ResponsibleManagement extends UseCaseSupport {
    private final CalloutRepository repository;
    private final EnrollmentRepository enrollments;
    private final CalloutWorkflow workflow;
    private final MonthlyLedger ledger;

    public ResponsibleManagement(
            CalloutRepository repository,
            EnrollmentRepository enrollments,
            CalloutWorkflow workflow,
            MonthlyLedger ledger,
            ExecutionContext context,
            NotificationPublisher notifications) {
        super(context, notifications);
        this.repository = repository;
        this.enrollments = enrollments;
        this.workflow = workflow;
        this.ledger = ledger;
    }

    public List<Map<String, Object>> candidates(UUID calloutId) {
        require(chief(), "Solo jefatura asigna responsables.");
        var callout = repository.snapshot(calloutId);
        return repository.responsibleCandidates(calloutId).stream()
                .filter(user -> !workflow.overlap(callout, id(user, "id")))
                .toList();
    }

    public void assign(UUID calloutId, ServicesUseCases.Responsible request) {
        require(chief(), "Solo jefatura asigna responsables.");
        var callout = workflow.locked(calloutId);
        workflow.model(callout).requireInitialResponsible(request.userId());
        setResponsible(callout, request.userId(), "Asignación inicial por jefatura");
    }

    public void change(UUID calloutId, ServicesUseCases.ResponsibleChange request) {
        require(chief(), "Solo jefatura cambia o retira responsables.");
        var callout = workflow.locked(calloutId);
        workflow.model(callout)
                .requireResponsibleChange(
                        request.expectedResponsibleId(), request.userId(), request.reason());
        setResponsible(callout, request.userId(), request.reason());
    }

    public List<Map<String, Object>> history(UUID calloutId) {
        require(chief(), "Solo jefatura consulta el historial de responsables.");
        return repository.responsibleHistory(calloutId);
    }

    private void setResponsible(Map<String, Object> callout, UUID user, String reason) {
        UUID calloutId = id(callout, "id");
        ledger.ensureOpen(callout);
        valid(
                user == null || text(callout, "kind").equals("SERVICIO"),
                "El ingreso no requiere un responsable de servicio.");
        valid(
                time(callout, "start_at").isAfter(now())
                        && callout.get("opened_at") == null
                        && !Set.of("CANCELADA", "FINALIZADA").contains(callout.get("status")),
                "Asigna al responsable antes del servicio.");
        if (user != null) {
            enrollments.lockVolunteer(user);
            valid(
                    workflow.qualified(callout, user, "RESPONSABLE"),
                    "El responsable debe ser un voluntario graduado activo.");
            var own = enrollments.byUser(calloutId, user);
            valid(
                    own.size() == 1
                            && "CONFIRMADA".equals(own.get(0).get("status"))
                            && Set.of("SOCORRISTA", "GRADUADO", "FORMACION")
                                    .contains(own.get(0).get("position")),
                    "Primero inscribe a la persona como voluntario general en esta convocatoria.");
            valid(
                    !workflow.overlap(callout, user),
                    "El responsable tiene otro servicio en ese horario.");
            valid(
                    workflow.canAdmit(callout, user, "RESPONSABLE", user),
                    "El responsable debe caber dentro del total sin ocupar el espacio reservado para piloto o radioperador.");
            valid(
                    !enrollments.replacementRequested(calloutId, user),
                    "Resuelve primero la solicitud de reemplazo de esta persona.");
        }
        repository.changeResponsible(
                calloutId, id(callout, "responsible_id"), user, actor().id(), now(), reason);
        workflow.synchronize(workflow.locked(calloutId));
        workflow.announce(workflow.locked(calloutId), "Responsable actualizado por jefatura.");
    }
}
