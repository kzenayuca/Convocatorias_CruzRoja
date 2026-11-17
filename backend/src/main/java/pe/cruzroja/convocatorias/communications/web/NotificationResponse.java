package pe.cruzroja.convocatorias.communications.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.UUID;
import pe.cruzroja.convocatorias.communications.domain.Notification;

/** Keeps the public JSON stable without coupling the domain to Jackson or maps. */
public record NotificationResponse(
        UUID id,
        @JsonProperty("recipient_id") UUID recipientId,
        String type,
        String message,
        String link,
        @JsonProperty("is_read") boolean read,
        @JsonProperty("read_at") LocalDateTime readAt,
        @JsonProperty("event_key") String eventKey) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.id(),
                notification.recipientId(),
                notification.type(),
                notification.message(),
                notification.link(),
                notification.read(),
                notification.readAt(),
                notification.eventKey());
    }
}
