package pe.cruzroja.convocatorias.notifications.api; import java.util.UUID; public interface NotificationQuery { long unreadCount(UUID recipientId); }
