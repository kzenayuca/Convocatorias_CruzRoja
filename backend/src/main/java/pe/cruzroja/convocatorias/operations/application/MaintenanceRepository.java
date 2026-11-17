package pe.cruzroja.convocatorias.operations.application;

import java.time.*;
import java.util.*;

public interface MaintenanceRepository {
    List<Map<String, Object>> lockDue(LocalDateTime at);

    List<Map<String, Object>> pendingEnrollments(UUID callout);

    void expire(UUID enrollment, LocalDateTime at);

    List<Map<String, Object>> accepted(UUID callout);

    void ensureAbsence(UUID enrollment, UUID user, UUID callout, UUID responsible);

    void markUnattended(UUID callout);

    List<Map<String, Object>> lockPendingAttendance(UUID callout);

    void autoConfirm(UUID attendance, boolean complete, double hours);

    boolean prepareReport(YearMonth month);

    List<UUID> chiefs();
}
