package pe.cruzroja.convocatorias.api;

import static pe.cruzroja.convocatorias.api.Store.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.cruzroja.convocatorias.domain.ServiceRules;

/** Vencimientos y ausencias idempotentes; ninguna tarea genera suspensiones. */
@Service
public class ServiceMaintenance {
    private final JdbcTemplate db;
    private final CalloutWorkflow workflow;
    private final MonthlyLedger ledger;
    public ServiceMaintenance(JdbcTemplate db,CalloutWorkflow workflow,MonthlyLedger ledger) { this.db=db; this.workflow=workflow; this.ledger=ledger; }
    @Transactional public void refresh() {
        for(var c:db.queryForList("""
            SELECT id,title,start_at,end_at,deadline,kind,status,created_by,responsible_id,opened_at,closed_at,
                total_volunteers,socorrista_slots,graduate_slots,trainee_slots,radio_slots,pilot_slots FROM callouts c WHERE c.status IN ('ABIERTA','CERRADA')
            OR EXISTS(SELECT 1 FROM enrollments e WHERE e.callout_id=c.id AND e.status IN ('PENDIENTE','PENDIENTE_REEMPLAZO'))
            OR (c.end_at<=? AND c.status IN ('PUBLICADA','CONFIRMADA','FINALIZADA') AND EXISTS(
                SELECT 1 FROM enrollments e WHERE e.callout_id=c.id AND e.status IN ('CONFIRMADA','REEMPLAZADA')
                AND NOT EXISTS(SELECT 1 FROM attendance_records a WHERE a.enrollment_id=e.id)))
            OR (c.end_at<=? AND c.status='FINALIZADA' AND EXISTS(SELECT 1 FROM attendance_records a
                WHERE a.callout_id=c.id AND a.check_in IS NULL AND a.status='PENDIENTE_REVISION'))
            OR (c.end_at<=? AND EXISTS(SELECT 1 FROM attendance_records a WHERE a.callout_id=c.id AND a.status='PENDIENTE_REVISION'))
            ORDER BY c.id FOR UPDATE
            """,now(),now(),now().minusDays(7))) {
            if(ledger.closed(c)) continue;
            if(Set.of("ABIERTA","CERRADA").contains(c.get("status")) && c.get("opened_at")==null) {
                workflow.synchronize(c);
                if(!time(c,"deadline").isAfter(now()) && !workflow.complete(c)) {
                    workflow.cancel(c,"No se completaron los puestos obligatorios antes del cierre de inscripciones.");
                    continue;
                }
            }
            for(var e:db.queryForList("SELECT * FROM enrollments WHERE callout_id=? AND status IN ('PENDIENTE','PENDIENTE_REEMPLAZO')",c.get("id"))) {
                if(!time(c,"start_at").isAfter(now())) {
                    db.update("UPDATE enrollments SET status='RECHAZADA',comment='El servicio comenzó sin resolver el reemplazo o la solicitud anterior',reviewed_at=? WHERE id=?",now(),e.get("id"));
                    Store.notify(db,id(e,"volunteer_id"),"INSCRIPCION",c.get("title")+": solicitud rechazada al inicio.","/services");
                }
            }
            if(time(c,"end_at").isAfter(now()) || !text(c,"kind").equals("SERVICIO") || text(c,"status").equals("CANCELADA")) continue;
            // El encargado debe haber abierto el servicio. Los históricos finalizados también son válidos.
            if(c.get("opened_at")==null && !text(c,"status").equals("FINALIZADA")) continue;
            for(var e:db.queryForList("SELECT * FROM enrollments WHERE callout_id=? AND status IN ('CONFIRMADA','REEMPLAZADA')",c.get("id"))) {
                db.update("""
                    INSERT INTO attendance_records(id,enrollment_id,volunteer_id,callout_id,responsible_id,status)
                    VALUES (?,?,?,?,?,'AUSENTE') ON CONFLICT(enrollment_id) DO NOTHING
                    """,UUID.randomUUID(),e.get("id"),e.get("volunteer_id"),c.get("id"),
                        c.get("responsible_id")==null?e.get("volunteer_id"):c.get("responsible_id"));
            }
            db.update("UPDATE attendance_records SET status='AUSENTE' WHERE callout_id=? AND check_in IS NULL AND status='PENDIENTE_REVISION'",c.get("id"));
            if(!time(c,"end_at").plusDays(7).isAfter(now())) {
                for(var a:db.queryForList("SELECT * FROM attendance_records WHERE callout_id=? AND status='PENDIENTE_REVISION' FOR UPDATE",c.get("id"))) {
                    boolean complete=time(a,"check_in")!=null && time(a,"check_out")!=null;
                    double hours=complete?ServiceRules.serviceCredit(time(c,"start_at"),time(c,"end_at"),time(a,"check_in"),time(a,"check_out")):0;
                    db.update("UPDATE attendance_records SET status=?,approved_hours=?,signed=false,observations=observations || ? WHERE id=?",complete?"CONFIRMADA":"INCOMPLETA",Math.round(hours*10000.0)/10000.0,"\nCierre automático a los 7 días con las marcas del participante; no se añadieron marcas.",a.get("id"));
                    Store.notify(db,id(a,"volunteer_id"),"ASISTENCIA","Asistencia cerrada a los 7 días: "+hours+" h. "+c.get("title"),"/services");
                }
            }
        }
        var previous=java.time.YearMonth.from(now()).minusMonths(1);
        if(db.update("INSERT INTO monthly_reports(month) VALUES(?) ON CONFLICT DO NOTHING",previous.atDay(1))>0) {
            for(var u:db.queryForList("SELECT id FROM users WHERE role='JEFATURA' AND status='ACTIVO'"))
                Store.notify(db,id(u,"id"),"REPORTE","Reporte de "+previous+" disponible para aprobación. Publicar entre el 1 y el 3.","/reports");
        }
    }
}
