package pe.cruzroja.convocatorias.operations.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import pe.cruzroja.convocatorias.operations.domain.Enrollment;
import pe.cruzroja.convocatorias.operations.domain.EnrollmentStatus;
import pe.cruzroja.convocatorias.operations.domain.Position;

@Entity
@Table(name = "enrollments")
public class EnrollmentEntity {
    @Id private UUID id;
    private UUID volunteerId;
    private UUID calloutId;
    private String status;
    private String position;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private UUID replacementFor;
    private UUID assignedBy;
    private LocalDateTime assignedAt;
    private boolean requirementsAccepted;

    @Column(columnDefinition = "text")
    private String comment;

    private LocalDateTime cancellationRequestedAt;

    @Column(columnDefinition = "text")
    private String cancellationReason;

    protected EnrollmentEntity() {}

    public static EnrollmentEntity create(UUID id, UUID user, UUID callout) {
        var entity = new EnrollmentEntity();
        entity.id = id;
        entity.volunteerId = user;
        entity.calloutId = callout;
        return entity;
    }

    public Enrollment toDomain() {
        return new Enrollment(
                id,
                volunteerId,
                calloutId,
                EnrollmentStatus.valueOf(status),
                Position.parse(position));
    }

    public void accept(String nextPosition, UUID replacement, UUID actor, LocalDateTime at) {
        if (status != null) {
            toDomain().requireNewRequest();
        }
        position = Position.parse(nextPosition).name();
        replacementFor = replacement;
        status = replacement == null ? "CONFIRMADA" : "PENDIENTE_REEMPLAZO";
        createdAt = at;
        reviewedAt = replacement == null ? at : null;
        comment = null;
        assignedBy = actor;
        assignedAt = actor == null ? null : at;
        requirementsAccepted = true;
    }

    public void requestCancellation(String reason, LocalDateTime at) {
        cancellationRequestedAt = at;
        cancellationReason = reason;
    }

    public void clearCancellation() {
        cancellationRequestedAt = null;
        cancellationReason = null;
    }

    public void cancelForReplacement() {
        status = toDomain().cancelForReplacement().status().name();
        comment = "Cancelada con reemplazo confirmado";
    }

    public void storeReview(boolean approved, String reason, LocalDateTime at) {
        status = approved ? "CONFIRMADA" : "RECHAZADA";
        comment = reason;
        reviewedAt = at;
    }

    public void storeIneligibleCancellation() {
        status = "CANCELADA";
        comment = "Cuenta no activa o habilitación retirada por jefatura";
    }
}
