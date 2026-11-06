package pe.cruzroja.convocatorias.operations.application;

import java.time.*;
import java.util.*;
import pe.cruzroja.convocatorias.operations.domain.*;
import pe.cruzroja.convocatorias.shared.application.*;
import static pe.cruzroja.convocatorias.shared.application.Projection.*;
import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

public class AttendanceUseCases extends UseCaseSupport {
    private final AttendanceRepository repository;
    private final EnrollmentRepository enrollments;
    private final CalloutRepository callouts;
    private final CalloutWorkflow services;
    private final ServiceMaintenance maintenance;
    private final MonthlyLedger ledger;
    private final GraduationService graduation;
    public AttendanceUseCases(AttendanceRepository repository,EnrollmentRepository enrollments,CalloutRepository callouts,CalloutWorkflow services,ServiceMaintenance maintenance,MonthlyLedger ledger,GraduationSettings settings,ExecutionContext context,NotificationPublisher notifications) {
        super(context,notifications); this.repository=repository; this.enrollments=enrollments; this.callouts=callouts; this.services=services; this.maintenance=maintenance; this.ledger=ledger; this.graduation=new GraduationService(settings);
    }
    public record Mark(String action) { public Mark { required(action,30,"Acción"); } }
    public record Confirmation(LocalDateTime checkIn,LocalDateTime checkOut,Double hours,String observations) { public Confirmation { valid(checkIn!=null && checkOut!=null,"Indica entrada y salida."); valid(hours==null || hours>=0,"Horas no válidas."); required(observations,3000,"Observaciones"); } }
    public record Reason(String reason) { public Reason { required(reason,3000,"Motivo"); } }
    public record Resolution(boolean approved,String comment) { public Resolution { required(comment,3000,"Motivo"); } }
    private List<Map<String,Object>> records(UUID user,UUID callout) {
        return repository.records(user,callout).stream().map(a -> { a.put("late_minutes",ServiceRules.lateSeconds(time(a,"start_at"),time(a,"check_in"))/60.0); a.put("month_closed",ledger.closed(YearMonth.from(time(a,"start_at")))); a.put("deduction",rounded(deduction(a))); return a; }).toList();
    }
    public List<Map<String,Object>> mine() { maintenance.refresh(); return records(actor().id(),null); }
    public List<Map<String,Object>> team(UUID id) { services.operationPermission(callouts.snapshot(id,false)); maintenance.refresh(); return records(null,id); }
    public void mark(UUID id,Mark r) {
        var e=enrollments.find(id,false); require(actor().id().equals(e.get("volunteer_id")),"Solo puedes marcar tu asistencia."); var c=services.locked(id(e,"callout_id")); ledger.ensureOpen(c); e=enrollments.find(id,false);
        valid(text(e,"status").equals("CONFIRMADA") && text(c,"kind").equals("SERVICIO"),"Necesitas una inscripción confirmada de servicio."); valid(c.get("opened_at")!=null && c.get("closed_at")==null && !text(c,"status").equals("CANCELADA"),"El encargado debe abrir el servicio y aún no cerrarlo."); valid(!now().isBefore(time(c,"start_at").minusHours(1)) && !now().isAfter(time(c,"end_at").plusHours(1)),"Marcación fuera de la ventana del servicio. Solicita revisión.");
        repository.prepareMark(id,actor().id(),id(c,"id"),c.get("responsible_id")==null?actor().id():id(c,"responsible_id")); var a=repository.byEnrollment(id);
        if(r.action().equals("ENTRADA")) { valid(now().isBefore(time(c,"end_at")),"El servicio terminó; solicita revisión si faltó registrar tu entrada."); valid(a.get("check_in")==null,"Ya registraste tu entrada."); }
        else valid(r.action().equals("SALIDA") && a.get("check_in")!=null && a.get("check_out")==null,"Registra primero tu entrada; la salida no puede repetirse.");
        repository.mark(id(a,"id"),r.action(),now());
    }
    public void confirm(UUID id,Confirmation r) {
        var a=repository.find(id,false); var c=services.locked(id(a,"callout_id")); services.operationPermission(c); ledger.ensureOpen(c);
        var model=new Attendance(id,new ServiceWindow(time(c,"start_at"),time(c,"end_at"),time(c,"deadline")),text(a,"status"),time(a,"check_in"),((Number)a.get("policy_version")).intValue(),false,"CANCELADA".equals(c.get("status")));
        double hours=model.confirm(r.checkIn(),r.checkOut(),r.hours(),chief(),now()); repository.confirm(id,r.checkIn(),r.checkOut(),rounded(hours),actor().id(),r.observations()); notifyUser(id(a,"volunteer_id"),"ASISTENCIA","Asistencia confirmada: "+hours+" h. "+c.get("title"),"/services");
    }
    public void correction(UUID id,Reason r) {
        var e=enrollments.find(id,false); require(actor().id().equals(e.get("volunteer_id")),"Solo puedes solicitar revisión propia."); var c=services.locked(id(e,"callout_id")); ledger.ensureOpen(c);
        valid(text(e,"status").equals("CONFIRMADA") && text(c,"kind").equals("SERVICIO"),"No corresponde revisión de asistencia."); valid(!time(c,"start_at").isAfter(now()),"El servicio aún no comenzó.");
        repository.correction(id,actor().id(),id(c,"id"),c.get("responsible_id")==null?actor().id():id(c,"responsible_id"),r.reason()); if(c.get("responsible_id")!=null) notifyUser(id(c,"responsible_id"),"REVISION","Revisión de asistencia: "+c.get("title"),"/callouts/"+c.get("id"));
    }
    public void justify(UUID id,Reason r) {
        require(!chief(),"Jefatura no está sujeta a penalizaciones ni necesita justificarse."); var initial=repository.find(id,false); var c=services.locked(id(initial,"callout_id")); var a=repository.find(id,true);
        require(actor().id().equals(a.get("volunteer_id")),"Solo puedes justificar tu asistencia."); valid(!time(c,"end_at").isAfter(now()),"Presenta la justificación después del servicio."); valid(text(a,"status").equals("AUSENTE") || ((Number)a.get("policy_version")).intValue()>=2 && ServiceRules.tardyDeduction(time(c,"start_at"),time(c,"end_at"),time(a,"check_in"),false)>0,"No hay inasistencia ni tardanza penalizable. Para otros errores solicita revisión.");
        repository.justify(UUID.randomUUID(),id,actor().id(),r.reason(),now(),ServiceRules.isLateJustification(time(c,"end_at"),now()),time(a,"check_in")==null?"AUSENCIA":"TARDANZA");
    }
    public List<Map<String,Object>> justifications() {
        var rows=repository.justifications(chief()?null:actor().id());
        for(var j:rows) { j.put("month_closed",ledger.closed(YearMonth.from(time(j,"start_at")))); j.put("history",repository.justificationHistory(id(j,"id"))); } return rows;
    }
    public void resolve(UUID id,Resolution r) {
        require(chief(),"Solo jefatura resuelve justificaciones."); var initial=repository.justification(id,false); var c=services.locked(id(initial,"callout_id")); var j=repository.justification(id,true); var a=repository.justificationAttendance(id);
        double before=rounded(deduction(a)); a.put("justification_status",r.approved()?"APROBADA":"RECHAZADA"); double after=rounded(deduction(a));
        var decision=new Justification(id,text(j,"status")).resolve(r.approved(),r.comment(),actor().id(),now(),before,after); repository.decision(decision);
        if(ledger.closed(c) && decision.deductionDelta()!=0) repository.adjustment(YearMonth.from(time(c,"start_at")),id(j,"volunteer_id"),decision);
        notifyUser(id(j,"volunteer_id"),"JUSTIFICACION","Justificación "+(r.approved()?"aprobada":"rechazada")+": "+r.comment(),"/justifications");
    }
    public Map<String,Object> hours(String month) { maintenance.refresh(); return summary(actor().id(),month==null?YearMonth.from(now()):YearMonth.parse(month)); }
    Map<String,Object> summary(UUID user,YearMonth month) {
        boolean exempt="JEFATURA".equals(repository.userRole(user));
        if(ledger.closed(month)) {
            var rows=repository.snapshot(user,month); var result=new LinkedHashMap<String,Object>(); result.putAll(rows.isEmpty()?Map.of("earned",0,"deductions",0,"hours",0,"pending",0):rows.get(0));
            var balance=new MonthlyBalance(((Number)result.get("earned")).doubleValue(),((Number)result.get("deductions")).doubleValue(),((Number)result.get("hours")).doubleValue(),repository.deductionDelta(user,month),exempt);
            result.put("original_hours",balance.originalHours()); result.put("original_deductions",balance.originalDeduction()); result.put("adjustment",balance.adjustment()); result.put("deductions",balance.deductions()); result.put("hours",balance.hours()); result.put("month",month.toString()); result.put("closed",true); return result;
        }
        var rows=repository.monthlyRecords(user,month,!chief() && !user.equals(actor().id())?actor().id():null); double earned=0,deductions=0; int pending=0;
        for(var a:rows) { if(text(a,"status").equals("CONFIRMADA") && a.get("approved_hours")!=null) earned+=((Number)a.get("approved_hours")).doubleValue(); if(!exempt) deductions+=deduction(a); if(Set.of("PENDIENTE_REVISION","INCOMPLETA").contains(text(a,"status"))) pending++; }
        return Map.of("month",month.toString(),"earned",rounded(earned),"deductions",rounded(deductions),"hours",rounded(earned-deductions),"pending",pending);
    }
    public List<Map<String,Object>> report(String month) {
        require(chief(),"Solo jefatura consulta el reporte general."); maintenance.refresh(); var parsed=YearMonth.parse(month);
        return (ledger.closed(parsed)?repository.reportUsers(parsed):repository.users()).stream().map(u -> { var row=new LinkedHashMap<String,Object>(u); row.putAll(summary(id(u,"id"),parsed)); return (Map<String,Object>)row; }).toList();
    }
    public Object progress() {
        if(chief()) return Map.of("applicable",false,"condition","JEFATURA","months",List.of()); maintenance.refresh(); double earned=0,deductions=0; int pending=0; var details=new ArrayList<Map<String,Object>>();
        for(var month:repository.months(actor().id())) { var row=summary(actor().id(),YearMonth.from(month)); details.add(row); earned+=((Number)row.get("earned")).doubleValue(); deductions+=((Number)row.get("deductions")).doubleValue(); pending+=((Number)row.get("pending")).intValue(); }
        return Map.of("applicable",true,"progress",graduation.progress(rounded(earned),rounded(deductions),pending,actor().graduated()),"condition",actor().graduated()?"GRADUADO":"FORMACION","target_period","ACUMULADO","months",details);
    }
    public Map<String,Object> reportStatus(String month) {
        require(chief(),"Solo jefatura gestiona cierres mensuales."); maintenance.refresh(); var parsed=YearMonth.parse(month); var rows=repository.reportStatus(parsed); var row=new LinkedHashMap<String,Object>();
        row.put("status",rows.isEmpty()?"EN_CURSO":rows.get(0).get("status")); row.put("approved_at",rows.isEmpty()?null:rows.get(0).get("approved_at")); row.put("due",parsed.plusMonths(1).atDay(3).toString()); row.put("overdue",now().toLocalDate().isAfter(parsed.plusMonths(1).atDay(3)) && !ledger.closed(parsed)); return row;
    }
    public void approve(String month) {
        require(chief(),"Solo jefatura aprueba el reporte mensual."); var parsed=YearMonth.parse(month); valid(parsed.isBefore(YearMonth.from(now())),"El mes todavía no terminó."); maintenance.refresh(); repository.lockMonthlyCallouts(parsed); valid(!repository.unfinished(parsed,now()),"Hay servicios del mes que todavía no terminaron. Espera a su finalización para aprobar."); repository.lockReport(parsed); valid(!ledger.closed(parsed),"El reporte ya está aprobado y no se puede volver a cerrar.");
        for(var u:repository.users()) { var s=summary(id(u,"id"),parsed); repository.saveSnapshot(parsed,id(u,"id"),text(u,"name"),s); notifyUser(id(u,"id"),"REPORTE","Reporte de "+parsed+" aprobado y publicado. Saldo: "+s.get("hours")+" h.","/services"); }
        repository.approve(parsed,actor().id(),now());
    }
    public Document reportPdf(String month) {
        var lines=new ArrayList<String>(); for(var r:report(month)) { lines.add(r.get("name")+" | Reconocidas: "+r.get("earned")+" h | Deducciones: "+r.get("deductions")+" h | Saldo: "+r.get("hours")+" h | Pendientes: "+r.get("pending")); if(Boolean.TRUE.equals(r.get("closed"))) lines.add("Cierre original: "+r.get("original_hours")+" h | Ajustes posteriores: "+r.get("adjustment")+" h"); }
        return new Document("reporte-"+YearMonth.parse(month)+".pdf","Reporte mensual - "+month,lines);
    }
    private static double rounded(double value) { return Math.round(value*10000.0)/10000.0; }
    private double deduction(Map<String,Object> a) {
        boolean absent=text(a,"status").equals("AUSENTE"); boolean approved="APROBADA".equals(a.get("justification_status")) && Objects.equals(a.get("justification_kind"),absent?"AUSENCIA":"TARDANZA");
        return new Attendance(id(a,"id"),new ServiceWindow(time(a,"start_at"),time(a,"end_at"),time(a,"start_at")),text(a,"status"),time(a,"check_in"),((Number)a.get("policy_version")).intValue(),"JEFATURA".equals(a.get("volunteer_role")),"CANCELADA".equals(a.get("callout_status"))).deduction(approved);
    }
}
