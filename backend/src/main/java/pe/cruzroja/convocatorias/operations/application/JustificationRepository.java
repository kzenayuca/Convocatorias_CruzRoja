package pe.cruzroja.convocatorias.operations.application;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import pe.cruzroja.convocatorias.operations.domain.Justification;

/** Justificaciones, solicitudes de reemplazo, evidencias y sus resoluciones. */
public interface JustificationRepository {
    void justify(
            UUID id,
            UUID attendance,
            UUID user,
            String reason,
            LocalDateTime at,
            boolean late,
            String kind);

    List<Map<String, Object>> justifications(UUID user);

    List<Map<String, Object>> justificationHistory(UUID id);

    Map<String, Object> justification(UUID id, boolean lock);

    Map<String, Object> justificationAttendance(UUID id);

    void requestReplacement(
            UUID id,
            UUID enrollment,
            UUID user,
            String reason,
            String proposedName,
            LocalDateTime at);

    void assignedReplacement(UUID id, UUID user);

    void evidence(UUID id, EvidenceFile file);

    EvidenceFile evidence(UUID id);

    void decision(Justification.Decision decision);

    void adjustment(YearMonth month, UUID user, Justification.Decision decision);
}
