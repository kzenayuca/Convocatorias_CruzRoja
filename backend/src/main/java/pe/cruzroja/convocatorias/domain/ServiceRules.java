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
        return Duration.between(checkIn, checkOut).toMinutes() / 60.0;
    }

    public static double absenceDeduction(double scheduledHours, boolean justificationApproved) {
        if (!Double.isFinite(scheduledHours) || scheduledHours <= 0) {
            throw new IllegalArgumentException("La duración del servicio debe ser positiva.");
        }
        return justificationApproved ? 0 : scheduledHours * 2;
    }

    public static boolean canJustify(LocalDateTime serviceStart, LocalDateTime submittedAt,
                                     LocalDateTime replacementConfirmedAt, boolean hasEvidence) {
        return serviceStart != null && submittedAt != null && replacementConfirmedAt != null
                && hasEvidence && !replacementConfirmedAt.isAfter(serviceStart)
                && !submittedAt.isAfter(serviceStart.plusHours(JUSTIFICATION_DEADLINE_HOURS));
    }
}
