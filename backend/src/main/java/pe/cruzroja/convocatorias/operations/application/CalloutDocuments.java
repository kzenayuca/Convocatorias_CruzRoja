package pe.cruzroja.convocatorias.operations.application;

import static pe.cruzroja.convocatorias.shared.application.Projection.id;
import static pe.cruzroja.convocatorias.shared.application.Projection.time;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import pe.cruzroja.convocatorias.operations.domain.EnrollmentRepository;
import pe.cruzroja.convocatorias.shared.application.Document;
import pe.cruzroja.convocatorias.shared.application.ExecutionContext;
import pe.cruzroja.convocatorias.shared.application.NotificationPublisher;
import pe.cruzroja.convocatorias.shared.application.UseCaseSupport;

/** Genera documentos derivados de la programación de convocatorias. */
public class CalloutDocuments extends UseCaseSupport {
    private final CalloutRepository repository;
    private final ServiceMaintenance maintenance;
    private final CalloutPresentation presentation;
    private final CalloutWorkflow workflow;
    private final EnrollmentRepository enrollments;

    public CalloutDocuments(
            CalloutRepository repository,
            ServiceMaintenance maintenance,
            CalloutPresentation presentation,
            CalloutWorkflow workflow,
            EnrollmentRepository enrollments,
            ExecutionContext context,
            NotificationPublisher notifications) {
        super(context, notifications);
        this.repository = repository;
        this.maintenance = maintenance;
        this.presentation = presentation;
        this.workflow = workflow;
        this.enrollments = enrollments;
    }

    public List<Map<String, Object>> schedule(LocalDate date) {
        maintenance.refresh();
        return repository.schedule(date, chief() ? null : actor().id()).stream()
                .map(presentation::present)
                .toList();
    }

    public Document schedulePdf(LocalDate date) {
        require(chief(), "Solo jefatura puede descargar la programación en PDF.");
        var lines = new ArrayList<String>();
        for (var callout : schedule(date)) {
            lines.add(
                    Objects.toString(callout.get("service_code"), "Sin código")
                            + " - "
                            + callout.get("title"));
            lines.add(
                    callout.get("service_type")
                            + " | "
                            + time(callout, "start_at")
                            + " a "
                            + time(callout, "end_at"));
            lines.add(
                    "Lugar: "
                            + callout.get("location")
                            + " | Responsable: "
                            + Objects.toString(callout.get("responsible_name"), "Sin asignar"));
            lines.add(
                    "Cobertura: "
                            + callout.get("coverage")
                            + " | Ejecución: "
                            + callout.get("execution"));
            lines.add(
                    "Total de voluntarios: "
                            + workflow.total(callout)
                            + " | Mínimo de graduados dentro del total: "
                            + callout.get("graduate_slots"));
            workflow.teamPermission(repository.snapshot(id(callout, "id"), false));
            for (var enrollment : enrollments.applicants(id(callout, "id"))) {
                lines.add(
                        enrollment.get("position")
                                + ": "
                                + enrollment.get("name")
                                + " "
                                + enrollment.get("surnames")
                                + " - "
                                + enrollment.get("status"));
            }
            lines.add("");
        }
        if (lines.isEmpty()) lines.add("No hay servicios para esta fecha.");
        return new Document(
                "programacion-" + date + ".pdf", "Programación general - " + date, lines);
    }
}
