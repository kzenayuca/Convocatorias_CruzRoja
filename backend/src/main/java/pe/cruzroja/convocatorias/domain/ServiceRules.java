package pe.cruzroja.convocatorias.domain;

import java.time.Duration;
import java.time.LocalDateTime;

/** Reglas puras: no dependen de Spring, de la interfaz ni de la base de datos. */
public final class ServiceRules {
    public static final int JUSTIFICATION_DEADLINE_HOURS = 48;

    private ServiceRules() { }

    public static double hours(LocalDateTime checkIn, LocalDateTime checkOut) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("La asistencia necesita una entrada y una salida válidas.");
        }
        return Duration.between(checkIn, checkOut).toSeconds() / 3600.0;
    }

    public static double absenceDeduction(double scheduledHours, boolean justificationApproved) {
        if (!Double.isFinite(scheduledHours) || scheduledHours <= 0) {
            throw new IllegalArgumentException("La duración del servicio debe ser positiva.");
        }
        return justificationApproved ? 0 : scheduledHours * 2;
    }

    public static boolean canJustify(LocalDateTime serviceStart, LocalDateTime serviceEnd, LocalDateTime submittedAt,
                                     LocalDateTime replacementConfirmedAt, boolean hasEvidence) {
        return serviceStart != null && serviceEnd != null && submittedAt != null && replacementConfirmedAt != null
                && hasEvidence && !replacementConfirmedAt.isAfter(serviceStart)
                && !submittedAt.isBefore(serviceEnd)
                && !submittedAt.isAfter(serviceEnd.plusHours(JUSTIFICATION_DEADLINE_HOURS));
    }

    public static double recognizedHours(LocalDateTime start, LocalDateTime end,
                                         LocalDateTime checkIn, LocalDateTime checkOut) {
        if (checkIn == null || checkOut == null) return 0;
        var from = checkIn.isAfter(start) ? checkIn : start;
        var to = checkOut.isBefore(end) ? checkOut : end;
        return to.isAfter(from) ? Duration.between(from, to).toSeconds() / 3600.0 : 0;
    }

    /** Más de diez minutos: el segundo 601 ya pertenece al minuto once. */
    public static long lateSeconds(LocalDateTime start, LocalDateTime checkIn) {
        return checkIn == null || !checkIn.isAfter(start) ? 0 : Duration.between(start, checkIn).toSeconds();
    }

    public static double tardyDeduction(LocalDateTime start, LocalDateTime end,
                                        LocalDateTime checkIn, boolean justified) {
        long seconds = lateSeconds(start, checkIn);
        if (justified || seconds <= 600) return 0;
        return Math.min(seconds / 3600.0, hours(start, end)) * 2;
    }

    /** Base programada; los primeros diez minutos tienen tolerancia. No genera extras. */
    public static double serviceCredit(LocalDateTime start, LocalDateTime end,
                                        LocalDateTime checkIn, LocalDateTime checkOut) {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) return 0;
        var effectiveIn = lateSeconds(start, checkIn) <= 600 ? start : checkIn;
        return recognizedHours(start, end, effectiveIn, checkOut);
    }

    public static boolean isLateJustification(LocalDateTime end, LocalDateTime submitted) {
        return submitted.isAfter(end.plusHours(JUSTIFICATION_DEADLINE_HOURS));
    }

    public static boolean canRequestCancellation(LocalDateTime start, LocalDateTime requested) {
        return !requested.isAfter(start.minusHours(48));
    }
}
