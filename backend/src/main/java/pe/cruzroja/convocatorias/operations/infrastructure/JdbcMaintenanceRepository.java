package pe.cruzroja.convocatorias.operations.infrastructure;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.cruzroja.convocatorias.shared.infrastructure.JdbcSupport;
import pe.cruzroja.convocatorias.operations.application.MaintenanceRepository;

@Repository
public class JdbcMaintenanceRepository extends JdbcSupport implements MaintenanceRepository {
    public JdbcMaintenanceRepository(JdbcTemplate db) { super(db); }
    public List<Map<String,Object>> lockDue(LocalDateTime at) { return rows("""
        SELECT id,title,start_at,end_at,deadline,kind,status,created_by,responsible_id,opened_at,closed_at,
        total_volunteers,socorrista_slots,graduate_slots,trainee_slots,radio_slots,pilot_slots FROM callouts c WHERE c.status IN ('ABIERTA','CERRADA')
        OR EXISTS(SELECT 1 FROM enrollments e WHERE e.callout_id=c.id AND e.status IN ('PENDIENTE','PENDIENTE_REEMPLAZO'))
        OR (c.end_at<=? AND c.status IN ('PUBLICADA','CONFIRMADA','FINALIZADA') AND EXISTS(SELECT 1 FROM enrollments e WHERE e.callout_id=c.id AND e.status IN ('CONFIRMADA','REEMPLAZADA') AND NOT EXISTS(SELECT 1 FROM attendance_records a WHERE a.enrollment_id=e.id)))
        OR (c.end_at<=? AND c.status='FINALIZADA' AND EXISTS(SELECT 1 FROM attendance_records a WHERE a.callout_id=c.id AND a.check_in IS NULL AND a.status='PENDIENTE_REVISION'))
        OR (c.end_at<=? AND EXISTS(SELECT 1 FROM attendance_records a WHERE a.callout_id=c.id AND a.status='PENDIENTE_REVISION')) ORDER BY c.id FOR UPDATE
        """,at,at,at.minusDays(7)); }
    public List<Map<String,Object>> pendingEnrollments(UUID id) { return rows("SELECT * FROM enrollments WHERE callout_id=? AND status IN ('PENDIENTE','PENDIENTE_REEMPLAZO')",id); }
    public void expire(UUID id,LocalDateTime at) { db.update("UPDATE enrollments SET status='RECHAZADA',comment='El servicio comenzó sin resolver el reemplazo o la solicitud anterior',reviewed_at=? WHERE id=?",at,id); }
    public List<Map<String,Object>> accepted(UUID id) { return rows("SELECT * FROM enrollments WHERE callout_id=? AND status IN ('CONFIRMADA','REEMPLAZADA')",id); }
    public void ensureAbsence(UUID e,UUID u,UUID c,UUID r) { db.update("INSERT INTO attendance_records(id,enrollment_id,volunteer_id,callout_id,responsible_id,status) VALUES (?,?,?,?,?,'AUSENTE') ON CONFLICT(enrollment_id) DO NOTHING",UUID.randomUUID(),e,u,c,r); }
    public void markUnattended(UUID id) { db.update("UPDATE attendance_records SET status='AUSENTE' WHERE callout_id=? AND check_in IS NULL AND status='PENDIENTE_REVISION'",id); }
    public List<Map<String,Object>> lockPendingAttendance(UUID id) { return rows("SELECT * FROM attendance_records WHERE callout_id=? AND status='PENDIENTE_REVISION' FOR UPDATE",id); }
    public void autoConfirm(UUID id,boolean complete,double hours) { db.update("UPDATE attendance_records SET status=?,approved_hours=?,signed=false,observations=observations || ? WHERE id=?",complete?"CONFIRMADA":"INCOMPLETA",Math.round(hours*10000.0)/10000.0,"\nCierre automático a los 7 días con las marcas del participante; no se añadieron marcas.",id); }
    public boolean prepareReport(YearMonth month) { return db.update("INSERT INTO monthly_reports(month) VALUES(?) ON CONFLICT DO NOTHING",month.atDay(1))>0; }
    public List<UUID> chiefs() { return db.queryForList("SELECT id FROM users WHERE role='JEFATURA' AND status='ACTIVO'",UUID.class); }
}
