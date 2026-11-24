package pe.cruzroja.convocatorias.operations.application;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Reporte mensual: instantáneas, ajustes, bloqueo y aprobación. */
public interface MonthlyReportRepository extends MonthlyReportClosureRepository {
    List<Map<String, Object>> snapshot(UUID user, YearMonth month);

    double deductionDelta(UUID user, YearMonth month);

    List<Map<String, Object>> monthlyRecords(UUID user, YearMonth month, UUID responsible);

    List<Map<String, Object>> reportUsers(YearMonth month);

    List<LocalDate> months(UUID user);

    List<Map<String, Object>> reportStatus(YearMonth month);

    void lockMonthlyCallouts(YearMonth month);

    boolean unfinished(YearMonth month, LocalDateTime now);

    void lockReport(YearMonth month);

    void saveSnapshot(YearMonth month, UUID user, String name, Map<String, Object> summary);

    void approve(YearMonth month, UUID actor, LocalDateTime at);
}
