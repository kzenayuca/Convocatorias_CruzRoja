package pe.cruzroja.convocatorias.operations.infrastructure;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.cruzroja.convocatorias.shared.infrastructure.JdbcSupport;
import pe.cruzroja.convocatorias.operations.application.EnrollmentRepository;

@Repository
public class JdbcEnrollmentRepository extends JdbcSupport implements EnrollmentRepository {
    public JdbcEnrollmentRepository(JdbcTemplate db) { super(db); }
    public Map<String,Object> find(UUID id,boolean lock) { return row("SELECT * FROM enrollments WHERE id=?"+(lock?" FOR UPDATE":""),id); }
    public List<Map<String,Object>> byUser(UUID callout,UUID user,boolean lock) { return rows("SELECT * FROM enrollments WHERE callout_id=? AND volunteer_id=?"+(lock?" FOR UPDATE":""),callout,user); }
    public void lockVolunteer(UUID user) { row("SELECT user_id FROM volunteer_profiles WHERE user_id=? FOR UPDATE",user); }
    public List<Map<String,Object>> candidates(UUID id) { return rows("SELECT u.id,u.name,u.surnames,u.graduated FROM users u JOIN volunteer_profiles p ON p.user_id=u.id WHERE u.status='ACTIVO' AND u.role='VOLUNTARIO' AND NOT EXISTS(SELECT 1 FROM enrollments e WHERE e.callout_id=? AND e.volunteer_id=u.id AND e.status NOT IN ('PENDIENTE','EXPIRADA')) ORDER BY u.name,u.surnames,u.id",id); }
    public List<Map<String,Object>> applicants(UUID id) { return rows("SELECT e.*,u.name,u.surnames,t.name || ' ' || t.surnames AS replaced_name FROM enrollments e JOIN users u ON u.id=e.volunteer_id LEFT JOIN enrollments old ON old.id=e.replacement_for LEFT JOIN users t ON t.id=old.volunteer_id WHERE e.callout_id=? ORDER BY e.created_at,e.id",id); }
    public List<Map<String,Object>> mine(UUID user) { return rows("SELECT e.*,u.name || ' ' || u.surnames AS full_name,c.title,c.start_at,c.end_at,c.kind,c.service_code,c.location,c.service_type,c.opened_at,c.closed_at,(SELECT j.id FROM justifications j WHERE j.enrollment_id=e.id AND j.kind='REEMPLAZO' ORDER BY j.submitted_at DESC,j.id LIMIT 1) AS replacement_request_id,EXISTS(SELECT 1 FROM monthly_reports m WHERE m.month=date_trunc('month',c.start_at)::date AND m.status='APROBADO') AS month_closed FROM enrollments e JOIN callouts c ON c.id=e.callout_id JOIN users u ON u.id=e.volunteer_id WHERE e.volunteer_id=? ORDER BY c.start_at DESC",user); }
    public int pendingReplacements(UUID id) { return db.queryForObject("SELECT count(*) FROM enrollments WHERE replacement_for=? AND status='PENDIENTE_REEMPLAZO'",Integer.class,id); }
    public boolean replacementRequested(UUID callout,UUID user) { return db.queryForObject("SELECT count(*) FROM enrollments e WHERE e.callout_id=? AND e.volunteer_id=? AND (e.cancellation_requested_at IS NOT NULL OR EXISTS(SELECT 1 FROM enrollments p WHERE p.replacement_for=e.id AND p.status='PENDIENTE_REEMPLAZO'))",Integer.class,callout,user)>0; }
    public void save(UUID existing,UUID user,UUID callout,String position,UUID replacement,UUID assignedBy,LocalDateTime at) {
        String status=replacement==null?"CONFIRMADA":"PENDIENTE_REEMPLAZO";
        if(existing==null) db.update("INSERT INTO enrollments(id,volunteer_id,callout_id,status,position,replacement_for,reviewed_at) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),user,callout,status,position,replacement,replacement==null?at:null);
        else db.update("UPDATE enrollments SET status=?,position=?,replacement_for=?,reviewed_at=?,created_at=?,comment=NULL WHERE id=?",status,position,replacement,replacement==null?at:null,at,existing);
        db.update("UPDATE enrollments SET requirements_accepted=true,assigned_by=?,assigned_at=? WHERE callout_id=? AND volunteer_id=?",assignedBy,assignedBy==null?null:at,callout,user);
    }
    public void cancellation(UUID id,String reason,LocalDateTime at) { db.update("UPDATE enrollments SET cancellation_requested_at=?,cancellation_reason=? WHERE id=?",at,reason,id); }
    public void clearCancellation(UUID id) { db.update("UPDATE enrollments SET cancellation_requested_at=NULL,cancellation_reason=NULL WHERE id=?",id); }
    public void cancelReplaced(UUID id) { db.update("UPDATE enrollments SET status='CANCELADA',comment='Cancelada con reemplazo confirmado' WHERE id=?",id); }
    public void review(UUID id,boolean approved,String comment,LocalDateTime at) { db.update("UPDATE enrollments SET status=?,comment=?,reviewed_at=? WHERE id=?",approved?"CONFIRMADA":"RECHAZADA",comment,at,id); }
    public List<UUID> commitments(UUID user) { return db.queryForList("SELECT DISTINCT callout_id FROM enrollments WHERE volunteer_id=? ORDER BY callout_id",UUID.class,user); }
    public List<Map<String,Object>> activeForUser(UUID c,UUID u) { return rows("SELECT * FROM enrollments WHERE callout_id=? AND volunteer_id=? AND status IN ('PENDIENTE','CONFIRMADA','PENDIENTE_REEMPLAZO')",c,u); }
    public void cancelIneligible(UUID id) { db.update("UPDATE enrollments SET status='CANCELADA',comment='Cuenta no activa o habilitación retirada por jefatura' WHERE id=?",id); }
}
