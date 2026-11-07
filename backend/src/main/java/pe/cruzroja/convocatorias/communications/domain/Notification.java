package pe.cruzroja.convocatorias.communications.domain;

import static pe.cruzroja.convocatorias.shared.domain.Rules.valid;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** A personal notice is independent of an announcement and preserves its first read time. */
public record Notification(
        UUID id,
        UUID recipientId,
        String type,
        String message,
        String link,
        boolean read,
        LocalDateTime readAt,
        String eventKey) {
    public Notification {
        Objects.requireNonNull(id);
        Objects.requireNonNull(recipientId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(message);
    }

    public Notification readBy(UUID actor, LocalDateTime at) {
        valid(recipientId.equals(actor), "Solo puedes leer tus propias notificaciones.");
        Objects.requireNonNull(at);
        return new Notification(
                id, recipientId, type, message, link, true, readAt == null ? at : readAt, eventKey);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Notification notification && id.equals(notification.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
