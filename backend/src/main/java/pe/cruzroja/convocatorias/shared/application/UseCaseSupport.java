package pe.cruzroja.convocatorias.shared.application;

import java.time.LocalDateTime;
import java.util.UUID;

public abstract class UseCaseSupport {
    private final ExecutionContext context;
    private final NotificationPublisher notifications;

    protected UseCaseSupport(ExecutionContext context, NotificationPublisher notifications) {
        this.context = context;
        this.notifications = notifications;
    }

    protected Actor actor() {
        return context.actor();
    }

    protected LocalDateTime now() {
        return context.now();
    }

    protected boolean chief() {
        return actor().chief();
    }

    protected boolean manager() {
        return chief();
    }

    protected void notifyUser(UUID user, String type, String message, String link) {
        notifications.publish(user, type, message, link);
    }

    protected void notifyOnce(UUID user, String key, String type, String message, String link) {
        notifications.publishOnce(user, key, type, message, link);
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new AccessDenied(message);
    }
}
