package pe.cruzroja.convocatorias.operations.domain;

import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** La propuesta no libera la plaza: solo una asignación de jefatura puede hacerlo. */
public final class ReplacementRequest {
    private ReplacementRequest() {}

    public static void submit(
            String reason, String proposedName, LocalDateTime start, LocalDateTime at) {
        required(reason, 3000, "Motivo y sustento");
        if (proposedName != null)
            required(proposedName, 281, "Nombre completo del reemplazo propuesto");
        valid(
                ServiceRules.canRequestCancellation(start, at),
                "Solicita el reemplazo con al menos 48 horas antes del inicio del servicio.");
    }

    public static void approve(
            UUID original,
            UUID replacement,
            boolean verified,
            LocalDateTime start,
            LocalDateTime at) {
        valid(
                replacement != null && !replacement.equals(original),
                "Debes asignar otra persona como reemplazo antes de aprobar.");
        valid(verified, "Jefatura debe verificar los requisitos del reemplazo.");
        valid(
                start.isAfter(at),
                "No se puede asignar un reemplazo después de iniciado el servicio.");
    }
}
