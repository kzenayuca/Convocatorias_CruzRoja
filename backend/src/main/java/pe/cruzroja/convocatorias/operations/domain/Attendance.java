package pe.cruzroja.convocatorias.operations.domain;

import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Asistencia como agregado independiente; el cierre mensual impide mutar sus marcas. */
public record Attendance(
        UUID id,
        ServiceWindow service,
        String status,
        LocalDateTime checkIn,
        int policyVersion,
        boolean exempt,
        boolean cancelled) {
    public double deduction(boolean justified) {
        if (exempt || cancelled) return 0;
        if (status.equals("AUSENTE"))
            return ServiceRules.absenceDeduction(
                    ServiceRules.hours(service.start(), service.end()), justified);
        return policyVersion < 2
                ? 0
                : ServiceRules.tardyDeduction(service.start(), service.end(), checkIn, justified);
    }

    public double confirm(
            LocalDateTime in,
            LocalDateTime out,
            Double exceptional,
            boolean chief,
            LocalDateTime now) {
        valid(
                !cancelled && !service.end().isAfter(now),
                "Confirma las horas después del horario del servicio, no si fue cancelado.");
        valid(
                in != null && out != null && out.isAfter(in) && !out.isAfter(now),
                "Las marcas deben ser válidas y no futuras.");
        valid(
                exceptional == null || chief,
                "Solo jefatura puede ajustar horas fuera del cálculo automático.");
        valid(
                ServiceRules.recognizedHours(service.start(), service.end(), in, out) > 0,
                "Las marcas deben acreditar presencia durante el horario del servicio.");
        double hours =
                exceptional == null
                        ? ServiceRules.serviceCredit(service.start(), service.end(), in, out)
                        : exceptional;
        valid(Double.isFinite(hours) && hours >= 0 && hours <= 48, "Horas no válidas.");
        return hours;
    }
}
