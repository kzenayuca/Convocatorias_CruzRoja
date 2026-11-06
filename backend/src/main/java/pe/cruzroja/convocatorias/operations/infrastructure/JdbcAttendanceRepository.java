package pe.cruzroja.convocatorias.operations.infrastructure;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.cruzroja.convocatorias.shared.infrastructure.JdbcSupport;
import pe.cruzroja.convocatorias.operations.application.AttendanceRepository;
import pe.cruzroja.convocatorias.operations.application.EvidenceFile;
import pe.cruzroja.convocatorias.operations.domain.Justification;

@Repository
public class JdbcAttendanceRepository extends JdbcSupport implements AttendanceRepository {
    public JdbcAttendanceRepository(JdbcTemplate db) { super(db); }
    public List<Map<String,Object>> records(UUID user,UUID callout) {
        String sql="""
            SELECT a.*,c.title,c.start_at,c.end_at,c.status AS callout_status,u.name,u.role AS volunteer_role,
            j.status AS justification_status,j.kind AS justification_kind,
            (SELECT count(*) FROM enrollments r WHERE r.replacement_for=a.enrollment_id AND r.status='CONFIRMADA' AND r.reviewed_at<=c.start_at) AS replacements
            FROM attendance_records a JOIN callouts c ON c.id=a.callout_id JOIN users u ON u.id=a.volunteer_id LEFT JOIN justifications j ON j.absence_id=a.id
            """;
        return rows(sql+(user!=null?" WHERE a.volunteer_id=?":" WHERE a.callout_id=?")+" ORDER BY c.start_at DESC",user!=null?user:callout);
    }
    public Map<String,Object> find(UUID id,boolean lock) { return row("SELECT * FROM attendance_records WHERE id=?"+(lock?" FOR UPDATE":""),id); }
    public Map<String,Object> byEnrollment(UUID id) { return row("SELECT * FROM attendance_records WHERE enrollment_id=? FOR UPDATE",id); }
    public void prepareMark(UUID e,UUID u,UUID c,UUID r) { db.update("INSERT INTO attendance_records(id,enrollment_id,volunteer_id,callout_id,responsible_id,status) VALUES (?,?,?,?,?,'PENDIENTE_REVISION') ON CONFLICT(enrollment_id) DO NOTHING",UUID.randomUUID(),e,u,c,r); }
    public void mark(UUID id,String action,LocalDateTime at) { if(action.equals("ENTRADA")) db.update("UPDATE attendance_records SET check_in=?,status='PENDIENTE_REVISION',signed=false,approved_hours=NULL WHERE id=?",at,id); else db.update("UPDATE attendance_records SET check_out=?,status='PENDIENTE_REVISION',signed=false,approved_hours=NULL WHERE id=?",at,id); }
    public void confirm(UUID id,LocalDateTime in,LocalDateTime out,double hours,UUID actor,String observations) { db.update("UPDATE attendance_records SET check_in=?,check_out=?,approved_hours=?,status='CONFIRMADA',signed=true,policy_version=2,responsible_id=?,observations=? WHERE id=?",in,out,hours,actor,observations,id); }
    public void correction(UUID e,UUID u,UUID c,UUID responsible,String reason) { db.update("INSERT INTO attendance_records(id,enrollment_id,volunteer_id,callout_id,responsible_id,status,observations) VALUES (?,?,?,?,?,'PENDIENTE_REVISION',?) ON CONFLICT(enrollment_id) DO UPDATE SET observations=EXCLUDED.observations",UUID.randomUUID(),e,u,c,responsible,"Solicitud de revisión: "+reason); }
    public void createReview(UUID id,UUID attendance,UUID user,String reason,LocalDateTime at) { db.update("INSERT INTO attendance_review_requests(id,attendance_id,volunteer_id,reason,submitted_at) VALUES (?,?,?,?,?)",id,attendance,user,reason,at); }
    private static final String REVIEWS="SELECT r.*,a.callout_id,c.title,c.start_at,c.end_at,u.name || ' ' || u.surnames AS name,reviewer.name AS resolved_name FROM attendance_review_requests r JOIN attendance_records a ON a.id=r.attendance_id JOIN callouts c ON c.id=a.callout_id JOIN users u ON u.id=r.volunteer_id LEFT JOIN users reviewer ON reviewer.id=r.resolved_by";
    public List<Map<String,Object>> reviews(UUID attendance) { return rows(REVIEWS+" WHERE r.attendance_id=? ORDER BY r.submitted_at DESC,r.id",attendance); }
    public Map<String,Object> review(UUID id,boolean lock) { return row(REVIEWS+" WHERE r.id=?"+(lock?" FOR UPDATE OF r":""),id); }
    public void resolveReview(pe.cruzroja.convocatorias.operations.domain.AttendanceReview.Decision d) { db.update("UPDATE attendance_review_requests SET status=?,resolution_comment=?,resolved_by=?,resolved_at=? WHERE id=?",d.status(),d.comment(),d.actor(),d.at(),d.request()); }
    public String userName(UUID user) { return db.queryForObject("SELECT name || ' ' || surnames FROM users WHERE id=?",String.class,user); }
    public boolean trainingVolunteer(UUID user) { return db.queryForObject("SELECT role='VOLUNTARIO' AND NOT graduated AND status='ACTIVO' FROM users WHERE id=?",Boolean.class,user); }
    public void justify(UUID id,UUID attendance,UUID user,String reason,LocalDateTime at,boolean late,String kind) { db.update("INSERT INTO justifications(id,absence_id,volunteer_id,reason,status,submitted_at,late,kind) VALUES (?,?,?,?,'PENDIENTE',?,?,?)",id,attendance,user,reason,at,late,kind); }
    public List<Map<String,Object>> justifications(UUID user) {
        return user==null?rows(JUSTIFICATIONS+" ORDER BY j.submitted_at DESC,j.id"):rows(JUSTIFICATIONS+" WHERE j.volunteer_id=? ORDER BY j.submitted_at DESC,j.id",user);
    }
    private static final String JUSTIFICATIONS="""
        SELECT j.id,j.absence_id,j.volunteer_id,j.reason,j.status,j.submitted_at,j.resolution_comment,j.late,j.kind,
        j.resolved_by,j.resolved_at,j.proposed_replacement_name,j.assigned_replacement_id,
        (j.evidence IS NOT NULL) AS has_evidence,u.name,u.surnames,c.id AS callout_id,c.title,c.start_at,c.end_at,
        COALESCE(j.enrollment_id,a.enrollment_id) AS enrollment_id,reviewer.name AS resolved_name,
        replacement.name || ' ' || replacement.surnames AS assigned_replacement_name,
        (SELECT count(*) FROM enrollments r WHERE r.replacement_for=a.enrollment_id AND r.status='CONFIRMADA' AND r.reviewed_at<=c.start_at) AS replacements
        FROM justifications j LEFT JOIN attendance_records a ON a.id=j.absence_id
        LEFT JOIN enrollments e ON e.id=j.enrollment_id JOIN callouts c ON c.id=COALESCE(a.callout_id,e.callout_id)
        JOIN users u ON u.id=j.volunteer_id LEFT JOIN users reviewer ON reviewer.id=j.resolved_by
        LEFT JOIN users replacement ON replacement.id=j.assigned_replacement_id
        """;
    public List<Map<String,Object>> justificationHistory(UUID id) { return rows("SELECT r.*,u.name AS resolved_name FROM justification_resolutions r JOIN users u ON u.id=r.resolved_by WHERE r.justification_id=? ORDER BY r.resolved_at,r.id",id); }
    public Map<String,Object> justification(UUID id,boolean lock) { return row(JUSTIFICATIONS+" WHERE j.id=?"+(lock?" FOR UPDATE OF j":""),id); }
    public void requestReplacement(UUID id,UUID enrollment,UUID user,String reason,String proposedName,LocalDateTime at) { db.update("INSERT INTO justifications(id,enrollment_id,volunteer_id,reason,status,submitted_at,kind,proposed_replacement_name) VALUES (?,?,?,?,'PENDIENTE',?,'REEMPLAZO',?)",id,enrollment,user,reason,at,proposedName); }
    public void assignedReplacement(UUID id,UUID user) { db.update("UPDATE justifications SET assigned_replacement_id=? WHERE id=?",user,id); }
    public void evidence(UUID id,EvidenceFile file) { if(file!=null) db.update("UPDATE justifications SET evidence=?,evidence_type=? WHERE id=?",file.bytes(),file.type(),id); }
    public EvidenceFile evidence(UUID id) { var r=row("SELECT evidence,evidence_type FROM justifications WHERE id=?",id); if(r.get("evidence")==null) throw new pe.cruzroja.convocatorias.shared.application.NotFound(); return new EvidenceFile((byte[])r.get("evidence"),(String)r.get("evidence_type")); }
    public List<UUID> chiefs() { return db.queryForList("SELECT id FROM users WHERE role='JEFATURA' AND status='ACTIVO'",UUID.class); }
    public Map<String,Object> justificationAttendance(UUID id) { return row("SELECT a.*,c.start_at,c.end_at,c.status AS callout_status,u.role AS volunteer_role,j.status AS justification_status,j.kind AS justification_kind FROM attendance_records a JOIN callouts c ON c.id=a.callout_id JOIN users u ON u.id=a.volunteer_id JOIN justifications j ON j.absence_id=a.id WHERE j.id=? FOR UPDATE OF a",id); }
    public void decision(Justification.Decision d) { db.update("INSERT INTO justification_resolutions(id,justification_id,previous_status,status,resolved_by,resolved_at,comment,deduction_before,deduction_after) VALUES (?,?,?,?,?,?,?,?,?)",d.id(),d.justification(),d.previousStatus(),d.status(),d.actor(),d.at(),d.comment(),d.before(),d.after()); db.update("UPDATE justifications SET status=?,resolution_comment=?,resolved_by=?,resolved_at=? WHERE id=?",d.status(),d.comment(),d.actor(),d.at(),d.justification()); }
    public void adjustment(YearMonth month,UUID user,Justification.Decision d) { db.update("INSERT INTO monthly_report_adjustments(id,month,user_id,resolution_id,deduction_delta,created_at) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),month.atDay(1),user,d.id(),d.deductionDelta(),d.at()); }
    public String userRole(UUID id) { return db.queryForObject("SELECT role FROM users WHERE id=?",String.class,id); }
    public boolean closed(YearMonth month) { return db.queryForObject("SELECT count(*) FROM monthly_reports WHERE month=? AND status='APROBADO'",Integer.class,month.atDay(1))>0; }
    public List<Map<String,Object>> snapshot(UUID user,YearMonth month) { return rows("SELECT earned,deductions,hours,pending FROM monthly_report_rows WHERE month=? AND user_id=?",month.atDay(1),user); }
    public double deductionDelta(UUID user,YearMonth month) { return db.queryForObject("SELECT COALESCE(sum(deduction_delta),0) FROM monthly_report_adjustments WHERE month=? AND user_id=?",Double.class,month.atDay(1),user); }
    public List<Map<String,Object>> monthlyRecords(UUID user,YearMonth month,UUID responsible) {
        String sql="SELECT a.*,c.start_at,c.end_at,c.status AS callout_status,j.status AS justification_status,j.kind AS justification_kind FROM attendance_records a JOIN callouts c ON c.id=a.callout_id LEFT JOIN justifications j ON j.absence_id=a.id WHERE a.volunteer_id=? AND c.start_at>=? AND c.start_at<? AND c.status<>'CANCELADA'";
        return responsible==null?rows(sql,user,month.atDay(1).atStartOfDay(),month.plusMonths(1).atDay(1).atStartOfDay()):rows(sql+" AND c.responsible_id=?",user,month.atDay(1).atStartOfDay(),month.plusMonths(1).atDay(1).atStartOfDay(),responsible);
    }
    public List<Map<String,Object>> users() { return rows("SELECT id,name || ' ' || surnames AS name,role FROM users ORDER BY name"); }
    public List<Map<String,Object>> reportUsers(YearMonth month) { return rows("SELECT user_id AS id,name FROM monthly_report_rows WHERE month=? ORDER BY name",month.atDay(1)); }
    public List<LocalDate> months(UUID user) { return db.query("SELECT DISTINCT date_trunc('month',c.start_at)::date AS month FROM attendance_records a JOIN callouts c ON c.id=a.callout_id WHERE a.volunteer_id=? UNION SELECT month FROM monthly_report_rows WHERE user_id=? ORDER BY month",(r,n)->r.getDate(1).toLocalDate(),user,user); }
    public List<Map<String,Object>> reportStatus(YearMonth month) { return rows("SELECT status,approved_at FROM monthly_reports WHERE month=?",month.atDay(1)); }
    public void lockMonthlyCallouts(YearMonth month) { rows("SELECT id FROM callouts WHERE start_at>=? AND start_at<? ORDER BY id FOR UPDATE",month.atDay(1).atStartOfDay(),month.plusMonths(1).atDay(1).atStartOfDay()); }
    public boolean unfinished(YearMonth month,LocalDateTime now) { return db.queryForObject("SELECT count(*) FROM callouts WHERE start_at>=? AND start_at<? AND end_at>? AND status<>'CANCELADA'",Integer.class,month.atDay(1).atStartOfDay(),month.plusMonths(1).atDay(1).atStartOfDay(),now)>0; }
    public void lockReport(YearMonth month) { db.update("INSERT INTO monthly_reports(month) VALUES(?) ON CONFLICT DO NOTHING",month.atDay(1)); row("SELECT month FROM monthly_reports WHERE month=? FOR UPDATE",month.atDay(1)); }
    public void saveSnapshot(YearMonth month,UUID user,String name,Map<String,Object> s) { db.update("INSERT INTO monthly_report_rows(month,user_id,name,earned,deductions,hours,pending) VALUES (?,?,?,?,?,?,?)",month.atDay(1),user,name,s.get("earned"),s.get("deductions"),s.get("hours"),s.get("pending")); }
    public void approve(YearMonth month,UUID actor,LocalDateTime at) { db.update("UPDATE monthly_reports SET status='APROBADO',approved_by=?,approved_at=? WHERE month=?",actor,at,month.atDay(1)); }
}
