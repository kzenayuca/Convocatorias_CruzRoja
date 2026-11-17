package pe.cruzroja.convocatorias.operations.infrastructure;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.cruzroja.convocatorias.operations.application.GraduationSettings;

@Repository
@Transactional(readOnly = true)
public class JpaGraduationSettings implements GraduationSettings {
    private final EntityManager entities;

    public JpaGraduationSettings(EntityManager entities) {
        this.entities = entities;
    }

    @Override
    public Double requiredHours(boolean graduated) {
        // A scalar projection also sees updates made through administration's mapping.
        var hours =
                entities.createQuery(
                                """
                select t.hours from GraduationTargetEntity t where t.condition = :condition
                """,
                                BigDecimal.class)
                        .setParameter("condition", graduated ? "GRADUADO" : "FORMACION")
                        .getSingleResult();
        return hours == null ? null : hours.doubleValue();
    }
}
