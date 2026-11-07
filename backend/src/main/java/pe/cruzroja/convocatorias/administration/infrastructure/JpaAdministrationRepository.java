package pe.cruzroja.convocatorias.administration.infrastructure;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.cruzroja.convocatorias.administration.domain.HourTargets;
import pe.cruzroja.convocatorias.administration.infrastructure.persistence.HourTargetEntity;
import pe.cruzroja.convocatorias.shared.infrastructure.ManagedEntities;

@Repository
@Transactional
public class JpaAdministrationRepository extends JdbcAdministrationRepository {
    private final ManagedEntities entities;

    public JpaAdministrationRepository(JdbcTemplate db, ManagedEntities entities) {
        super(db);
        this.entities = entities;
    }

    @Override
    public void save(HourTargets targets, UUID actor, LocalDateTime at) {
        entities.lock(HourTargetEntity.class, "FORMACION")
                .store(targets.trainingHours(), actor, at);
        entities.lock(HourTargetEntity.class, "GRADUADO").store(targets.graduateHours(), actor, at);
        entities.flush();
    }
}
