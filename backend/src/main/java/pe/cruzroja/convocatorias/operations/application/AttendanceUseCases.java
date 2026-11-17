package pe.cruzroja.convocatorias.operations.application;

import static pe.cruzroja.convocatorias.shared.application.Projection.*;
import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

import java.time.*;
import java.util.*;
import pe.cruzroja.convocatorias.operations.domain.*;
import pe.cruzroja.convocatorias.shared.application.*;

public class AttendanceUseCases extends UseCaseSupport implements GraduationEligibility {
    private final AttendanceRepository repository;
    private final EnrollmentRepository enrollments;
    private final CalloutRepository callouts;
    private final CalloutWorkflow services;
    private final ServiceMaintenance maintenance;
    private final MonthlyLedger ledger;
    private final GraduationService graduation;
    private final ImageInspector images;

    public AttendanceUseCases(
            AttendanceRepository repository,
            EnrollmentRepository enrollments,
            CalloutRepository callouts,
            CalloutWorkflow services,
            ServiceMaintenance maintenance,
            MonthlyLedger ledger,
            GraduationSettings settings,
            ImageInspector images,
            ExecutionContext context,
            NotificationPublisher notifications) {
        super(context, notifications);
        this.repository = repository;
        this.enrollments = enrollments;
        this.callouts = callouts;
        this.services = services;
        this.maintenance = maintenance;
        this.ledger = ledger;
        this.graduation = new GraduationService(settings);
        this.images = images;
    }

    public record Mark(String action) {
        public Mark {
            required(action, 30, "Acción");
        }
    }

    public record Confirmation(
            LocalDateTime checkIn, LocalDateTime checkOut, Double hours, String observations) {
        public Confirmation {
            valid(checkIn != null && checkOut != null, "Indica entrada y salida.");
            valid(hours == null || hours >= 0, "Horas no válidas.");
            required(observations, 3000, "Observaciones");
        }
    }

    public record Reason(String reason) {
        public Reason {
            required(reason, 3000, "Motivo");
        }
    }

    public record Resolution(boolean approved, String comment) {
        public Resolution {
            required(comment, 3000, "Motivo");
        }
    }

    private List<Map<String, Object>> records(UUID user, UUID callout) {
        return repository.records(user, callout).stream()
                .map(
                        a -> {
                            a.put(
                                    "late_minutes",
                                    ServiceRules.lateSeconds(
                                                    time(a, "start_at"), time(a, "check_in"))
                                            / 60.0);
                            a.put(
                                    "month_closed",
                                    ledger.closed(YearMonth.from(time(a, "start_at"))));
                            a.put("deduction", rounded(deduction(a)));
                            a.put("review_requests", repository.reviews(id(a, "id")));
                            return a;
                        })
                .toList();
    }

    public List<Map<String, Object>> mine() {
        maintenance.refresh();
        return records(actor().id(), null);
    }

    public List<Map<String, Object>> team(UUID id) {
        services.operationPermission(callouts.snapshot(id, false));
        maintenance.refresh();
        return records(null, id);
    }

    public void mark(UUID id, Mark r) {
        var e = enrollments.find(id, false);
        require(actor().id().equals(e.get("volunteer_id")), "Solo puedes marcar tu asistencia.");
        var c = services.locked(id(e, "callout_id"));
        ledger.ensureOpen(c);
        e = enrollments.find(id, false);
        valid(
                text(e, "status").equals("CONFIRMADA") && text(c, "kind").equals("SERVICIO"),
                "Necesitas una inscripción confirmada de servicio.");
        valid(
                c.get("opened_at") != null
                        && c.get("closed_at") == null
                        && !text(c, "status").equals("CANCELADA"),
                "El encargado debe abrir el servicio y aún no cerrarlo.");
        valid(
                !now().isBefore(time(c, "start_at").minusHours(1))
                        && !now().isAfter(time(c, "end_at").plusHours(1)),
                "Marcación fuera de la ventana del servicio. Solicita revisión.");
        repository.prepareMark(
                id,
                actor().id(),
                id(c, "id"),
                c.get("responsible_id") == null ? actor().id() : id(c, "responsible_id"));
        var a = repository.byEnrollment(id);
        if (r.action().equals("ENTRADA")) {
            valid(
                    now().isBefore(time(c, "end_at")),
                    "El servicio terminó; solicita revisión si faltó registrar tu entrada.");
            valid(a.get("check_in") == null, "Ya registraste tu entrada.");
        } else
            valid(
                    r.action().equals("SALIDA")
                            && a.get("check_in") != null
                            && a.get("check_out") == null,
                    "Registra primero tu entrada; la salida no puede repetirse.");
        repository.mark(id(a, "id"), r.action(), now());
        if (r.action().equals("SALIDA"))
            notifyReviewers(
                    c,
                    "Marcas de " + repository.userName(actor().id()) + " listas para validar.",
                    "/callouts/" + c.get("id") + "?attendance=" + a.get("id"));
    }

    public void confirm(UUID id, Confirmation r) {
        var a = repository.find(id, false);
        var c = services.locked(id(a, "callout_id"));
        services.operationPermission(c);
        ledger.ensureOpen(c);
        var model =
                new Attendance(
                        id,
                        new ServiceWindow(
                                time(c, "start_at"), time(c, "end_at"), time(c, "deadline")),
                        text(a, "status"),
                        time(a, "check_in"),
                        ((Number) a.get("policy_version")).intValue(),
                        false,
                        "CANCELADA".equals(c.get("status")));
        double hours = model.confirm(r.checkIn(), r.checkOut(), r.hours(), chief(), now());
        repository.confirm(
                id, r.checkIn(), r.checkOut(), rounded(hours), actor().id(), r.observations());
        boolean requested = false;
        for (var review : repository.reviews(id))
            if ("PENDIENTE".equals(review.get("status"))) {
                requested = true;
                UUID request = id(review, "id");
                repository.resolveReview(
                        new AttendanceReview(request, "PENDIENTE")
                                .resolve(true, r.observations(), actor().id(), now()));
                notifyUser(
                        id(a, "volunteer_id"),
                        "REVISION",
                        "Revisión de asistencia aprobada: " + hours + " h. " + r.observations(),
                        "/services?review=" + request);
            }
        if (!requested)
            notifyUser(
                    id(a, "volunteer_id"),
                    "ASISTENCIA",
                    "Asistencia confirmada: " + hours + " h. " + c.get("title"),
                    "/services?attendance=" + id);
        graduationAlert(id(a, "volunteer_id"));
    }

    public void correction(UUID id, Reason r) {
        var e = enrollments.find(id, false);
        require(
                actor().id().equals(e.get("volunteer_id")),
                "Solo puedes solicitar revisión propia.");
        var c = services.locked(id(e, "callout_id"));
        ledger.ensureOpen(c);
        valid(
                text(e, "status").equals("CONFIRMADA") && text(c, "kind").equals("SERVICIO"),
                "No corresponde revisión de asistencia.");
        valid(!time(c, "start_at").isAfter(now()), "El servicio aún no comenzó.");
        repository.correction(
                id,
                actor().id(),
                id(c, "id"),
                c.get("responsible_id") == null ? actor().id() : id(c, "responsible_id"),
                r.reason());
        var attendance = repository.byEnrollment(id);
        UUID a = id(attendance, "id");
        valid(
                repository.reviews(a).stream()
                        .noneMatch(review -> "PENDIENTE".equals(review.get("status"))),
                "Ya hay una solicitud de revisión pendiente para esta asistencia.");
        UUID request = UUID.randomUUID();
        repository.createReview(request, a, actor().id(), r.reason(), now());
        notifyReviewers(
                c,
                repository.userName(actor().id())
                        + " solicita revisión de asistencia en "
                        + c.get("title"),
                "/callouts/" + c.get("id") + "?attendance=" + a);
        notifyUser(
                actor().id(),
                "REVISION",
                "Solicitud de revisión de asistencia recibida y pendiente de validación.",
                "/services?review=" + request);
    }

    private void notifyReviewers(Map<String, Object> c, String message, String link) {
        var recipients = new LinkedHashSet<>(repository.chiefs());
        if (c.get("responsible_id") != null) recipients.add(id(c, "responsible_id"));
        for (var recipient : recipients)
            if (!recipient.equals(actor().id())) notifyUser(recipient, "REVISION", message, link);
    }

    public Map<String, Object> reviewRequest(UUID id) {
        var review = repository.review(id, false);
        if (!chief() && !actor().id().equals(review.get("volunteer_id")))
            services.operationPermission(callouts.snapshot(id(review, "callout_id"), false));
        return review;
    }

    public void rejectReview(UUID id, Resolution r) {
        var initial = repository.review(id, false);
        var c = services.locked(id(initial, "callout_id"));
        services.operationPermission(c);
        var review = repository.review(id, true);
        valid(!r.approved(), "Para aprobar confirma primero las marcas y horas de la asistencia.");
        repository.resolveReview(
                new AttendanceReview(id, text(review, "status"))
                        .resolve(false, r.comment(), actor().id(), now()));
        notifyUser(
                id(review, "volunteer_id"),
                "REVISION",
                "Revisión de asistencia rechazada: " + r.comment(),
                "/services?review=" + id);
    }

    public void justify(UUID id, Reason r) {
        justify(id, r, null);
    }

    public void justify(UUID id, Reason r, byte[] bytes) {
        require(!chief(), "Jefatura no está sujeta a penalizaciones ni necesita justificarse.");
        var initial = repository.find(id, false);
        var c = services.locked(id(initial, "callout_id"));
        var a = repository.find(id, true);
        require(
                actor().id().equals(a.get("volunteer_id")),
                "Solo puedes justificar tu asistencia.");
        valid(!time(c, "end_at").isAfter(now()), "Presenta la justificación después del servicio.");
        valid(
                text(a, "status").equals("AUSENTE")
                        || ((Number) a.get("policy_version")).intValue() >= 2
                                && ServiceRules.tardyDeduction(
                                                time(c, "start_at"),
                                                time(c, "end_at"),
                                                time(a, "check_in"),
                                                false)
                                        > 0,
                "No hay inasistencia ni tardanza penalizable. Para otros errores solicita revisión.");
        valid(
                ServiceRules.canJustify(time(c, "end_at"), now()),
                "El plazo para justificar ausencias o tardanzas vence 72 horas después de terminado el servicio.");
        valid(
                !"CANCELADA".equals(c.get("status")),
                "Un servicio cancelado no genera penalizaciones.");
        var evidence = EvidenceFile.inspect(bytes, images);
        UUID request = UUID.randomUUID();
        repository.justify(
                request,
                id,
                actor().id(),
                r.reason(),
                now(),
                false,
                time(a, "check_in") == null ? "AUSENCIA" : "TARDANZA");
        repository.evidence(request, evidence);
        var j = repository.justification(request, false);
        for (var chief : repository.chiefs())
            notifyUser(
                    chief,
                    "JUSTIFICACION",
                    j.get("name")
                            + " "
                            + j.get("surnames")
                            + " presentó una justificación de "
                            + j.get("kind")
                            + " en "
                            + c.get("title"),
                    "/justifications?request=" + request);
    }

    public List<Map<String, Object>> justifications() {
        var rows = repository.justifications(chief() ? null : actor().id());
        for (var j : rows) {
            j.put("month_closed", ledger.closed(YearMonth.from(time(j, "start_at"))));
            j.put("history", repository.justificationHistory(id(j, "id")));
        }
        return rows;
    }

    public void resolve(UUID id, Resolution r) {
        require(chief(), "Solo jefatura resuelve justificaciones.");
        var initial = repository.justification(id, false);
        valid(
                !"REEMPLAZO".equals(initial.get("kind")),
                "Asigna el reemplazo desde la solicitud antes de aprobarla.");
        var c = services.locked(id(initial, "callout_id"));
        var j = repository.justification(id, true);
        var a = repository.justificationAttendance(id);
        double before = rounded(deduction(a));
        a.put("justification_status", r.approved() ? "APROBADA" : "RECHAZADA");
        double after = rounded(deduction(a));
        var decision =
                new Justification(id, text(j, "status"))
                        .resolve(r.approved(), r.comment(), actor().id(), now(), before, after);
        repository.decision(decision);
        if (ledger.closed(c) && decision.deductionDelta() != 0)
            repository.adjustment(
                    YearMonth.from(time(c, "start_at")), id(j, "volunteer_id"), decision);
        notifyUser(
                id(j, "volunteer_id"),
                "JUSTIFICACION",
                "Justificación " + (r.approved() ? "aprobada" : "rechazada") + ": " + r.comment(),
                "/justifications?request=" + id);
        graduationAlert(id(j, "volunteer_id"));
    }

    public Map<String, Object> hours(String month) {
        maintenance.refresh();
        return summary(
                actor().id(), month == null ? YearMonth.from(now()) : YearMonth.parse(month));
    }

    Map<String, Object> summary(UUID user, YearMonth month) {
        boolean exempt = "JEFATURA".equals(repository.userRole(user));
        if (ledger.closed(month)) {
            var rows = repository.snapshot(user, month);
            var result = new LinkedHashMap<String, Object>();
            result.putAll(
                    rows.isEmpty()
                            ? Map.of("earned", 0, "deductions", 0, "hours", 0, "pending", 0)
                            : rows.get(0));
            var balance =
                    new MonthlyBalance(
                            ((Number) result.get("earned")).doubleValue(),
                            ((Number) result.get("deductions")).doubleValue(),
                            ((Number) result.get("hours")).doubleValue(),
                            repository.deductionDelta(user, month),
                            exempt);
            result.put("original_hours", balance.originalHours());
            result.put("original_deductions", balance.originalDeduction());
            result.put("adjustment", balance.adjustment());
            result.put("deductions", balance.deductions());
            result.put("hours", balance.hours());
            result.put("month", month.toString());
            result.put("closed", true);
            return result;
        }
        var rows =
                repository.monthlyRecords(
                        user, month, !chief() && !user.equals(actor().id()) ? actor().id() : null);
        double earned = 0, deductions = 0;
        int pending = 0;
        for (var a : rows) {
            if (text(a, "status").equals("CONFIRMADA") && a.get("approved_hours") != null)
                earned += ((Number) a.get("approved_hours")).doubleValue();
            if (!exempt) deductions += deduction(a);
            if (Set.of("PENDIENTE_REVISION", "INCOMPLETA").contains(text(a, "status"))) pending++;
        }
        return Map.of(
                "month",
                month.toString(),
                "earned",
                rounded(earned),
                "deductions",
                rounded(deductions),
                "hours",
                rounded(earned - deductions),
                "pending",
                pending);
    }

    public List<Map<String, Object>> report(String month) {
        require(chief(), "Solo jefatura consulta el reporte general.");
        maintenance.refresh();
        var parsed = YearMonth.parse(month);
        return (ledger.closed(parsed) ? repository.reportUsers(parsed) : repository.users())
                .stream()
                        .map(
                                u -> {
                                    var row = new LinkedHashMap<String, Object>(u);
                                    row.putAll(summary(id(u, "id"), parsed));
                                    return (Map<String, Object>) row;
                                })
                        .toList();
    }

    public Object progress() {
        if (chief())
            return Map.of("applicable", false, "condition", "JEFATURA", "months", List.of());
        maintenance.refresh();
        double earned = 0, deductions = 0;
        int pending = 0;
        var details = new ArrayList<Map<String, Object>>();
        for (var month : repository.months(actor().id())) {
            if (actor().graduated() && month.getYear() != now().getYear()) continue;
            var row = summary(actor().id(), YearMonth.from(month));
            details.add(row);
            earned += ((Number) row.get("earned")).doubleValue();
            deductions += ((Number) row.get("deductions")).doubleValue();
            pending += ((Number) row.get("pending")).intValue();
        }
        var p =
                graduation.progress(
                        rounded(earned), rounded(deductions), pending, actor().graduated());
        if (!actor().graduated() && p.required() != null && p.balance() >= p.required())
            graduationAlert(actor().id());
        return Map.of(
                "applicable",
                true,
                "progress",
                p,
                "condition",
                actor().graduated() ? "GRADUADO" : "FORMACION",
                "target_period",
                actor().graduated() ? "ANUAL" : "ACUMULADO",
                "year",
                now().getYear(),
                "eligible_for_graduation",
                !actor().graduated() && p.required() != null && p.balance() >= p.required(),
                "months",
                details);
    }

    public GraduationEligibility.Assessment assess(UUID user) {
        double balance = 0;
        for (var month : repository.months(user))
            balance += ((Number) summary(user, YearMonth.from(month)).get("hours")).doubleValue();
        return new GraduationEligibility.Assessment(
                rounded(balance), graduation.progress(0, 0, 0, false).required());
    }

    private void graduationAlert(UUID user) {
        if (!repository.trainingVolunteer(user)) return;
        var assessment = assess(user);
        if (!assessment.eligible()) return;
        for (var chief : repository.chiefs()) notificationsForGraduation(chief, user, assessment);
    }

    private void notificationsForGraduation(
            UUID chief, UUID user, GraduationEligibility.Assessment assessment) {
        notifyOnce(
                chief,
                "GRADUACION:" + user + ":" + assessment.target(),
                "GRADUACION",
                repository.userName(user)
                        + " alcanzó la meta neta de formación ("
                        + assessment.balance()
                        + " h). Revisa y valida su condición.",
                "/users?user=" + user);
    }

    public Map<String, Object> personalYear(int year) {
        require(!chief(), "Jefatura consulta los reportes generales.");
        maintenance.refresh();
        return yearly(actor().id(), year);
    }

    private Map<String, Object> yearly(UUID user, int year) {
        valid(year >= 1900 && year <= 9999, "Año de reporte no válido.");
        double earned = 0, deductions = 0, hours = 0, adjustment = 0;
        int pending = 0;
        var months = new ArrayList<Map<String, Object>>();
        for (int m = 1; m <= 12; m++) {
            var row = summary(user, YearMonth.of(year, m));
            months.add(row);
            earned += ((Number) row.get("earned")).doubleValue();
            deductions += ((Number) row.get("deductions")).doubleValue();
            hours += ((Number) row.get("hours")).doubleValue();
            pending += ((Number) row.get("pending")).intValue();
            adjustment += ((Number) row.getOrDefault("adjustment", 0)).doubleValue();
        }
        return Map.of(
                "year",
                year,
                "earned",
                rounded(earned),
                "deductions",
                rounded(deductions),
                "hours",
                rounded(hours),
                "adjustment",
                rounded(adjustment),
                "pending",
                pending,
                "months",
                months);
    }

    public List<Map<String, Object>> annualReport(int year) {
        require(chief(), "Solo Jefatura consulta el reporte anual de todos los voluntarios.");
        maintenance.refresh();
        valid(year >= 1900 && year <= 9999, "Año no válido.");
        return repository.users().stream()
                .filter(u -> !"JEFATURA".equals(u.get("role")))
                .map(
                        u -> {
                            var row = new LinkedHashMap<String, Object>(u);
                            row.putAll(yearly(id(u, "id"), year));
                            return (Map<String, Object>) row;
                        })
                .toList();
    }

    private List<String> annualLines(Map<String, Object> row) {
        var lines = new ArrayList<String>();
        lines.add(
                "Total anual: reconocidas "
                        + row.get("earned")
                        + " h | Deducciones: "
                        + row.get("deductions")
                        + " h | Saldo neto: "
                        + row.get("hours")
                        + " h | Pendientes: "
                        + row.get("pending"));
        @SuppressWarnings("unchecked")
        var months = (List<Map<String, Object>>) row.get("months");
        for (var month : months) {
            lines.add(
                    month.get("month")
                            + " | Reconocidas: "
                            + month.get("earned")
                            + " h | Deducciones: "
                            + month.get("deductions")
                            + " h | Saldo: "
                            + month.get("hours")
                            + " h"
                            + (Boolean.TRUE.equals(month.get("closed"))
                                    ? " | Mes aprobado | Ajuste posterior: "
                                            + month.get("adjustment")
                                            + " h"
                                    : " | Sin cierre aprobado"));
        }
        return lines;
    }

    public Document annualPdf(int year) {
        var lines = new ArrayList<String>();
        for (var row : annualReport(year)) {
            lines.add(text(row, "name"));
            lines.addAll(annualLines(row));
            lines.add("");
        }
        return new Document(
                "reporte-anual-" + year + ".pdf", "Reporte anual enero-diciembre - " + year, lines);
    }

    public Document personalYearPdf(int year) {
        return new Document(
                "mis-horas-" + year + ".pdf",
                "Mis horas - " + year,
                annualLines(personalYear(year)));
    }

    public Document personalMonthPdf(String month) {
        require(!chief(), "Jefatura consulta los reportes generales.");
        var row = hours(month);
        return new Document(
                "mis-horas-" + row.get("month") + ".pdf",
                "Mis horas - " + row.get("month"),
                List.of(
                        "Reconocidas: " + row.get("earned") + " h",
                        "Deducciones: " + row.get("deductions") + " h",
                        "Saldo neto: " + row.get("hours") + " h",
                        "Pendientes: " + row.get("pending"),
                        "Ajustes posteriores al cierre: "
                                + row.getOrDefault("adjustment", 0)
                                + " h"));
    }

    public Map<String, Object> reportStatus(String month) {
        require(chief(), "Solo jefatura gestiona cierres mensuales.");
        maintenance.refresh();
        var parsed = YearMonth.parse(month);
        var rows = repository.reportStatus(parsed);
        var row = new LinkedHashMap<String, Object>();
        row.put("status", rows.isEmpty() ? "EN_CURSO" : rows.get(0).get("status"));
        row.put("approved_at", rows.isEmpty() ? null : rows.get(0).get("approved_at"));
        row.put("due", parsed.plusMonths(1).atDay(3).toString());
        row.put(
                "overdue",
                now().toLocalDate().isAfter(parsed.plusMonths(1).atDay(3))
                        && !ledger.closed(parsed));
        return row;
    }

    public void approve(String month) {
        require(chief(), "Solo jefatura aprueba el reporte mensual.");
        var parsed = YearMonth.parse(month);
        valid(parsed.isBefore(YearMonth.from(now())), "El mes todavía no terminó.");
        maintenance.refresh();
        repository.lockMonthlyCallouts(parsed);
        valid(
                !repository.unfinished(parsed, now()),
                "Hay servicios del mes que todavía no terminaron. Espera a su finalización para aprobar.");
        repository.lockReport(parsed);
        valid(!ledger.closed(parsed), "El reporte ya está aprobado y no se puede volver a cerrar.");
        for (var u : repository.users()) {
            var s = summary(id(u, "id"), parsed);
            repository.saveSnapshot(parsed, id(u, "id"), text(u, "name"), s);
            notifyUser(
                    id(u, "id"),
                    "REPORTE",
                    "Reporte de "
                            + parsed
                            + " aprobado y publicado. Saldo: "
                            + s.get("hours")
                            + " h.",
                    "JEFATURA".equals(u.get("role"))
                            ? "/reports?month=" + parsed
                            : "/services?month=" + parsed);
        }
        repository.approve(parsed, actor().id(), now());
    }

    public Document reportPdf(String month) {
        var lines = new ArrayList<String>();
        for (var r : report(month)) {
            lines.add(
                    r.get("name")
                            + " | Reconocidas: "
                            + r.get("earned")
                            + " h | Deducciones: "
                            + r.get("deductions")
                            + " h | Saldo: "
                            + r.get("hours")
                            + " h | Pendientes: "
                            + r.get("pending"));
            if (Boolean.TRUE.equals(r.get("closed")))
                lines.add(
                        "Cierre original: "
                                + r.get("original_hours")
                                + " h | Ajustes posteriores: "
                                + r.get("adjustment")
                                + " h");
        }
        return new Document(
                "reporte-" + YearMonth.parse(month) + ".pdf", "Reporte mensual - " + month, lines);
    }

    private static double rounded(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private double deduction(Map<String, Object> a) {
        boolean absent = text(a, "status").equals("AUSENTE");
        boolean approved =
                "APROBADA".equals(a.get("justification_status"))
                        && Objects.equals(
                                a.get("justification_kind"), absent ? "AUSENCIA" : "TARDANZA");
        return new Attendance(
                        id(a, "id"),
                        new ServiceWindow(
                                time(a, "start_at"), time(a, "end_at"), time(a, "start_at")),
                        text(a, "status"),
                        time(a, "check_in"),
                        ((Number) a.get("policy_version")).intValue(),
                        "JEFATURA".equals(a.get("volunteer_role")),
                        "CANCELADA".equals(a.get("callout_status")))
                .deduction(approved);
    }
}
