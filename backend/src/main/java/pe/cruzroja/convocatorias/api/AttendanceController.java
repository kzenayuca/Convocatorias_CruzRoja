package pe.cruzroja.convocatorias.api;

import static pe.cruzroja.convocatorias.api.Store.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pe.cruzroja.convocatorias.domain.ServiceRules;

@RestController
@RequestMapping("/api/v1")
public class AttendanceController {
    private final JdbcTemplate db;
    private final CalloutWorkflow services;
    private final ServiceMaintenance maintenance;
    private final MonthlyLedger ledger;
    private final pe.cruzroja.convocatorias.application.GraduationService graduation;
    public AttendanceController(JdbcTemplate db, CalloutWorkflow services, ServiceMaintenance maintenance,MonthlyLedger ledger,JdbcGraduationSettings settings) {
        this.db=db; this.services=services; this.maintenance=maintenance; this.ledger=ledger;
        this.graduation=new pe.cruzroja.convocatorias.application.GraduationService(settings);
    }
    public record Mark(@NotBlank String action) { }
    public record Confirmation(@NotNull LocalDateTime checkIn, @NotNull LocalDateTime checkOut,
                               @PositiveOrZero Double hours, @NotBlank @Size(max=3000) String observations) { }
    public record Reason(@NotBlank @Size(max=3000) String reason) { }
    public record Resolution(boolean approved, @NotBlank @Size(max=3000) String comment) { }

    @GetMapping("/attendance") public List<Map<String,Object>> mine() {
        maintenance.refresh(); return records(" WHERE a.volunteer_id=?",actor().id());
    }
    private List<Map<String,Object>> records(String where,Object... args) {
        return db.queryForList("""
            SELECT a.*,c.title,c.start_at,c.end_at,c.status AS callout_status,u.name,u.role AS volunteer_role,
            j.status AS justification_status,j.kind AS justification_kind,j.reason,j.late,
            (SELECT count(*) FROM enrollments r WHERE r.replacement_for=a.enrollment_id AND r.status='CONFIRMADA'
                AND r.reviewed_at<=c.start_at) AS replacements
            FROM attendance_records a JOIN callouts c ON c.id=a.callout_id JOIN users u ON u.id=a.volunteer_id
            LEFT JOIN justifications j ON j.absence_id=a.id
            """+where+" ORDER BY c.start_at DESC",args).stream().map(a -> {
                a.put("late_minutes",ServiceRules.lateSeconds(time(a,"start_at"),time(a,"check_in"))/60.0);
                a.put("month_closed",ledger.closed(YearMonth.from(time(a,"start_at"))));
                a.put("deduction",rounded(deduction(a))); return a;
            }).toList();
    }
    @GetMapping("/callouts/{id}/attendance") public List<Map<String,Object>> team(@PathVariable UUID id) {
        services.operationPermission(one(db,"SELECT * FROM callouts WHERE id=?",id)); maintenance.refresh();
        return records(" WHERE a.callout_id=?",id);
    }

    @PostMapping("/enrollments/{id}/mark") @Transactional
    public void mark(@PathVariable UUID id,@Valid @RequestBody Mark r) {
        var e=one(db,"SELECT * FROM enrollments WHERE id=?",id);
        require(actor().id().equals(e.get("volunteer_id")),"Solo puedes marcar tu asistencia.");
        var c=services.locked(id(e,"callout_id"));
        ledger.ensureOpen(c);
        e=one(db,"SELECT * FROM enrollments WHERE id=?",id);
        valid(text(e,"status").equals("CONFIRMADA") && text(c,"kind").equals("SERVICIO"),"Necesitas una inscripción confirmada de servicio.");
        valid(c.get("opened_at")!=null && c.get("closed_at")==null && !text(c,"status").equals("CANCELADA"),"El encargado debe abrir el servicio y aún no cerrarlo.");
        valid(!now().isBefore(time(c,"start_at").minusHours(1)) && !now().isAfter(time(c,"end_at").plusHours(1)),"Marcación fuera de la ventana del servicio. Solicita revisión.");
        db.update("INSERT INTO attendance_records(id,enrollment_id,volunteer_id,callout_id,responsible_id,status) VALUES (?,?,?,?,?,'PENDIENTE_REVISION') ON CONFLICT(enrollment_id) DO NOTHING",
                UUID.randomUUID(),id,actor().id(),c.get("id"),c.get("responsible_id")==null?actor().id():c.get("responsible_id"));
        var a=one(db,"SELECT * FROM attendance_records WHERE enrollment_id=? FOR UPDATE",id);
        if(r.action().equals("ENTRADA")) {
            valid(now().isBefore(time(c,"end_at")),"El servicio terminó; solicita revisión si faltó registrar tu entrada.");
            valid(a.get("check_in")==null,"Ya registraste tu entrada.");
            db.update("UPDATE attendance_records SET check_in=?,status='PENDIENTE_REVISION',signed=false,approved_hours=NULL WHERE id=?",now(),a.get("id"));
        } else {
            valid(r.action().equals("SALIDA") && a.get("check_in")!=null && a.get("check_out")==null,"Registra primero tu entrada; la salida no puede repetirse.");
            db.update("UPDATE attendance_records SET check_out=?,status='PENDIENTE_REVISION',signed=false,approved_hours=NULL WHERE id=?",now(),a.get("id"));
        }
    }

    @PostMapping("/attendance/{id}/confirm") @Transactional
    public void confirm(@PathVariable UUID id,@Valid @RequestBody Confirmation r) {
        var initial=one(db,"SELECT * FROM attendance_records WHERE id=?",id);
        var c=services.locked(id(initial,"callout_id")); services.operationPermission(c);
        ledger.ensureOpen(c);
        valid(!text(c,"status").equals("CANCELADA") && !time(c,"end_at").isAfter(now()),"Confirma las horas después del horario del servicio, no si fue cancelado.");
        valid(r.checkOut().isAfter(r.checkIn()) && !r.checkOut().isAfter(now()),"Las marcas deben ser válidas y no futuras.");
        valid(r.hours()==null || chief(),"Solo jefatura puede ajustar horas fuera del cálculo automático.");
        valid(ServiceRules.recognizedHours(time(c,"start_at"),time(c,"end_at"),r.checkIn(),r.checkOut())>0,"Las marcas deben acreditar presencia durante el horario del servicio.");
        double hours=r.hours()==null ? ServiceRules.serviceCredit(time(c,"start_at"),time(c,"end_at"),r.checkIn(),r.checkOut()) : r.hours();
        valid(Double.isFinite(hours) && hours<=48,"Horas no válidas.");
        db.update("UPDATE attendance_records SET check_in=?,check_out=?,approved_hours=?,status='CONFIRMADA',signed=true,policy_version=2,responsible_id=?,observations=? WHERE id=?",
                r.checkIn(),r.checkOut(),rounded(hours),actor().id(),r.observations(),id);
        Store.notify(db,id(initial,"volunteer_id"),"ASISTENCIA","Asistencia confirmada: "+hours+" h. "+c.get("title"),"/services");
    }
    @PostMapping("/enrollments/{id}/correction") @Transactional
    public void correction(@PathVariable UUID id,@Valid @RequestBody Reason r) {
        var e=one(db,"SELECT * FROM enrollments WHERE id=?",id); require(actor().id().equals(e.get("volunteer_id")),"Solo puedes solicitar revisión propia.");
        var c=services.locked(id(e,"callout_id"));
        ledger.ensureOpen(c);
        valid(text(e,"status").equals("CONFIRMADA") && text(c,"kind").equals("SERVICIO"),"No corresponde revisión de asistencia.");
        valid(!time(c,"start_at").isAfter(now()),"El servicio aún no comenzó.");
        db.update("INSERT INTO attendance_records(id,enrollment_id,volunteer_id,callout_id,responsible_id,status,observations) VALUES (?,?,?,?,?,'PENDIENTE_REVISION',?) ON CONFLICT(enrollment_id) DO UPDATE SET observations=EXCLUDED.observations",
                UUID.randomUUID(),id,actor().id(),c.get("id"),c.get("responsible_id")==null?actor().id():c.get("responsible_id"),"Solicitud de revisión: "+r.reason());
        if(c.get("responsible_id")!=null) Store.notify(db,id(c,"responsible_id"),"REVISION","Revisión de asistencia: "+c.get("title"),"/callouts/"+c.get("id"));
    }

    @PostMapping("/attendance/{id}/justifications") @Transactional
    public void justify(@PathVariable UUID id,@Valid @RequestBody Reason r) {
        require(!chief(),"Jefatura no está sujeta a penalizaciones ni necesita justificarse.");
        var initial=one(db,"SELECT * FROM attendance_records WHERE id=?",id);
        var c=services.locked(id(initial,"callout_id"));
        var a=one(db,"SELECT * FROM attendance_records WHERE id=? FOR UPDATE",id);
        require(actor().id().equals(a.get("volunteer_id")),"Solo puedes justificar tu asistencia.");
        valid(!time(c,"end_at").isAfter(now()),"Presenta la justificación después del servicio.");
        valid(text(a,"status").equals("AUSENTE") || ((Number)a.get("policy_version")).intValue()>=2 && ServiceRules.tardyDeduction(time(c,"start_at"),time(c,"end_at"),time(a,"check_in"),false)>0,"No hay inasistencia ni tardanza penalizable. Para otros errores solicita revisión.");
        String kind=time(a,"check_in")==null?"AUSENCIA":"TARDANZA";
        db.update("INSERT INTO justifications(id,absence_id,volunteer_id,reason,status,submitted_at,late,kind) VALUES (?,?,?,?,'PENDIENTE',?,?,?)",
                UUID.randomUUID(),id,actor().id(),r.reason(),now(),ServiceRules.isLateJustification(time(c,"end_at"),now()),kind);
    }

    @GetMapping("/justifications") public List<Map<String,Object>> justifications() {
        String sql="SELECT j.*,u.name,u.surnames,c.title,c.start_at,c.end_at,a.enrollment_id,reviewer.name AS resolved_name,(SELECT count(*) FROM enrollments r WHERE r.replacement_for=a.enrollment_id AND r.status='CONFIRMADA' AND r.reviewed_at<=c.start_at) AS replacements FROM justifications j JOIN attendance_records a ON a.id=j.absence_id JOIN callouts c ON c.id=a.callout_id JOIN users u ON u.id=j.volunteer_id LEFT JOIN users reviewer ON reviewer.id=j.resolved_by";
        var rows=chief()?db.queryForList(sql+" ORDER BY j.submitted_at DESC"):db.queryForList(sql+" WHERE j.volunteer_id=? ORDER BY j.submitted_at DESC",actor().id());
        for(var j:rows) {
            j.put("month_closed",ledger.closed(YearMonth.from(time(j,"start_at"))));
            j.put("history",db.queryForList("SELECT r.*,u.name AS resolved_name FROM justification_resolutions r JOIN users u ON u.id=r.resolved_by WHERE r.justification_id=? ORDER BY r.resolved_at,r.id",j.get("id")));
        }
        return rows;
    }
    @PostMapping("/justifications/{id}/resolve") @Transactional
    public void resolve(@PathVariable UUID id,@Valid @RequestBody Resolution r) {
        require(chief(),"Solo jefatura resuelve justificaciones.");
        var j=one(db,"SELECT j.*,a.enrollment_id,a.callout_id FROM justifications j JOIN attendance_records a ON a.id=j.absence_id WHERE j.id=?",id);
        var c=services.locked(id(j,"callout_id"));
        j=one(db,"SELECT * FROM justifications WHERE id=? FOR UPDATE",id);
        var a=one(db,"SELECT a.*,c.start_at,c.end_at,c.status AS callout_status,u.role AS volunteer_role,j.status AS justification_status,j.kind AS justification_kind FROM attendance_records a JOIN callouts c ON c.id=a.callout_id JOIN users u ON u.id=a.volunteer_id JOIN justifications j ON j.absence_id=a.id WHERE j.id=? FOR UPDATE OF a",id);
        double before=rounded(deduction(a)); String status=r.approved()?"APROBADA":"RECHAZADA";
        a.put("justification_status",status); double after=rounded(deduction(a));
        UUID resolution=UUID.randomUUID();
        db.update("INSERT INTO justification_resolutions(id,justification_id,previous_status,status,resolved_by,resolved_at,comment,deduction_before,deduction_after) VALUES (?,?,?,?,?,?,?,?,?)",resolution,id,j.get("status"),status,actor().id(),now(),r.comment(),before,after);
        db.update("UPDATE justifications SET status=?,resolution_comment=?,resolved_by=?,resolved_at=? WHERE id=?",status,r.comment(),actor().id(),now(),id);
        if(ledger.closed(c) && before!=after) db.update("INSERT INTO monthly_report_adjustments(id,month,user_id,resolution_id,deduction_delta,created_at) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),YearMonth.from(time(c,"start_at")).atDay(1),j.get("volunteer_id"),resolution,rounded(after-before),now());
        Store.notify(db,id(j,"volunteer_id"),"JUSTIFICACION","Justificación "+(r.approved()?"aprobada":"rechazada")+": "+r.comment(),"/justifications");
    }

    @GetMapping("/attendance/hours") public Map<String,Object> hours(@RequestParam(required=false) String month) {
        maintenance.refresh(); return summary(actor().id(),month==null?YearMonth.from(now()):YearMonth.parse(month));
    }
    Map<String,Object> summary(UUID user, YearMonth month) {
        boolean exempt="JEFATURA".equals(db.queryForObject("SELECT role FROM users WHERE id=?",String.class,user));
        if(ledger.closed(month)) {
            var rows=db.queryForList("SELECT earned,deductions,hours,pending FROM monthly_report_rows WHERE month=? AND user_id=?",month.atDay(1),user);
            var result=new LinkedHashMap<String,Object>();
            result.putAll(rows.isEmpty()?Map.of("earned",0,"deductions",0,"hours",0,"pending",0):rows.get(0));
            double original=((Number)result.get("hours")).doubleValue();
            double originalDeduction=((Number)result.get("deductions")).doubleValue();
            double adjustment=db.queryForObject("SELECT COALESCE(sum(deduction_delta),0) FROM monthly_report_adjustments WHERE month=? AND user_id=?",Double.class,month.atDay(1),user);
            result.put("original_hours",original); result.put("original_deductions",originalDeduction);
            result.put("adjustment",exempt?originalDeduction:rounded(-adjustment));
            result.put("deductions",exempt?0:rounded(originalDeduction+adjustment));
            result.put("hours",rounded(original+(exempt?originalDeduction:-adjustment)));
            result.put("month",month.toString()); result.put("closed",true); return result;
        }
        var sql="SELECT a.*,c.start_at,c.end_at,c.status AS callout_status,j.status AS justification_status,j.kind AS justification_kind FROM attendance_records a JOIN callouts c ON c.id=a.callout_id LEFT JOIN justifications j ON j.absence_id=a.id WHERE a.volunteer_id=? AND c.start_at>=? AND c.start_at<? AND c.status<>'CANCELADA'";
        var args=new ArrayList<Object>(List.of(user,month.atDay(1).atStartOfDay(),month.plusMonths(1).atDay(1).atStartOfDay()));
        if(!chief() && !user.equals(actor().id())) { sql+=" AND c.responsible_id=?"; args.add(actor().id()); }
        var rows=db.queryForList(sql,args.toArray());
        double earned=0,deductions=0; int pending=0;
        for(var a:rows) {
            if(text(a,"status").equals("CONFIRMADA") && a.get("approved_hours")!=null) earned+=((Number)a.get("approved_hours")).doubleValue();
            if(!exempt) deductions+=deduction(a);
            if(Set.of("PENDIENTE_REVISION","INCOMPLETA").contains(text(a,"status"))) pending++;
        }
        return Map.of("month",month.toString(),"earned",rounded(earned),"deductions",rounded(deductions),"hours",rounded(earned-deductions),"pending",pending);
    }
    @GetMapping("/reports") public List<Map<String,Object>> report(@RequestParam String month) {
        require(chief(),"Solo jefatura consulta el reporte general."); maintenance.refresh();
        var parsed=YearMonth.parse(month);
        if(ledger.closed(parsed)) return db.queryForList("SELECT user_id AS id,name FROM monthly_report_rows WHERE month=? ORDER BY name",parsed.atDay(1)).stream().map(u -> { var row=new LinkedHashMap<String,Object>(u); row.putAll(summary(id(u,"id"),parsed)); return (Map<String,Object>)row; }).toList();
        var users=db.queryForList("SELECT id,name || ' ' || surnames AS name FROM users ORDER BY name");
        return users.stream().map(u -> { var row=new LinkedHashMap<String,Object>(u); row.putAll(summary(id(u,"id"),parsed)); return (Map<String,Object>)row; }).toList();
    }
    @GetMapping("/attendance/progress") public Object progress() {
        if(chief()) return Map.of("applicable",false,"condition","JEFATURA","months",List.of());
        maintenance.refresh(); double earned=0,deductions=0; int pending=0;
        var months=db.queryForList("""
            SELECT DISTINCT date_trunc('month',c.start_at)::date AS month FROM attendance_records a JOIN callouts c ON c.id=a.callout_id WHERE a.volunteer_id=?
            UNION SELECT month FROM monthly_report_rows WHERE user_id=? ORDER BY month
            """,actor().id(),actor().id());
        var details=new ArrayList<Map<String,Object>>();
        for(var m:months) {
            var row=summary(actor().id(),YearMonth.from(((java.sql.Date)m.get("month")).toLocalDate())); details.add(row);
            earned+=((Number)row.get("earned")).doubleValue(); deductions+=((Number)row.get("deductions")).doubleValue(); pending+=((Number)row.get("pending")).intValue();
        }
        return Map.of("applicable",true,"progress",graduation.progress(rounded(earned),rounded(deductions),pending,actor().graduated()),
            "condition",actor().graduated()?"GRADUADO":"FORMACION","target_period","ACUMULADO","months",details);
    }

    @GetMapping("/reports/status") public Map<String,Object> reportStatus(@RequestParam String month) {
        require(chief(),"Solo jefatura gestiona cierres mensuales."); maintenance.refresh(); var parsed=YearMonth.parse(month);
        var rows=db.queryForList("SELECT status,approved_at FROM monthly_reports WHERE month=?",parsed.atDay(1));
        var row=new LinkedHashMap<String,Object>(); row.put("status",rows.isEmpty()?"EN_CURSO":rows.get(0).get("status"));
        row.put("approved_at",rows.isEmpty()?null:rows.get(0).get("approved_at"));
        row.put("due",parsed.plusMonths(1).atDay(3).toString()); row.put("overdue",now().toLocalDate().isAfter(parsed.plusMonths(1).atDay(3)) && !ledger.closed(parsed));
        return row;
    }
    @PostMapping("/reports/approve") @Transactional
    public void approve(@RequestParam String month) {
        require(chief(),"Solo jefatura aprueba el reporte mensual."); var parsed=YearMonth.parse(month);
        valid(parsed.isBefore(YearMonth.from(now())),"El mes todavía no terminó.");
        maintenance.refresh();
        db.queryForList("SELECT id FROM callouts WHERE start_at>=? AND start_at<? ORDER BY id FOR UPDATE",parsed.atDay(1).atStartOfDay(),parsed.plusMonths(1).atDay(1).atStartOfDay());
        valid(db.queryForObject("SELECT count(*) FROM callouts WHERE start_at>=? AND start_at<? AND end_at>? AND status<>'CANCELADA'",Integer.class,parsed.atDay(1).atStartOfDay(),parsed.plusMonths(1).atDay(1).atStartOfDay(),now())==0,"Hay servicios del mes que todavía no terminaron. Espera a su finalización para aprobar.");
        db.update("INSERT INTO monthly_reports(month) VALUES(?) ON CONFLICT DO NOTHING",parsed.atDay(1));
        one(db,"SELECT month FROM monthly_reports WHERE month=? FOR UPDATE",parsed.atDay(1));
        valid(!ledger.closed(parsed),"El reporte ya está aprobado y no se puede volver a cerrar.");
        for(var u:db.queryForList("SELECT id,name,surnames FROM users ORDER BY id")) {
            var s=summary(id(u,"id"),parsed);
            db.update("INSERT INTO monthly_report_rows(month,user_id,name,earned,deductions,hours,pending) VALUES (?,?,?,?,?,?,?)",parsed.atDay(1),u.get("id"),u.get("name")+" "+u.get("surnames"),s.get("earned"),s.get("deductions"),s.get("hours"),s.get("pending"));
            Store.notify(db,id(u,"id"),"REPORTE","Reporte de "+parsed+" aprobado y publicado. Saldo: "+s.get("hours")+" h.","/services");
        }
        db.update("UPDATE monthly_reports SET status='APROBADO',approved_by=?,approved_at=? WHERE month=?",actor().id(),now(),parsed.atDay(1));
    }
    @GetMapping("/reports/pdf") public org.springframework.http.ResponseEntity<byte[]> reportPdf(@RequestParam String month) throws java.io.IOException {
        var lines=new ArrayList<String>();
        for(var r:report(month)) {
            lines.add(r.get("name")+" | Reconocidas: "+r.get("earned")+" h | Deducciones: "+r.get("deductions")+" h | Saldo: "+r.get("hours")+" h | Pendientes: "+r.get("pending"));
            if(Boolean.TRUE.equals(r.get("closed"))) lines.add("Cierre original: "+r.get("original_hours")+" h | Ajustes posteriores: "+r.get("adjustment")+" h");
        }
        return PdfDocument.download("reporte-"+YearMonth.parse(month)+".pdf","Reporte mensual - "+month,lines);
    }
    private static double rounded(double value) { return Math.round(value*10000.0)/10000.0; }
    private double deduction(Map<String,Object> a) {
        if("JEFATURA".equals(a.get("volunteer_role"))) return 0;
        if("CANCELADA".equals(a.get("callout_status"))) return 0;
        boolean absent=text(a,"status").equals("AUSENTE");
        if(!absent && ((Number)a.get("policy_version")).intValue()<2) return 0;
        boolean approved="APROBADA".equals(a.get("justification_status")) && Objects.equals(a.get("justification_kind"),absent?"AUSENCIA":"TARDANZA");
        return absent ? ServiceRules.absenceDeduction(ServiceRules.hours(time(a,"start_at"),time(a,"end_at")),approved)
                : ServiceRules.tardyDeduction(time(a,"start_at"),time(a,"end_at"),time(a,"check_in"),approved);
    }
}
