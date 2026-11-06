package pe.cruzroja.convocatorias.operations.application;

import java.time.YearMonth;
import java.util.*;
import pe.cruzroja.convocatorias.operations.domain.ServiceRules;
import pe.cruzroja.convocatorias.shared.application.*;
import static pe.cruzroja.convocatorias.shared.application.Projection.*;

public class ServiceMaintenance extends UseCaseSupport {
    private final MaintenanceRepository repository;
    private final CalloutWorkflow workflow;
    private final MonthlyLedger ledger;
    public ServiceMaintenance(MaintenanceRepository repository,CalloutWorkflow workflow,MonthlyLedger ledger,ExecutionContext context,NotificationPublisher notifications) { super(context,notifications); this.repository=repository; this.workflow=workflow; this.ledger=ledger; }
    public void refresh() {
        for(var c:repository.lockDue(now())) {
            if(ledger.closed(c)) continue;
            if(Set.of("ABIERTA","CERRADA").contains(c.get("status")) && c.get("opened_at")==null) { workflow.synchronize(c); if(!time(c,"deadline").isAfter(now()) && !workflow.complete(c)) { workflow.cancel(c,"No se completaron los puestos obligatorios antes del cierre de inscripciones."); continue; } }
            UUID callout=id(c,"id");
            for(var e:repository.pendingEnrollments(callout)) if(!time(c,"start_at").isAfter(now())) { repository.expire(id(e,"id"),now()); notifyUser(id(e,"volunteer_id"),"INSCRIPCION",c.get("title")+": solicitud rechazada al inicio.","/services?enrollment="+e.get("id")); }
            if(time(c,"end_at").isAfter(now()) || !text(c,"kind").equals("SERVICIO") || text(c,"status").equals("CANCELADA") || c.get("opened_at")==null && !text(c,"status").equals("FINALIZADA")) continue;
            for(var e:repository.accepted(callout)) repository.ensureAbsence(id(e,"id"),id(e,"volunteer_id"),callout,c.get("responsible_id")==null?id(e,"volunteer_id"):id(c,"responsible_id"));
            repository.markUnattended(callout);
            if(!time(c,"end_at").plusDays(7).isAfter(now())) for(var a:repository.lockPendingAttendance(callout)) {
                boolean complete=time(a,"check_in")!=null && time(a,"check_out")!=null; double hours=complete?ServiceRules.serviceCredit(time(c,"start_at"),time(c,"end_at"),time(a,"check_in"),time(a,"check_out")):0;
                repository.autoConfirm(id(a,"id"),complete,hours); notifyUser(id(a,"volunteer_id"),"ASISTENCIA","Asistencia cerrada a los 7 días: "+hours+" h. "+c.get("title"),"/services?attendance="+a.get("id"));
            }
        }
        var month=YearMonth.from(now()).minusMonths(1);
        if(repository.prepareReport(month)) for(var user:repository.chiefs()) notifyUser(user,"REPORTE","Reporte de "+month+" disponible para aprobación. Publicar entre el 1 y el 3.","/reports?month="+month);
    }
}
