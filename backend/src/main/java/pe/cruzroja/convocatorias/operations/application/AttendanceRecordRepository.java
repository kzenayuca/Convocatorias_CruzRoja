package pe.cruzroja.convocatorias.operations.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Registro de asistencia: consulta, marcación, confirmación y corrección. */
public interface AttendanceRecordRepository {
    List<Map<String, Object>> records(UUID user, UUID callout);

    Map<String, Object> find(UUID id, boolean lock);

    Map<String, Object> byEnrollment(UUID id);

    void prepareMark(UUID enrollment, UUID user, UUID callout, UUID responsible);

    void mark(UUID id, String action, LocalDateTime at);

    void confirm(
            UUID id,
            LocalDateTime in,
            LocalDateTime out,
            double hours,
            UUID actor,
            String observations);

    void correction(UUID enrollment, UUID user, UUID callout, UUID responsible, String reason);
}
