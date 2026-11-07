package pe.cruzroja.convocatorias.shared.infrastructure;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Component;
import pe.cruzroja.convocatorias.shared.application.NotFound;

/** Refresh is required while historical SQL and ORM participate in the same transaction. */
@Component
public class ManagedEntities {
    private final EntityManager entities;

    public ManagedEntities(EntityManager entities) {
        this.entities = entities;
    }

    public <T> T lock(Class<T> type, Object id) {
        var entity = entities.find(type, id, LockModeType.PESSIMISTIC_WRITE);
        if (entity == null) {
            throw new NotFound();
        }
        entities.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
        return entity;
    }

    public void insert(Object entity) {
        entities.persist(entity);
        flush();
    }

    public void flush() {
        // Legacy projections use JDBC and must see the writes before transaction completion.
        entities.flush();
    }
}
