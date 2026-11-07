package pe.cruzroja.convocatorias.operations.application;

import java.time.*;
import java.util.*;
import pe.cruzroja.convocatorias.operations.domain.Justification;

public interface AttendanceRepository {
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

    void createReview(UUID id, UUID attendance, UUID user, String reason, LocalDateTime at);

    List<Map<String, Object>> reviews(UUID attendance);

    Map<String, Object> review(UUID request, boolean lock);

    void resolveReview(
            pe.cruzroja.convocatorias.operations.domain.AttendanceReview.Decision decision);

    String userName(UUID user);

    boolean trainingVolunteer(UUID user);

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

    List<UUID> chiefs();

    void decision(Justification.Decision decision);

    void adjustment(YearMonth month, UUID user, Justification.Decision decision);

    String userRole(UUID id);

    boolean closed(YearMonth month);

    List<Map<String, Object>> snapshot(UUID user, YearMonth month);

    double deductionDelta(UUID user, YearMonth month);

    List<Map<String, Object>> monthlyRecords(UUID user, YearMonth month, UUID responsible);

    List<Map<String, Object>> users();

    List<Map<String, Object>> reportUsers(YearMonth month);

    List<LocalDate> months(UUID user);

    List<Map<String, Object>> reportStatus(YearMonth month);

    void lockMonthlyCallouts(YearMonth month);

    boolean unfinished(YearMonth month, LocalDateTime now);

    void lockReport(YearMonth month);

    void saveSnapshot(YearMonth month, UUID user, String name, Map<String, Object> summary);

    void approve(YearMonth month, UUID actor, LocalDateTime at);
}
