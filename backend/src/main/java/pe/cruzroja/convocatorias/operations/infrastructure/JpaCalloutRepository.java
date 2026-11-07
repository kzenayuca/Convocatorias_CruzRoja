package pe.cruzroja.convocatorias.operations.infrastructure;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.cruzroja.convocatorias.operations.application.ServicesUseCases;
import pe.cruzroja.convocatorias.operations.infrastructure.persistence.CalloutEntity;
import pe.cruzroja.convocatorias.operations.infrastructure.persistence.CalloutJpaRepository;
import pe.cruzroja.convocatorias.shared.infrastructure.ManagedEntities;

/**
 * ORM commands with existing SQL projections, code reservations and historical audit operations.
 */
@Repository
@Transactional
public class JpaCalloutRepository extends JdbcCalloutRepository {
    private final ManagedEntities entities;
    private final CalloutJpaRepository callouts;

    public JpaCalloutRepository(
            JdbcTemplate db, ManagedEntities entities, CalloutJpaRepository callouts) {
        super(db);
        this.entities = entities;
        this.callouts = callouts;
    }

    @Override
    public void create(UUID id, ServicesUseCases.Callout request, UUID creator, String code) {
        callouts.saveAndFlush(CalloutEntity.draft(id, request, creator, code));
    }

    @Override
    public void edit(UUID id, ServicesUseCases.Callout request, String code) {
        entities.lock(CalloutEntity.class, id).apply(request, code);
        entities.flush();
    }

    @Override
    public void status(UUID id, String status) {
        entities.lock(CalloutEntity.class, id).storeStatus(status);
        entities.flush();
    }

    @Override
    public void cancel(UUID id, String reason) {
        entities.lock(CalloutEntity.class, id).storeCancellation(reason);
        entities.flush();
        db.update(
                """
                UPDATE enrollments SET status='CANCELADA',comment=?
                WHERE callout_id=? AND status IN ('CONFIRMADA','PENDIENTE','PENDIENTE_REEMPLAZO')
                """,
                reason,
                id);
    }

    @Override
    public void operation(UUID id, String action, String observations, LocalDateTime at) {
        entities.lock(CalloutEntity.class, id).storeOperation(action, observations, at);
        entities.flush();
    }
}
