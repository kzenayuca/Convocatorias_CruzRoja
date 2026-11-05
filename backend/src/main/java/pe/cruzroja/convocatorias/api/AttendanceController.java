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
    public AttendanceController(JdbcTemplate db, CalloutWorkflow services, ServiceMaintenance maintenance) {
        this.db=db; this.services=services; this.maintenance=maintenance;
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
            SELECT a.*,c.title,c.start_at,c.end_at,c.status AS callout_status,u.name,
            j.status AS justification_status,j.kind AS justification_kind,j.reason,j.late,
            (SELECT count(*) FROM enrollments r WHERE r.replacement_for=a.enrollment_id AND r.status='CONFIRMADA'
                AND r.reviewed_at<=c.start_at) AS replacements
            FROM attendance_records a JOIN callouts c ON c.id=a.callout_id JOIN users u ON u.id=a.volunteer_id
            LEFT JOIN justifications j ON j.absence_id=a.id
            """+where+" ORDER BY c.start_at DESC",args).stream().map(a -> {
                a.put("late_minutes",ServiceRules.lateSeconds(time(a,"start_at"),time(a,"check_in"))/60.0);
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
        valid(text(e,"status").equals("CONFIRMADA") && text(c,"kind").equals("SERVICIO"),"No corresponde revisión de asistencia.");
        valid(!time(c,"start_at").isAfter(now()),"El servicio aún no comenzó.");
        db.update("INSERT INTO attendance_records(id,enrollment_id,volunteer_id,callout_id,responsible_id,status,observations) VALUES (?,?,?,?,?,'PENDIENTE_REVISION',?) ON CONFLICT(enrollment_id) DO UPDATE SET observations=EXCLUDED.observations",
                UUID.randomUUID(),id,actor().id(),c.get("id"),c.get("responsible_id")==null?actor().id():c.get("responsible_id"),"Solicitud de revisión: "+r.reason());
        if(c.get("responsible_id")!=null) Store.notify(db,id(c,"responsible_id"),"REVISION","Revisión de asistencia: "+c.get("title"),"/callouts/"+c.get("id"));
    }

    @PostMapping("/attendance/{id}/justifications") @Transactional
    public void justify(@PathVariable UUID id,@Valid @RequestBody Reason r) {
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
        String sql="SELECT j.*,u.name,c.title,c.start_at,c.end_at,a.enrollment_id,(SELECT count(*) FROM enrollments r WHERE r.replacement_for=a.enrollment_id AND r.status='CONFIRMADA' AND r.reviewed_at<=c.start_at) AS replacements FROM justifications j JOIN attendance_records a ON a.id=j.absence_id JOIN callouts c ON c.id=a.callout_id JOIN users u ON u.id=j.volunteer_id";
        return chief()?db.queryForList(sql+" ORDER BY j.submitted_at DESC"):db.queryForList(sql+" WHERE j.volunteer_id=? ORDER BY j.submitted_at DESC",actor().id());
    }
    @PostMapping("/justifications/{id}/resolve") @Transactional
    public void resolve(@PathVariable UUID id,@Valid @RequestBody Resolution r) {
        require(chief(),"Solo jefatura resuelve justificaciones.");
        var j=one(db,"SELECT j.*,a.enrollment_id,a.callout_id FROM justifications j JOIN attendance_records a ON a.id=j.absence_id WHERE j.id=?",id);
        var c=services.locked(id(j,"callout_id"));
        one(db,"SELECT id FROM justifications WHERE id=? FOR UPDATE",id);
        if(r.approved() && text(j,"kind").equals("AUSENCIA")) valid(db.queryForObject("SELECT count(*) FROM enrollments WHERE replacement_for=? AND status='CONFIRMADA' AND reviewed_at<=?",Integer.class,j.get("enrollment_id"),time(c,"start_at"))>0,"Una inasistencia requiere reemplazo aceptado antes del inicio. La tardanza no requiere reemplazo.");
        db.update("UPDATE justifications SET status=?,resolution_comment=? WHERE id=?",r.approved()?"APROBADA":"RECHAZADA",r.comment(),id);
        Store.notify(db,id(j,"volunteer_id"),"JUSTIFICACION","Justificación "+(r.approved()?"aprobada":"rechazada")+": "+r.comment(),"/justifications");
    }

    @GetMapping("/attendance/hours") public Map<String,Object> hours(@RequestParam(required=false) String month) {
        maintenance.refresh(); return summary(actor().id(),month==null?YearMonth.from(now()):YearMonth.parse(month));
    }
    Map<String,Object> summary(UUID user, YearMonth month) {
        var sql="SELECT a.*,c.start_at,c.end_at,c.status AS callout_status,j.status AS justification_status,j.kind AS justification_kind FROM attendance_records a JOIN callouts c ON c.id=a.callout_id LEFT JOIN justifications j ON j.absence_id=a.id WHERE a.volunteer_id=? AND c.start_at>=? AND c.start_at<? AND c.status<>'CANCELADA'";
        var args=new ArrayList<Object>(List.of(user,month.atDay(1).atStartOfDay(),month.plusMonths(1).atDay(1).atStartOfDay()));
        if(!chief() && !user.equals(actor().id())) { sql+=" AND (c.responsible_id=? OR c.created_by=?)"; args.add(actor().id()); args.add(actor().id()); }
        var rows=db.queryForList(sql,args.toArray());
        double earned=0,deductions=0; int pending=0;
        for(var a:rows) {
            if(text(a,"status").equals("CONFIRMADA") && a.get("approved_hours")!=null) earned+=((Number)a.get("approved_hours")).doubleValue();
            deductions+=deduction(a);
            if(!text(a,"status").equals("CONFIRMADA")) pending++;
        }
        return Map.of("month",month.toString(),"earned",rounded(earned),"deductions",rounded(deductions),"hours",rounded(earned-deductions),"pending",pending);
    }
    @GetMapping("/reports") public List<Map<String,Object>> report(@RequestParam String month) {
        require(manager() || actor().graduated(),"No puedes consultar reportes del equipo."); maintenance.refresh();
        var parsed=YearMonth.parse(month);
        var users=chief()?db.queryForList("SELECT id,name FROM users ORDER BY name"):
                db.queryForList("SELECT DISTINCT u.id,u.name FROM users u JOIN enrollments e ON e.volunteer_id=u.id JOIN callouts c ON c.id=e.callout_id WHERE (c.responsible_id=? OR c.created_by=?) AND e.status IN ('CONFIRMADA','REEMPLAZADA') ORDER BY u.name",actor().id(),actor().id());
        return users.stream().map(u -> { var row=new LinkedHashMap<String,Object>(u); row.putAll(summary(id(u,"id"),parsed)); return (Map<String,Object>)row; }).toList();
    }
    private static double rounded(double value) { return Math.round(value*10000.0)/10000.0; }
    private double deduction(Map<String,Object> a) {
        if("CANCELADA".equals(a.get("callout_status"))) return 0;
        boolean absent=text(a,"status").equals("AUSENTE");
        if(!absent && ((Number)a.get("policy_version")).intValue()<2) return 0;
        boolean approved="APROBADA".equals(a.get("justification_status")) && Objects.equals(a.get("justification_kind"),absent?"AUSENCIA":"TARDANZA");
        return absent ? ServiceRules.absenceDeduction(ServiceRules.hours(time(a,"start_at"),time(a,"end_at")),approved)
                : ServiceRules.tardyDeduction(time(a,"start_at"),time(a,"end_at"),time(a,"check_in"),approved);
    }
}
