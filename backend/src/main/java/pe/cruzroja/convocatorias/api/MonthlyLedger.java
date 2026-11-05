package pe.cruzroja.convocatorias.api;

import static pe.cruzroja.convocatorias.api.Store.*;
import java.time.*;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Se consulta después de bloquear la convocatoria. Aprobar bloquea primero todas las del mes. */
@Service
public class MonthlyLedger {
    private final JdbcTemplate db;
    public MonthlyLedger(JdbcTemplate db) { this.db=db; }
    public boolean closed(Map<String,Object> c) { return closed(YearMonth.from(time(c,"start_at"))); }
    public boolean closed(YearMonth month) {
        return db.queryForObject("SELECT count(*) FROM monthly_reports WHERE month=? AND status='APROBADO'",Integer.class,month.atDay(1))>0;
    }
    public void ensureOpen(Map<String,Object> c) { valid(!closed(c),"El mes ya fue aprobado y no admite cambios de asistencia ni justificaciones."); }
}
