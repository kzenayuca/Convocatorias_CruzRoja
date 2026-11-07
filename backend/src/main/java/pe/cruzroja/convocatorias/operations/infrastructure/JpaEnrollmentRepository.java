package pe.cruzroja.convocatorias.operations.infrastructure;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.cruzroja.convocatorias.operations.infrastructure.persistence.EnrollmentEntity;
import pe.cruzroja.convocatorias.shared.infrastructure.ManagedEntities;

@Repository
@Transactional
public class JpaEnrollmentRepository extends JdbcEnrollmentRepository {
    private final ManagedEntities entities;

    public JpaEnrollmentRepository(JdbcTemplate db, ManagedEntities entities) {
        super(db);
        this.entities = entities;
    }

    @Override
    public void save(
            UUID existing,
            UUID user,
            UUID callout,
            String position,
            UUID replacement,
            UUID assignedBy,
            LocalDateTime at)
            throws IllegalArgumentException {
        var entity =
                existing == null
                        ? EnrollmentEntity.create(UUID.randomUUID(), user, callout)
                        : entities.lock(EnrollmentEntity.class, existing);
        entity.accept(position, replacement, assignedBy, at);
        if (existing == null) {
            entities.insert(entity);
        } else {
            entities.flush();
        }
    }

    @Override
    public void cancellation(UUID id, String reason, LocalDateTime at) {
        entities.lock(EnrollmentEntity.class, id).requestCancellation(reason, at);
        entities.flush();
    }

    @Override
    public void clearCancellation(UUID id) {
        entities.lock(EnrollmentEntity.class, id).clearCancellation();
        entities.flush();
    }

    @Override
    public void cancelReplaced(UUID id) throws IllegalArgumentException {
        entities.lock(EnrollmentEntity.class, id).cancelForReplacement();
        entities.flush();
    }

    @Override
    public void review(UUID id, boolean approved, String comment, LocalDateTime at) {
        entities.lock(EnrollmentEntity.class, id).storeReview(approved, comment, at);
        entities.flush();
    }

    @Override
    public void cancelIneligible(UUID id) {
        entities.lock(EnrollmentEntity.class, id).storeIneligibleCancellation();
        entities.flush();
    }
}
