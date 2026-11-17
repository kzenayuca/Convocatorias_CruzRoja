package pe.cruzroja.convocatorias.operations.domain;

import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

import java.time.LocalDateTime;
import java.util.*;

/** Raíz de agregado: la convocatoria protege el total inclusivo y las funciones del equipo. */
public record Callout(
        UUID id,
        ServiceWindow window,
        String kind,
        String status,
        UUID responsibleId,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        TeamCapacity capacity,
        List<Member> members) {
    public record Member(UUID volunteer, String position, boolean graduated) {}

    public Callout {
        Objects.requireNonNull(id);
        members = List.copyOf(members);
    }

    public int[] counts(UUID excluding) {
        int[] n = new int[4];
        for (var m : members)
            if (!Objects.equals(excluding, m.volunteer())) {
                n[0]++;
                if (m.graduated()) n[1]++;
                if (m.position().equals("RADIOPERADOR")) n[2]++;
                if (m.position().equals("PILOTO")) n[3]++;
            }
        return n;
    }

    public boolean fits() {
        var n = counts(null);
        return capacity.fits(n[0], n[1], n[2], n[3]);
    }

    public boolean complete() {
        var n = counts(null);
        return capacity.complete(n[0], n[1], n[2], n[3]);
    }

    public boolean canAdmit(boolean graduated, String position, UUID replacing) {
        var n = counts(replacing);
        return capacity.fits(
                n[0] + 1,
                n[1] + (graduated ? 1 : 0),
                n[2] + (position.equals("RADIOPERADOR") ? 1 : 0),
                n[3] + (position.equals("PILOTO") ? 1 : 0));
    }

    public void requireEnrollment(
            boolean requirementsAccepted,
            boolean assigned,
            boolean replacement,
            LocalDateTime now) {
        valid(requirementsAccepted, "Debes aceptar los requisitos.");
        valid(
                CalloutStatus.parse(status).isPublished()
                        && (!assigned || openedAt == null)
                        && window.start().isAfter(now),
                "La convocatoria no admite inscripciones.");
        valid(replacement || window.deadline().isAfter(now), "Terminó el plazo de inscripción.");
    }

    public void requireInitialResponsible(UUID user) {
        valid(
                responsibleId == null && user != null,
                "Ya hay responsable. Usa Cambiar o retirar responsable e indica un motivo.");
    }

    public void requireResponsibleChange(UUID expected, UUID next, String reason) {
        required(reason, 2000, "Motivo");
        valid(
                Objects.equals(responsibleId, expected),
                "El responsable cambió. Actualiza la pantalla antes de continuar.");
        valid(
                !Objects.equals(expected, next),
                "Selecciona otra persona o retira la responsabilidad.");
    }

    public void requireOperation(String action, LocalDateTime now) {
        valid(kind.equals("SERVICIO"), "El ingreso no registra asistencia de servicio.");
        if (action.equals("ABRIR")) {
            valid(
                    openedAt == null && status.equals("CERRADA") && complete(),
                    "Faltan puestos obligatorios o el servicio ya se abrió/canceló.");
            valid(
                    !now.isBefore(window.start().minusHours(1)) && now.isBefore(window.end()),
                    "Abre dentro de la hora previa al inicio o durante el servicio.");
        } else {
            valid(
                    action.equals("CERRAR")
                            && openedAt != null
                            && closedAt == null
                            && !status.equals("CANCELADA"),
                    "Acción no permitida.");
            valid(!now.isBefore(window.end()), "No se cierra antes de terminar el horario.");
        }
    }
}
