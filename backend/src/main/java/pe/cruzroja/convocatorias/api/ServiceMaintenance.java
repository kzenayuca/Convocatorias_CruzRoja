package pe.cruzroja.convocatorias.api;

import static pe.cruzroja.convocatorias.api.Store.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.cruzroja.convocatorias.domain.ServiceRules;

/** Transiciones de tiempo idempotentes; también se ejecutan antes de consultar datos. */
@Service
public class ServiceMaintenance {
    private final JdbcTemplate db;
    public ServiceMaintenance(JdbcTemplate db) { this.db=db; }
    @Transactional public void refresh() {
        for(var c:db.queryForList("""
            SELECT c.id,c.title,c.start_at,c.end_at,c.kind,c.status,c.responsible_id FROM callouts c
            WHERE EXISTS(SELECT 1 FROM enrollments e WHERE e.callout_id=c.id AND e.status='PENDIENTE')
               OR (c.end_at<=? AND c.kind='SERVICIO' AND c.status IN ('PUBLICADA','CONFIRMADA','FINALIZADA')
                   AND EXISTS(SELECT 1 FROM enrollments e WHERE e.callout_id=c.id AND e.status IN ('CONFIRMADA','REEMPLAZADA')
                       AND NOT EXISTS(SELECT 1 FROM attendance_records a WHERE a.enrollment_id=e.id)))
               OR (c.end_at<=? AND EXISTS(SELECT 1 FROM attendance_records a WHERE a.callout_id=c.id
                   AND a.status='PENDIENTE_REVISION' AND a.check_in IS NULL))
            ORDER BY c.id FOR UPDATE
            """,now(),now())) {
            var current=now();
            for(var e:db.queryForList("SELECT * FROM enrollments WHERE callout_id=? AND status='PENDIENTE'",c.get("id"))) {
                boolean started=!time(c,"start_at").isAfter(current);
                if(started || !ServiceRules.reviewDeadline(time(e,"created_at")).isAfter(current)) {
                    var status=started?"RECHAZADA":"EXPIRADA";
                    db.update("UPDATE enrollments SET status=?,comment=?,reviewed_at=? WHERE id=?",status,started?"El servicio comenzó sin aprobación":"Terminó el plazo de cinco días hábiles",current,e.get("id"));
                    Store.notify(db,id(e,"volunteer_id"),"INSCRIPCION",c.get("title")+": "+status,"/services");
                }
            }
            if(time(c,"end_at").isAfter(current) || !text(c,"kind").equals("SERVICIO") || !java.util.Set.of("PUBLICADA","CONFIRMADA","FINALIZADA").contains(c.get("status"))) continue;
            for(var e:db.queryForList("SELECT * FROM enrollments WHERE callout_id=? AND status IN ('CONFIRMADA','REEMPLAZADA')",c.get("id"))) {
                db.update("""
                    INSERT INTO attendance_records(id,enrollment_id,volunteer_id,callout_id,responsible_id,status)
                    VALUES (?,?,?,?,?,'AUSENTE') ON CONFLICT(enrollment_id) DO NOTHING
                    """,UUID.randomUUID(),e.get("id"),e.get("volunteer_id"),c.get("id"),c.get("responsible_id")==null?e.get("volunteer_id"):c.get("responsible_id"));
            }
            db.update("UPDATE attendance_records SET status='AUSENTE' WHERE callout_id=? AND check_in IS NULL AND status='PENDIENTE_REVISION'",c.get("id"));
        }
    }
}
