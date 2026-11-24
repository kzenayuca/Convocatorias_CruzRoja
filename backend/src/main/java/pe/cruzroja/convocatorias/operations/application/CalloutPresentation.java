package pe.cruzroja.convocatorias.operations.application;

import static pe.cruzroja.convocatorias.shared.application.Projection.id;
import static pe.cruzroja.convocatorias.shared.application.Projection.text;

import java.util.Map;
import java.util.Set;
import pe.cruzroja.convocatorias.operations.domain.EnrollmentRepository;
import pe.cruzroja.convocatorias.shared.application.ExecutionContext;
import pe.cruzroja.convocatorias.shared.application.UseCaseSupport;

/** Construye la representación pública de una convocatoria. */
public class CalloutPresentation extends UseCaseSupport {
    private final CalloutRepository repository;
    private final EnrollmentRepository enrollments;
    private final CalloutWorkflow workflow;

    public CalloutPresentation(
            CalloutRepository repository,
            EnrollmentRepository enrollments,
            CalloutWorkflow workflow,
            ExecutionContext context) {
        super(context, null);
        this.repository = repository;
        this.enrollments = enrollments;
        this.workflow = workflow;
    }

    public Map<String, Object> present(Map<String, Object> callout) {
        callout.put("total_volunteers", workflow.total(callout));
        callout.put("positions", workflow.positions(callout));
        callout.put("can_edit", workflow.canEdit(callout));
        callout.put("can_operate", workflow.canOperate(callout));
        callout.put("can_view_team", workflow.canEdit(callout));
        callout.put("can_upload_photo", workflow.canOperate(callout));
        callout.put(
                "eligible_positions",
                CalloutWorkflow.POSITIONS.stream()
                        .filter(
                                position ->
                                        !Set.of("RESPONSABLE", "FORMACION").contains(position)
                                                && (!position.equals("GRADUADO")
                                                        || workflow.capacity(callout, "GRADUADO")
                                                                > 0)
                                                && workflow.qualified(
                                                        callout, actor().id(), position))
                        .toList());
        var own = enrollments.byUser(id(callout, "id"), actor().id(), false);
        callout.put(
                "my_enrollment",
                own.isEmpty()
                        ? null
                        : Map.of(
                                "id",
                                own.get(0).get("id"),
                                "status",
                                own.get(0).get("status"),
                                "position",
                                own.get(0).get("position")));
        callout.put("contact", "Consultar a jefatura");
        if ((chief() || !own.isEmpty() && "CONFIRMADA".equals(own.get(0).get("status")))
                && callout.get("responsible_id") != null) {
            String phone =
                    repository.responsiblePhone(id(callout, "id"), id(callout, "responsible_id"));
            if (phone != null && !phone.isBlank()) callout.put("contact", phone);
        }
        callout.put("coverage", workflow.complete(callout) ? "COMPLETA" : "PENDIENTE");
        callout.put(
                "execution",
                text(callout, "status").equals("CANCELADA")
                        ? "CANCELADO"
                        : callout.get("closed_at") != null
                                        || text(callout, "status").equals("FINALIZADA")
                                ? "FINALIZADO"
                                : callout.get("opened_at") != null ? "EN CURSO" : "PROGRAMADO");
        return callout;
    }
}
