package pe.cruzroja.convocatorias.administration.infrastructure;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.cruzroja.convocatorias.administration.domain.AdministrationRepository;
import pe.cruzroja.convocatorias.administration.domain.HourTargets;
import pe.cruzroja.convocatorias.administration.infrastructure.persistence.HourTargetEntity;
import pe.cruzroja.convocatorias.shared.infrastructure.ManagedEntities;

@Repository
@Transactional
public class JpaAdministrationRepository implements AdministrationRepository {
    private final ManagedEntities entities;
    private final EntityManager persistence;

    public JpaAdministrationRepository(EntityManager persistence, ManagedEntities entities) {
        this.persistence = persistence;
        this.entities = entities;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> settings() {
        var values =
                persistence
                        .createQuery(
                                """
                select max(case when t.condition = 'FORMACION' then t.hours else null end),
                       max(case when t.condition = 'GRADUADO' then t.hours else null end),
                       max(t.updatedAt)
                from HourTargetEntity t
                """,
                                Object[].class)
                        .getSingleResult();
        // Preserve the aggregate query's nullable fields, including an empty configuration.
        var result = new LinkedHashMap<String, Object>();
        result.put("training_hours", values[0]);
        result.put("graduate_hours", values[1]);
        result.put("updated_at", values[2]);
        return result;
    }

    @Override
    public void save(HourTargets targets, UUID actor, LocalDateTime at) {
        entities.lock(HourTargetEntity.class, "FORMACION")
                .store(targets.trainingHours(), actor, at);
        entities.lock(HourTargetEntity.class, "GRADUADO").store(targets.graduateHours(), actor, at);
        entities.flush();
    }
}
