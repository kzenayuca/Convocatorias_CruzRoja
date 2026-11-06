package pe.cruzroja.convocatorias.operations.application;

import java.time.*;
import java.util.Map;
import static pe.cruzroja.convocatorias.shared.application.Projection.*;
import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

/** Se consulta después de bloquear la convocatoria. Aprobar bloquea primero todas las del mes. */
public class MonthlyLedger {
    private final AttendanceRepository repository;
    public MonthlyLedger(AttendanceRepository repository) { this.repository=repository; }
    public boolean closed(Map<String,Object> c) { return closed(YearMonth.from(time(c,"start_at"))); }
    public boolean closed(YearMonth month) { return repository.closed(month); }
    public void ensureOpen(Map<String,Object> c) { valid(!closed(c),"El mes ya fue aprobado y no admite cambios de asistencia. Las justificaciones se registran como ajustes auditados."); }
}
