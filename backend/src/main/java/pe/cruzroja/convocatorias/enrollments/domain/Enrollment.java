package pe.cruzroja.convocatorias.enrollments.domain;

import java.util.UUID;

public class Enrollment {
    private final UUID id;
    private final UUID volunteerId;
    private final UUID calloutId;
    private EnrollmentStatus status = EnrollmentStatus.PENDIENTE;

    public Enrollment(UUID id, UUID volunteerId, UUID calloutId) {
        if (id == null || volunteerId == null || calloutId == null) {
            throw new IllegalArgumentException("La inscripción requiere identificadores válidos");
        }
        this.id = id;
        this.volunteerId = volunteerId;
        this.calloutId = calloutId;
    }

    public void confirm() {
        requirePending();
        status = EnrollmentStatus.CONFIRMADA;
    }

    public void reject() {
        requirePending();
        status = EnrollmentStatus.RECHAZADA;
    }

    public void cancel() {
        if (status != EnrollmentStatus.PENDIENTE && status != EnrollmentStatus.CONFIRMADA) {
            throw new IllegalStateException("Solo puede cancelar una inscripción pendiente o confirmada");
        }
        status = EnrollmentStatus.CANCELADA;
    }

    private void requirePending() {
        if (status != EnrollmentStatus.PENDIENTE) {
            throw new IllegalStateException("La inscripción ya no está pendiente");
        }
    }

    public UUID id() { return id; }
    public UUID volunteerId() { return volunteerId; }
    public UUID calloutId() { return calloutId; }
    public EnrollmentStatus status() { return status; }
}
