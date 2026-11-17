package pe.cruzroja.convocatorias.communications.infrastructure;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.cruzroja.convocatorias.communications.infrastructure.persistence.NotificationEntity;
import pe.cruzroja.convocatorias.shared.application.NotificationPublisher;

@Component
@Transactional
public class JpaNotificationPublisher implements NotificationPublisher {
    private final EntityManager entities;

    public JpaNotificationPublisher(EntityManager entities) {
        this.entities = entities;
    }

    @Override
    public void publish(UUID user, String type, String message, String link) {
        entities.persist(NotificationEntity.delivery(user, type, message, link));
        entities.flush();
    }

    @Override
    public void publishOnce(UUID user, String key, String type, String message, String link) {
        // PostgreSQL's partial unique index arbitrates concurrent deliveries atomically.
        // A SELECT-before-INSERT would race and catching a constraint error would poison the
        // transaction.
        entities.flush();
        entities.createNativeQuery(
                        """
                INSERT INTO notifications(id,recipient_id,event_key,type,message,link)
                VALUES (:id,:user,:key,:type,:message,:link)
                ON CONFLICT(recipient_id,event_key) WHERE event_key IS NOT NULL DO NOTHING
                """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("user", user)
                .setParameter("key", key)
                .setParameter("type", type)
                .setParameter("message", message)
                .setParameter("link", link)
                .executeUpdate();
    }
}
