package pe.cruzroja.convocatorias.operations.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import pe.cruzroja.convocatorias.operations.domain.AttendanceReview;

/** Solicitudes de revisión de asistencia. */
public interface AttendanceReviewRepository {
    void createReview(UUID id, UUID attendance, UUID user, String reason, LocalDateTime at);

    List<Map<String, Object>> reviews(UUID attendance);

    Map<String, Object> review(UUID request, boolean lock);

    void resolveReview(AttendanceReview.Decision decision);
}
