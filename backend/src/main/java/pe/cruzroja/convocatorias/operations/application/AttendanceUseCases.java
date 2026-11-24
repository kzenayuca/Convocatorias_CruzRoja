package pe.cruzroja.convocatorias.operations.application;

import static pe.cruzroja.convocatorias.shared.application.Projection.*;
import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

import java.time.*;
import java.util.*;
import pe.cruzroja.convocatorias.operations.domain.*;
import pe.cruzroja.convocatorias.shared.application.*;

public class AttendanceUseCases extends UseCaseSupport implements GraduationEligibility {
    private final AttendanceRecordRepository attendanceRecords;
    private final AttendanceReviewRepository attendanceReviews;
    private final JustificationRepository justificationRecords;
    private final MonthlyReportRepository monthlyReports;
    private final AttendanceDirectoryRepository userDirectory;
    private final EnrollmentRepository enrollments;
    private final CalloutRepository callouts;
    private final CalloutWorkflow services;
    private final ServiceMaintenance maintenance;
    private final MonthlyLedger ledger;
    private final GraduationService graduation;
    private final ImageInspector images;

    public AttendanceUseCases(
            AttendanceRecordRepository attendanceRecords,
            AttendanceReviewRepository attendanceReviews,
            JustificationRepository justificationRecords,
            MonthlyReportRepository monthlyReports,
            AttendanceDirectoryRepository userDirectory,
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
        this.attendanceRecords = attendanceRecords;
        this.attendanceReviews = attendanceReviews;
        this.justificationRecords = justificationRecords;
        this.monthlyReports = monthlyReports;
        this.userDirectory = userDirectory;
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
        return attendanceRecords.records(user, callout).stream()
                .map(this::withComputedFields)
                .toList();
    }

    private Map<String, Object> withComputedFields(Map<String, Object> attendance) {
        attendance.put(
                "late_minutes",
                ServiceRules.lateSeconds(time(attendance, "start_at"), time(attendance, "check_in"))
                        / 60.0);
        attendance.put("month_closed", ledger.closed(YearMonth.from(time(attendance, "start_at"))));
        attendance.put("deduction", rounded(deduction(attendance)));
        attendance.put("review_requests", attendanceReviews.reviews(id(attendance, "id")));
        return attendance;
    }

    public List<Map<String, Object>> mine() {
        maintenance.refresh();
        return records(actor().id(), null);
    }

    public List<Map<String, Object>> team(UUID calloutId) {
        services.operationPermission(callouts.snapshot(calloutId));
        maintenance.refresh();
        return records(null, calloutId);
    }

    public void mark(UUID enrollmentId, Mark request) {
        var enrollment = ownEnrollment(enrollmentId, "Solo puedes marcar tu asistencia.");
        var callout = services.locked(id(enrollment, "callout_id"));
        ledger.ensureOpen(callout);
        enrollment = enrollments.find(enrollmentId);
        ensureConfirmedService(
                enrollment, callout, "Necesitas una inscripción confirmada de servicio.");
        ensureServiceInProgress(callout);
        ensureWithinMarkingWindow(callout);
        attendanceRecords.prepareMark(
                enrollmentId, actor().id(), id(callout, "id"), reviewerOf(callout));
        var attendance = attendanceRecords.byEnrollment(enrollmentId);
        validateMarkAction(request.action(), callout, attendance);
        attendanceRecords.mark(id(attendance, "id"), request.action(), now());
        if (request.action().equals("SALIDA"))
            notifyReviewers(
                    callout,
                    "Marcas de " + userDirectory.userName(actor().id()) + " listas para validar.",
                    "/callouts/" + callout.get("id") + "?attendance=" + attendance.get("id"));
    }

    private Map<String, Object> ownEnrollment(UUID enrollmentId, String deniedMessage) {
        var enrollment = enrollments.find(enrollmentId);
        require(actor().id().equals(enrollment.get("volunteer_id")), deniedMessage);
        return enrollment;
    }

    private static void ensureConfirmedService(
            Map<String, Object> enrollment, Map<String, Object> callout, String message) {
        valid(
                text(enrollment, "status").equals("CONFIRMADA")
                        && text(callout, "kind").equals("SERVICIO"),
                message);
    }

    private static void ensureServiceInProgress(Map<String, Object> callout) {
        valid(
                callout.get("opened_at") != null
                        && callout.get("closed_at") == null
                        && !text(callout, "status").equals("CANCELADA"),
                "El encargado debe abrir el servicio y aún no cerrarlo.");
    }

    private void ensureWithinMarkingWindow(Map<String, Object> callout) {
        valid(
                !now().isBefore(time(callout, "start_at").minusHours(1))
                        && !now().isAfter(time(callout, "end_at").plusHours(1)),
                "Marcación fuera de la ventana del servicio. Solicita revisión.");
    }

    /** Quien valida la asistencia: el responsable del servicio o, si no hay, el propio actor. */
    private UUID reviewerOf(Map<String, Object> callout) {
        return callout.get("responsible_id") == null ? actor().id() : id(callout, "responsible_id");
    }

    private void validateMarkAction(
            String action, Map<String, Object> callout, Map<String, Object> attendance) {
        if (action.equals("ENTRADA")) {
            valid(
                    now().isBefore(time(callout, "end_at")),
                    "El servicio terminó; solicita revisión si faltó registrar tu entrada.");
            valid(attendance.get("check_in") == null, "Ya registraste tu entrada.");
        } else
            valid(
                    action.equals("SALIDA")
                            && attendance.get("check_in") != null
                            && attendance.get("check_out") == null,
                    "Registra primero tu entrada; la salida no puede repetirse.");
    }

    public void confirm(UUID attendanceId, Confirmation request) {
        var attendance = attendanceRecords.find(attendanceId);
        var callout = services.locked(id(attendance, "callout_id"));
        services.operationPermission(callout);
        ledger.ensureOpen(callout);
        var model =
                new Attendance(
                        attendanceId,
                        new ServiceWindow(
                                time(callout, "start_at"),
                                time(callout, "end_at"),
                                time(callout, "deadline")),
                        text(attendance, "status"),
                        time(attendance, "check_in"),
                        ((Number) attendance.get("policy_version")).intValue(),
                        false,
                        "CANCELADA".equals(callout.get("status")));
        double hours =
                model.confirm(
                        request.checkIn(), request.checkOut(), request.hours(), chief(), now());
        attendanceRecords.confirm(
                attendanceId,
                request.checkIn(),
                request.checkOut(),
                rounded(hours),
                actor().id(),
                request.observations());
        UUID volunteer = id(attendance, "volunteer_id");
        boolean reviewsApproved =
                approvePendingReviews(attendanceId, volunteer, hours, request.observations());
        if (!reviewsApproved)
            notifyUser(
                    volunteer,
                    "ASISTENCIA",
                    "Asistencia confirmada: " + hours + " h. " + callout.get("title"),
                    "/services?attendance=" + attendanceId);
        graduationAlert(volunteer);
    }

    /** Aprueba las revisiones pendientes de la asistencia y avisa al voluntario por cada una. */
    private boolean approvePendingReviews(
            UUID attendanceId, UUID volunteer, double hours, String observations) {
        boolean anyApproved = false;
        for (var review : attendanceReviews.reviews(attendanceId))
            if ("PENDIENTE".equals(review.get("status"))) {
                anyApproved = true;
                UUID reviewId = id(review, "id");
                attendanceReviews.resolveReview(
                        new AttendanceReview(reviewId, "PENDIENTE")
                                .resolve(true, observations, actor().id(), now()));
                notifyUser(
                        volunteer,
                        "REVISION",
                        "Revisión de asistencia aprobada: " + hours + " h. " + observations,
                        "/services?review=" + reviewId);
            }
        return anyApproved;
    }

    public void correction(UUID enrollmentId, Reason request) {
        var enrollment = ownEnrollment(enrollmentId, "Solo puedes solicitar revisión propia.");
        var callout = services.locked(id(enrollment, "callout_id"));
        ledger.ensureOpen(callout);
        ensureConfirmedService(enrollment, callout, "No corresponde revisión de asistencia.");
        valid(!time(callout, "start_at").isAfter(now()), "El servicio aún no comenzó.");
        attendanceRecords.correction(
                enrollmentId,
                actor().id(),
                id(callout, "id"),
                reviewerOf(callout),
                request.reason());
        UUID attendanceId = id(attendanceRecords.byEnrollment(enrollmentId), "id");
        ensureNoPendingReview(attendanceId);
        UUID reviewId = UUID.randomUUID();
        attendanceReviews.createReview(
                reviewId, attendanceId, actor().id(), request.reason(), now());
        notifyReviewers(
                callout,
                userDirectory.userName(actor().id())
                        + " solicita revisión de asistencia en "
                        + callout.get("title"),
                "/callouts/" + callout.get("id") + "?attendance=" + attendanceId);
        notifyUser(
                actor().id(),
                "REVISION",
                "Solicitud de revisión de asistencia recibida y pendiente de validación.",
                "/services?review=" + reviewId);
    }

    private void ensureNoPendingReview(UUID attendanceId) {
        valid(
                attendanceReviews.reviews(attendanceId).stream()
                        .noneMatch(review -> "PENDIENTE".equals(review.get("status"))),
                "Ya hay una solicitud de revisión pendiente para esta asistencia.");
    }

    private void notifyReviewers(Map<String, Object> callout, String message, String link) {
        var recipients = new LinkedHashSet<>(userDirectory.chiefs());
        if (callout.get("responsible_id") != null) recipients.add(id(callout, "responsible_id"));
        for (var recipient : recipients)
            if (!recipient.equals(actor().id())) notifyUser(recipient, "REVISION", message, link);
    }

    public Map<String, Object> reviewRequest(UUID reviewId) {
        var review = attendanceReviews.review(reviewId);
        if (!chief() && !actor().id().equals(review.get("volunteer_id")))
            services.operationPermission(callouts.snapshot(id(review, "callout_id")));
        return review;
    }

    public void rejectReview(UUID reviewId, Resolution resolution) {
        var initial = attendanceReviews.review(reviewId);
        var callout = services.locked(id(initial, "callout_id"));
        services.operationPermission(callout);
        var review = attendanceReviews.reviewForUpdate(reviewId);
        valid(
                !resolution.approved(),
                "Para aprobar confirma primero las marcas y horas de la asistencia.");
        attendanceReviews.resolveReview(
                new AttendanceReview(reviewId, text(review, "status"))
                        .resolve(false, resolution.comment(), actor().id(), now()));
        notifyUser(
                id(review, "volunteer_id"),
                "REVISION",
                "Revisión de asistencia rechazada: " + resolution.comment(),
                "/services?review=" + reviewId);
    }

    public void justify(UUID attendanceId, Reason request) {
        justify(attendanceId, request, null);
    }

    public void justify(UUID attendanceId, Reason request, byte[] evidenceImage) {
        require(!chief(), "Jefatura no está sujeta a penalizaciones ni necesita justificarse.");
        var initial = attendanceRecords.find(attendanceId);
        var callout = services.locked(id(initial, "callout_id"));
        var attendance = attendanceRecords.findForUpdate(attendanceId);
        require(
                actor().id().equals(attendance.get("volunteer_id")),
                "Solo puedes justificar tu asistencia.");
        valid(
                !time(callout, "end_at").isAfter(now()),
                "Presenta la justificación después del servicio.");
        ensurePenalized(attendance, callout);
        valid(
                ServiceRules.canJustify(time(callout, "end_at"), now()),
                "El plazo para justificar ausencias o tardanzas vence 72 horas después de terminado el servicio.");
        valid(
                !"CANCELADA".equals(callout.get("status")),
                "Un servicio cancelado no genera penalizaciones.");
        var evidence = EvidenceFile.inspect(evidenceImage, images);
        UUID justificationId = UUID.randomUUID();
        justificationRecords.justify(
                justificationId,
                attendanceId,
                actor().id(),
                request.reason(),
                now(),
                false,
                time(attendance, "check_in") == null ? "AUSENCIA" : "TARDANZA");
        justificationRecords.evidence(justificationId, evidence);
        notifyChiefsOfJustification(justificationId, callout);
    }

    /** Solo se justifica una inasistencia o una tardanza que realmente descuenta horas. */
    private static void ensurePenalized(
            Map<String, Object> attendance, Map<String, Object> callout) {
        valid(
                text(attendance, "status").equals("AUSENTE")
                        || ((Number) attendance.get("policy_version")).intValue() >= 2
                                && ServiceRules.tardyDeduction(
                                                time(callout, "start_at"),
                                                time(callout, "end_at"),
                                                time(attendance, "check_in"),
                                                false)
                                        > 0,
                "No hay inasistencia ni tardanza penalizable. Para otros errores solicita revisión.");
    }

    private void notifyChiefsOfJustification(UUID justificationId, Map<String, Object> callout) {
        var justification = justificationRecords.justification(justificationId);
        for (var chiefId : userDirectory.chiefs())
            notifyUser(
                    chiefId,
                    "JUSTIFICACION",
                    justification.get("name")
                            + " "
                            + justification.get("surnames")
                            + " presentó una justificación de "
                            + justification.get("kind")
                            + " en "
                            + callout.get("title"),
                    "/justifications?request=" + justificationId);
    }

    public List<Map<String, Object>> justifications() {
        var rows = justificationRecords.justifications(chief() ? null : actor().id());
        for (var justification : rows) {
            justification.put(
                    "month_closed", ledger.closed(YearMonth.from(time(justification, "start_at"))));
            justification.put(
                    "history", justificationRecords.justificationHistory(id(justification, "id")));
        }
        return rows;
    }

    public void resolve(UUID justificationId, Resolution resolution) {
        require(chief(), "Solo jefatura resuelve justificaciones.");
        var initial = justificationRecords.justification(justificationId);
        valid(
                !"REEMPLAZO".equals(initial.get("kind")),
                "Asigna el reemplazo desde la solicitud antes de aprobarla.");
        var callout = services.locked(id(initial, "callout_id"));
        var justification = justificationRecords.justificationForUpdate(justificationId);
        var attendance = justificationRecords.justificationAttendance(justificationId);
        double deductionBefore = rounded(deduction(attendance));
        attendance.put("justification_status", resolution.approved() ? "APROBADA" : "RECHAZADA");
        double deductionAfter = rounded(deduction(attendance));
        var decision =
                new Justification(justificationId, text(justification, "status"))
                        .resolve(
                                resolution.approved(),
                                resolution.comment(),
                                actor().id(),
                                now(),
                                deductionBefore,
                                deductionAfter);
        justificationRecords.decision(decision);
        UUID volunteer = id(justification, "volunteer_id");
        if (ledger.closed(callout) && decision.deductionDelta() != 0)
            justificationRecords.adjustment(
                    YearMonth.from(time(callout, "start_at")), volunteer, decision);
        notifyUser(
                volunteer,
                "JUSTIFICACION",
                "Justificación "
                        + (resolution.approved() ? "aprobada" : "rechazada")
                        + ": "
                        + resolution.comment(),
                "/justifications?request=" + justificationId);
        graduationAlert(volunteer);
    }

    public Map<String, Object> hours(String month) {
        maintenance.refresh();
        return summary(
                actor().id(), month == null ? YearMonth.from(now()) : YearMonth.parse(month));
    }

    Map<String, Object> summary(UUID user, YearMonth month) {
        boolean exempt = "JEFATURA".equals(userDirectory.userRole(user));
        return ledger.closed(month)
                ? closedMonthSummary(user, month, exempt)
                : openMonthSummary(user, month, exempt);
    }

    /** Mes aprobado: parte de la foto guardada al cierre y suma los ajustes posteriores. */
    private Map<String, Object> closedMonthSummary(UUID user, YearMonth month, boolean exempt) {
        var snapshotRows = monthlyReports.snapshot(user, month);
        var result = new LinkedHashMap<String, Object>();
        result.putAll(
                snapshotRows.isEmpty()
                        ? Map.of("earned", 0, "deductions", 0, "hours", 0, "pending", 0)
                        : snapshotRows.get(0));
        var balance =
                new MonthlyBalance(
                        ((Number) result.get("earned")).doubleValue(),
                        ((Number) result.get("deductions")).doubleValue(),
                        ((Number) result.get("hours")).doubleValue(),
                        monthlyReports.deductionDelta(user, month),
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

    /** Mes en curso: calcula horas, descuentos y pendientes a partir de las asistencias. */
    private Map<String, Object> openMonthSummary(UUID user, YearMonth month, boolean exempt) {
        var monthAttendances =
                monthlyReports.monthlyRecords(
                        user, month, !chief() && !user.equals(actor().id()) ? actor().id() : null);
        double earned = 0, deductions = 0;
        int pending = 0;
        for (var attendance : monthAttendances) {
            if (text(attendance, "status").equals("CONFIRMADA")
                    && attendance.get("approved_hours") != null)
                earned += ((Number) attendance.get("approved_hours")).doubleValue();
            if (!exempt) deductions += deduction(attendance);
            if (Set.of("PENDIENTE_REVISION", "INCOMPLETA").contains(text(attendance, "status")))
                pending++;
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
        return (ledger.closed(parsed) ? monthlyReports.reportUsers(parsed) : userDirectory.users())
                .stream()
                        .map(
                                user -> {
                                    var row = new LinkedHashMap<String, Object>(user);
                                    row.putAll(summary(id(user, "id"), parsed));
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
        for (var month : monthlyReports.months(actor().id())) {
            if (actor().graduated() && month.getYear() != now().getYear()) continue;
            var row = summary(actor().id(), YearMonth.from(month));
            details.add(row);
            earned += ((Number) row.get("earned")).doubleValue();
            deductions += ((Number) row.get("deductions")).doubleValue();
            pending += ((Number) row.get("pending")).intValue();
        }
        var graduationProgress =
                graduation.progress(
                        rounded(earned), rounded(deductions), pending, actor().graduated());
        boolean reachedTarget =
                !actor().graduated()
                        && graduationProgress.required() != null
                        && graduationProgress.balance() >= graduationProgress.required();
        if (reachedTarget) graduationAlert(actor().id());
        return Map.of(
                "applicable",
                true,
                "progress",
                graduationProgress,
                "condition",
                actor().graduated() ? "GRADUADO" : "FORMACION",
                "target_period",
                actor().graduated() ? "ANUAL" : "ACUMULADO",
                "year",
                now().getYear(),
                "eligible_for_graduation",
                reachedTarget,
                "months",
                details);
    }

    public GraduationEligibility.Assessment assess(UUID user) {
        double balance = 0;
        for (var month : monthlyReports.months(user))
            balance += ((Number) summary(user, YearMonth.from(month)).get("hours")).doubleValue();
        return new GraduationEligibility.Assessment(
                rounded(balance), graduation.progress(0, 0, 0, false).required());
    }

    private void graduationAlert(UUID user) {
        if (!userDirectory.trainingVolunteer(user)) return;
        var assessment = assess(user);
        if (!assessment.eligible()) return;
        for (var chief : userDirectory.chiefs())
            notificationsForGraduation(chief, user, assessment);
    }

    private void notificationsForGraduation(
            UUID chief, UUID user, GraduationEligibility.Assessment assessment) {
        notifyOnce(
                chief,
                "GRADUACION:" + user + ":" + assessment.target(),
                "GRADUACION",
                userDirectory.userName(user)
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
        for (int monthNumber = 1; monthNumber <= 12; monthNumber++) {
            var row = summary(user, YearMonth.of(year, monthNumber));
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
        return userDirectory.users().stream()
                .filter(user -> !"JEFATURA".equals(user.get("role")))
                .map(
                        user -> {
                            var row = new LinkedHashMap<String, Object>(user);
                            row.putAll(yearly(id(user, "id"), year));
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
        var rows = monthlyReports.reportStatus(parsed);
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
        monthlyReports.lockMonthlyCallouts(parsed);
        valid(
                !monthlyReports.unfinished(parsed, now()),
                "Hay servicios del mes que todavía no terminaron. Espera a su finalización para aprobar.");
        monthlyReports.lockReport(parsed);
        valid(!ledger.closed(parsed), "El reporte ya está aprobado y no se puede volver a cerrar.");
        for (var user : userDirectory.users()) {
            var monthSummary = summary(id(user, "id"), parsed);
            monthlyReports.saveSnapshot(parsed, id(user, "id"), text(user, "name"), monthSummary);
            notifyUser(
                    id(user, "id"),
                    "REPORTE",
                    "Reporte de "
                            + parsed
                            + " aprobado y publicado. Saldo: "
                            + monthSummary.get("hours")
                            + " h.",
                    "JEFATURA".equals(user.get("role"))
                            ? "/reports?month=" + parsed
                            : "/services?month=" + parsed);
        }
        monthlyReports.approve(parsed, actor().id(), now());
    }

    public Document reportPdf(String month) {
        var lines = new ArrayList<String>();
        for (var row : report(month)) {
            lines.add(
                    row.get("name")
                            + " | Reconocidas: "
                            + row.get("earned")
                            + " h | Deducciones: "
                            + row.get("deductions")
                            + " h | Saldo: "
                            + row.get("hours")
                            + " h | Pendientes: "
                            + row.get("pending"));
            if (Boolean.TRUE.equals(row.get("closed")))
                lines.add(
                        "Cierre original: "
                                + row.get("original_hours")
                                + " h | Ajustes posteriores: "
                                + row.get("adjustment")
                                + " h");
        }
        return new Document(
                "reporte-" + YearMonth.parse(month) + ".pdf", "Reporte mensual - " + month, lines);
    }

    private static double rounded(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private double deduction(Map<String, Object> attendance) {
        boolean absent = text(attendance, "status").equals("AUSENTE");
        boolean approved =
                "APROBADA".equals(attendance.get("justification_status"))
                        && Objects.equals(
                                attendance.get("justification_kind"),
                                absent ? "AUSENCIA" : "TARDANZA");
        return new Attendance(
                        id(attendance, "id"),
                        new ServiceWindow(
                                time(attendance, "start_at"),
                                time(attendance, "end_at"),
                                time(attendance, "start_at")),
                        text(attendance, "status"),
                        time(attendance, "check_in"),
                        ((Number) attendance.get("policy_version")).intValue(),
                        "JEFATURA".equals(attendance.get("volunteer_role")),
                        "CANCELADA".equals(attendance.get("callout_status")))
                .deduction(approved);
    }
}
