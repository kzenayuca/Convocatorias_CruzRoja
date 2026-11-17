package pe.cruzroja.convocatorias.communications.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import pe.cruzroja.convocatorias.communications.domain.Notification;

@Entity
@Table(name = "notifications")
public class NotificationEntity {
    @Id private UUID id;
    private UUID recipientId;

    @Column(length = 50)
    private String type;

    @Column(columnDefinition = "text")
    private String message;

    @Column(length = 300)
    private String link;

    @Column(name = "is_read")
    private boolean read;

    private LocalDateTime readAt;

    @Column(length = 300)
    private String eventKey;

    protected NotificationEntity() {}

    public static NotificationEntity delivery(UUID user, String type, String message, String link) {
        var entity = new NotificationEntity();
        entity.id = UUID.randomUUID();
        entity.recipientId = user;
        entity.type = type;
        entity.message = message;
        entity.link = link;
        return entity;
    }

    public Notification toDomain() {
        return new Notification(id, recipientId, type, message, link, read, readAt, eventKey);
    }

    public void storeRead(UUID actor, LocalDateTime at) {
        var next = toDomain().readBy(actor, at);
        read = next.read();
        readAt = next.readAt();
    }
}
