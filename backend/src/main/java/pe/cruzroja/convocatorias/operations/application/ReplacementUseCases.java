package pe.cruzroja.convocatorias.operations.application;

import static pe.cruzroja.convocatorias.shared.application.Projection.*;
import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

import java.util.*;
import pe.cruzroja.convocatorias.operations.domain.*;
import pe.cruzroja.convocatorias.shared.application.*;

/** Un único cambio transaccional: resolución, relevo de la plaza, permisos y avisos. */
public class ReplacementUseCases extends UseCaseSupport {
    private final JustificationRepository justificationRecords;
    private final AttendanceDirectoryRepository userDirectory;
    private final EnrollmentRepository enrollments;
    private final CalloutWorkflow workflow;
    private final MonthlyLedger ledger;
    private final ImageInspector images;

    public ReplacementUseCases(
            JustificationRepository justificationRecords,
            AttendanceDirectoryRepository userDirectory,
            EnrollmentRepository enrollments,
            CalloutWorkflow workflow,
            MonthlyLedger ledger,
            ImageInspector images,
            ExecutionContext context,
            NotificationPublisher notifications) {
        super(context, notifications);
        this.justificationRecords = justificationRecords;
        this.userDirectory = userDirectory;
        this.enrollments = enrollments;
        this.workflow = workflow;
        this.ledger = ledger;
        this.images = images;
    }

    public record Request(String reason, String proposedReplacementName) {
        public Request {
            required(reason, 3000, "Motivo y sustento");
            if (proposedReplacementName != null)
                required(proposedReplacementName, 281, "Nombre completo del reemplazo propuesto");
        }
    }

    public record Resolution(
            boolean approved,
            String comment,
            UUID replacementUserId,
            boolean requirementsVerified) {
        public Resolution {
            required(comment, 3000, "Motivo de la resolución");
        }
    }

    public Map<String, UUID> submit(UUID enrollmentId, Request request, byte[] evidenceImage) {
        require(!chief(), "Jefatura no ocupa plazas de voluntario.");
        var initial = enrollments.find(enrollmentId, false);
        var callout = workflow.locked(id(initial, "callout_id"));
        var enrollment = enrollments.find(enrollmentId, true);
        require(
                actor().id().equals(enrollment.get("volunteer_id")),
                "Solo puedes solicitar tu propio reemplazo.");
        valid(
                "CONFIRMADA".equals(enrollment.get("status"))
                        && "SERVICIO".equals(callout.get("kind"))
                        && isBeforeOperation(callout),
                "La inscripción no admite una solicitud de reemplazo.");
        ReplacementRequest.submit(
                request.reason(),
                request.proposedReplacementName(),
                time(callout, "start_at"),
                now());
        ledger.ensureOpen(callout);
        ensureNoPendingReplacement(enrollmentId);
        var evidence = EvidenceFile.inspect(evidenceImage, images);
        UUID requestId = UUID.randomUUID();
        justificationRecords.requestReplacement(
                requestId,
                enrollmentId,
                actor().id(),
                request.reason().trim(),
                request.proposedReplacementName() == null
                        ? null
                        : request.proposedReplacementName().trim(),
                now());
        justificationRecords.evidence(requestId, evidence);
        enrollments.cancellation(enrollmentId, request.reason(), now());
        notifyReplacementRequested(requestId, callout, request.proposedReplacementName());
        return Map.of("id", requestId);
    }

    /** El servicio sigue abierto o cerrado a inscripciones, pero todavía no se inició. */
    private static boolean isBeforeOperation(Map<String, Object> callout) {
        return callout.get("opened_at") == null
                && Set.of("ABIERTA", "CERRADA").contains(callout.get("status"));
    }

    private void ensureNoPendingReplacement(UUID enrollmentId) {
        valid(
                justificationRecords.justifications(actor().id()).stream()
                        .noneMatch(
                                justification ->
                                        enrollmentId.equals(justification.get("enrollment_id"))
                                                && "REEMPLAZO".equals(justification.get("kind"))
                                                && "PENDIENTE".equals(justification.get("status"))),
                "Ya tienes una solicitud pendiente para esta inscripción.");
    }

    private void notifyReplacementRequested(
            UUID requestId, Map<String, Object> callout, String proposedReplacementName) {
        var justification = justificationRecords.justification(requestId, false);
        var recipients = new LinkedHashSet<>(userDirectory.chiefs());
        if (callout.get("responsible_id") != null
                && !actor().id().equals(callout.get("responsible_id")))
            recipients.add(id(callout, "responsible_id"));
        String link = "/justifications?request=" + requestId;
        for (var recipient : recipients)
            notifyUser(
                    recipient,
                    "REEMPLAZO",
                    justification.get("name")
                            + " "
                            + justification.get("surnames")
                            + " solicita reemplazo en "
                            + callout.get("title")
                            + (proposedReplacementName == null
                                    ? ". Solicita que Jefatura busque y asigne a la persona."
                                    : ". Persona propuesta: " + proposedReplacementName)
                            + ". Solo Jefatura puede asignar y resolver.",
                    link);
        notifyUser(
                actor().id(),
                "REEMPLAZO",
                "Solicitud recibida. Conservas tu plaza hasta que Jefatura asigne y apruebe el reemplazo.",
                link);
    }

    public Map<String, Object> detail(UUID requestId) {
        var justification = justificationRecords.justification(requestId, false);
        if (chief() || actor().id().equals(justification.get("volunteer_id"))) {
            justification.put("history", justificationRecords.justificationHistory(requestId));
            return justification;
        }
        // Un responsable recibe solo el resumen operativo de una solicitud puntual, nunca el
        // listado ni el sustento privado.
        var callout = workflow.locked(id(justification, "callout_id"));
        require(
                "REEMPLAZO".equals(justification.get("kind"))
                        && actor().id().equals(callout.get("responsible_id"))
                        && workflow.canOperate(callout),
                "No puedes consultar esta justificación.");
        var summary = new LinkedHashMap<String, Object>();
        for (String key :
                List.of(
                        "id",
                        "name",
                        "surnames",
                        "title",
                        "start_at",
                        "end_at",
                        "kind",
                        "status",
                        "proposed_replacement_name",
                        "assigned_replacement_name",
                        "submitted_at")) summary.put(key, justification.get(key));
        summary.put("operational_only", true);
        return summary;
    }

    public EvidenceFile evidence(UUID requestId) {
        var justification = justificationRecords.justification(requestId, false);
        require(
                chief() || actor().id().equals(justification.get("volunteer_id")),
                "La evidencia es privada: solo el solicitante y Jefatura pueden verla.");
        return justificationRecords.evidence(requestId);
    }

    public List<Map<String, Object>> candidates(UUID requestId) {
        require(chief(), "Solo Jefatura puede consultar y asignar reemplazos.");
        var justification = justificationRecords.justification(requestId, false);
        valid(
                "REEMPLAZO".equals(justification.get("kind")),
                "Esta justificación no solicita un reemplazo.");
        var enrollment = enrollments.find(id(justification, "enrollment_id"), false);
        var callout = workflow.locked(id(justification, "callout_id"));
        if (!acceptsReplacement(justification, enrollment, callout)) return List.of();
        return enrollments.candidates(id(callout, "id")).stream()
                .filter(
                        user ->
                                !user.get("id").equals(enrollment.get("volunteer_id"))
                                        && compatible(callout, enrollment, id(user, "id")))
                .toList();
    }

    private boolean acceptsReplacement(
            Map<String, Object> justification,
            Map<String, Object> enrollment,
            Map<String, Object> callout) {
        return "PENDIENTE".equals(justification.get("status"))
                && "CONFIRMADA".equals(enrollment.get("status"))
                && callout.get("opened_at") == null
                && time(callout, "start_at").isAfter(now())
                && Set.of("ABIERTA", "CERRADA").contains(callout.get("status"));
    }

    private boolean compatible(
            Map<String, Object> callout, Map<String, Object> enrollment, UUID user) {
        String position = text(enrollment, "position");
        return workflow.qualified(callout, user, position)
                && !workflow.overlap(callout, user)
                && workflow.canAdmit(callout, user, position, id(enrollment, "volunteer_id"));
    }

    public void resolve(UUID requestId, Resolution resolution) {
        require(chief(), "Solo Jefatura asigna reemplazos y resuelve justificaciones.");
        var initial = justificationRecords.justification(requestId, false);
        var callout = workflow.locked(id(initial, "callout_id"));
        var justification = justificationRecords.justification(requestId, true);
        valid(
                "REEMPLAZO".equals(justification.get("kind"))
                        && "PENDIENTE".equals(justification.get("status")),
                "La solicitud de reemplazo ya fue resuelta o no corresponde a este trámite.");
        var enrollment = enrollments.find(id(justification, "enrollment_id"), true);
        if (resolution.approved())
            assignReplacement(requestId, resolution, callout, enrollment, justification);
        else enrollments.clearCancellation(id(enrollment, "id"));
        justificationRecords.decision(
                new Justification(requestId, text(justification, "status"))
                        .resolve(
                                resolution.approved(),
                                resolution.comment(),
                                actor().id(),
                                now(),
                                0,
                                0));
        notifyResolution(requestId, resolution, callout, justification);
    }

    private void assignReplacement(
            UUID requestId,
            Resolution resolution,
            Map<String, Object> callout,
            Map<String, Object> enrollment,
            Map<String, Object> justification) {
        UUID selected = resolution.replacementUserId();
        ReplacementRequest.approve(
                id(enrollment, "volunteer_id"),
                selected,
                resolution.requirementsVerified(),
                time(callout, "start_at"),
                now());
        ledger.ensureOpen(callout);
        valid(
                "CONFIRMADA".equals(enrollment.get("status")) && isBeforeOperation(callout),
                "El servicio o la inscripción ya no admiten el reemplazo.");
        enrollments.lockVolunteer(selected);
        valid(
                compatible(callout, enrollment, selected),
                "El reemplazo no cumple la graduación, habilitaciones, cupos reservados o disponibilidad horaria.");
        var existingEnrollments = enrollments.byUser(id(callout, "id"), selected, true);
        valid(
                existingEnrollments.isEmpty()
                        || Set.of("PENDIENTE", "EXPIRADA")
                                .contains(existingEnrollments.get(0).get("status")),
                "La persona ya tiene una inscripción o resolución en este servicio.");
        valid(
                enrollments.pendingReplacements(id(enrollment, "id")) == 0,
                "Hay un reemplazo histórico pendiente: resuélvelo primero.");
        // El bloqueo de convocatoria protege la plaza durante todo el cambio, incluso al
        // relevar al responsable.
        enrollments.cancelReplaced(id(enrollment, "id"));
        enrollments.save(
                existingEnrollments.isEmpty() ? null : id(existingEnrollments.get(0), "id"),
                selected,
                id(callout, "id"),
                text(enrollment, "position"),
                id(enrollment, "id"),
                actor().id(),
                now());
        var replacementEnrollment = enrollments.byUser(id(callout, "id"), selected, true).get(0);
        enrollments.review(id(replacementEnrollment, "id"), true, resolution.comment(), now());
        justificationRecords.assignedReplacement(requestId, selected);
        workflow.assignResponsible(callout);
        workflow.synchronize(callout);
        notifyUser(
                selected,
                "REEMPLAZO",
                "Jefatura te asignó como reemplazo de "
                        + justification.get("name")
                        + " "
                        + justification.get("surnames")
                        + " en "
                        + callout.get("title")
                        + ". Puesto: "
                        + enrollment.get("position"),
                "/callouts/" + callout.get("id"));
    }

    private void notifyResolution(
            UUID requestId,
            Resolution resolution,
            Map<String, Object> callout,
            Map<String, Object> justification) {
        String link = "/justifications?request=" + requestId;
        notifyUser(
                id(justification, "volunteer_id"),
                "REEMPLAZO",
                "Solicitud de reemplazo "
                        + (resolution.approved()
                                ? "aprobada; se canceló tu inscripción sin generar inasistencia"
                                : "rechazada; sigues inscrito")
                        + ". "
                        + resolution.comment(),
                link);
        boolean responsibleIsAnotherPerson =
                callout.get("responsible_id") != null
                        && !Objects.equals(
                                callout.get("responsible_id"), justification.get("volunteer_id"));
        if (responsibleIsAnotherPerson)
            notifyUser(
                    id(callout, "responsible_id"),
                    "REEMPLAZO",
                    "Solicitud de "
                            + justification.get("name")
                            + " "
                            + justification.get("surnames")
                            + ": "
                            + (resolution.approved() ? "APROBADA" : "RECHAZADA"),
                    link);
    }
}
