package pe.cruzroja.convocatorias.operations.domain;

import static pe.cruzroja.convocatorias.shared.domain.Rules.valid;

import java.util.Objects;
import java.util.UUID;

/** Identity and transitions of a commitment. Admission capacity belongs to Callout. */
public final class Enrollment {
    private final UUID id;
    private final UUID volunteerId;
    private final UUID calloutId;
    private final EnrollmentStatus status;
    private final Position position;

    public Enrollment(
            UUID id, UUID volunteerId, UUID calloutId, EnrollmentStatus status, Position position) {
        this.id = Objects.requireNonNull(id);
        this.volunteerId = Objects.requireNonNull(volunteerId);
        this.calloutId = Objects.requireNonNull(calloutId);
        this.status = Objects.requireNonNull(status);
        this.position = Objects.requireNonNull(position);
    }

    public static Enrollment accept(UUID id, UUID volunteer, UUID callout, Position position) {
        position.requireSelfEnrollment();
        return new Enrollment(id, volunteer, callout, EnrollmentStatus.CONFIRMADA, position);
    }

    public void requireNewRequest() {
        valid(
                status.allowsNewRequest(),
                "Ya tienes una inscripción o una resolución en esta convocatoria.");
    }

    public Enrollment cancelForReplacement() {
        valid(
                status == EnrollmentStatus.CONFIRMADA,
                "Solo se reemplaza una inscripción confirmada.");
        return withStatus(EnrollmentStatus.CANCELADA);
    }

    public Enrollment assignResponsible() {
        valid(
                status == EnrollmentStatus.CONFIRMADA && position.isGeneralVolunteer(),
                "Primero inscribe a la persona como voluntario general.");
        return new Enrollment(id, volunteerId, calloutId, status, Position.RESPONSABLE);
    }

    public Enrollment removeResponsibility() {
        valid(
                status == EnrollmentStatus.CONFIRMADA && position == Position.RESPONSABLE,
                "La persona no es el responsable confirmado.");
        return new Enrollment(id, volunteerId, calloutId, status, Position.SOCORRISTA);
    }

    private Enrollment withStatus(EnrollmentStatus next) {
        return new Enrollment(id, volunteerId, calloutId, next, position);
    }

    public UUID id() {
        return id;
    }

    public UUID volunteerId() {
        return volunteerId;
    }

    public UUID calloutId() {
        return calloutId;
    }

    public EnrollmentStatus status() {
        return status;
    }

    public Position position() {
        return position;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Enrollment enrollment && id.equals(enrollment.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
