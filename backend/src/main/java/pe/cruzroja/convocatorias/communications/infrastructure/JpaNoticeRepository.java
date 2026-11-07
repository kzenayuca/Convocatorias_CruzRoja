package pe.cruzroja.convocatorias.communications.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.cruzroja.convocatorias.communications.domain.Announcement;
import pe.cruzroja.convocatorias.communications.domain.NoticeRepository;
import pe.cruzroja.convocatorias.communications.domain.Notification;
import pe.cruzroja.convocatorias.communications.infrastructure.persistence.AnnouncementEntity;
import pe.cruzroja.convocatorias.communications.infrastructure.persistence.NotificationEntity;
import pe.cruzroja.convocatorias.shared.application.ExecutionContext;

@Repository
@Transactional
public class JpaNoticeRepository implements NoticeRepository {
    private final EntityManager entities;
    private final ExecutionContext context;

    public JpaNoticeRepository(EntityManager entities, ExecutionContext context) {
        this.entities = entities;
        this.context = context;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Notification> notices(UUID user) {
        return entities
                .createQuery(
                        """
                select n from NotificationEntity n
                where n.recipientId = :user order by n.read, n.id
                """,
                        NotificationEntity.class)
                .setParameter("user", user)
                .getResultList()
                .stream()
                .map(NotificationEntity::toDomain)
                .toList();
    }

    @Override
    public void read(UUID id, UUID user, LocalDateTime at) {
        // Filtering by recipient preserves the existing no-op contract for inaccessible IDs.
        entities
                .createQuery(
                        """
                select n from NotificationEntity n where n.id = :id and n.recipientId = :user
                """,
                        NotificationEntity.class)
                .setParameter("id", id)
                .setParameter("user", user)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList()
                .stream()
                .findFirst()
                .ifPresent(
                        entity -> {
                            entities.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
                            entity.storeRead(user, at);
                        });
        entities.flush();
    }

    @Override
    public void save(UUID id, Announcement announcement, UUID actor) {
        entities.persist(AnnouncementEntity.published(id, announcement, actor, context.now()));
        entities.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> recipients(String audience) {
        return entities.createQuery(
                        """
                select r.id from NoticeRecipientEntity r
                where r.status = 'ACTIVO' and (:audience = 'TODOS' or r.role = :audience)
                order by r.id
                """,
                        UUID.class)
                .setParameter("audience", audience)
                .getResultList();
    }
}
