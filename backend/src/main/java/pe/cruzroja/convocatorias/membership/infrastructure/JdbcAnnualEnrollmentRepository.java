package pe.cruzroja.convocatorias.membership.infrastructure;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.cruzroja.convocatorias.membership.application.AnnualEnrollmentRepository;
import pe.cruzroja.convocatorias.shared.infrastructure.JdbcSupport;

@Repository
public class JdbcAnnualEnrollmentRepository extends JdbcSupport implements AnnualEnrollmentRepository {
    public JdbcAnnualEnrollmentRepository(JdbcTemplate db) { super(db); }
    public List<Map<String,Object>> list(UUID user) { return rows("SELECT year,registered_at,registered_by,source FROM annual_enrollments WHERE user_id=? ORDER BY year DESC",user); }
    public boolean register(UUID user,int year,UUID actor,LocalDateTime at) { return db.update("INSERT INTO annual_enrollments(user_id,year,registered_by,registered_at,source) VALUES (?,?,?,?,'INSCRIPCION') ON CONFLICT DO NOTHING",user,year,actor,at)>0; }
    public void preserveCommitments(UUID user,UUID actor,LocalDateTime at) { db.update("INSERT INTO annual_enrollments(user_id,year,registered_by,registered_at,source) SELECT DISTINCT e.volunteer_id,EXTRACT(YEAR FROM c.start_at)::int,?,?,'GRADUACION' FROM enrollments e JOIN callouts c ON c.id=e.callout_id WHERE e.volunteer_id=? AND e.status='CONFIRMADA' AND c.start_at>=? ON CONFLICT DO NOTHING",actor,at,user,at); }
    public void graduation(UUID user,UUID actor,LocalDateTime at,GraduationEligibilityData data,String reason) { db.update("INSERT INTO graduation_history(id,user_id,approved_by,approved_at,net_hours,required_hours,reason) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),user,actor,at,data.balance(),data.target(),reason); }
}
