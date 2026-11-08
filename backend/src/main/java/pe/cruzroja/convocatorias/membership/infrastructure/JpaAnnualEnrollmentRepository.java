package pe.cruzroja.convocatorias.membership.infrastructure;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.cruzroja.convocatorias.membership.application.AnnualEnrollmentRepository;
import pe.cruzroja.convocatorias.membership.infrastructure.persistence.AnnualEnrollmentEntity;
import pe.cruzroja.convocatorias.membership.infrastructure.persistence.GraduationHistoryEntity;

@Repository
@Transactional
public class JpaAnnualEnrollmentRepository implements AnnualEnrollmentRepository {
    private final EntityManager entities;

    public JpaAnnualEnrollmentRepository(EntityManager entities) {
        this.entities = entities;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID user) {
        return entities
                .createQuery(
                        """
                select e from AnnualEnrollmentEntity e where e.userId = :user order by e.year desc
                """,
                        AnnualEnrollmentEntity.class)
                .setParameter("user", user)
                .getResultList()
                .stream()
                .map(AnnualEnrollmentEntity::projection)
                .toList();
    }

    @Override
    public boolean register(UUID user, int year, UUID actor, LocalDateTime at) {
        // The existing primary key arbitrates simultaneous registrations without aborting either
        // transaction.
        entities.flush();
        return entities.createNativeQuery(
                                """
                INSERT INTO annual_enrollments(user_id,year,registered_by,registered_at,source)
                VALUES (:user,:year,:actor,:at,'INSCRIPCION')
                ON CONFLICT(user_id,year) DO NOTHING
                """)
                        .setParameter("user", user)
                        .setParameter("year", year)
                        .setParameter("actor", actor)
                        .setParameter("at", at)
                        .executeUpdate()
                > 0;
    }

    @Override
    public void preserveCommitments(UUID user, UUID actor, LocalDateTime at) {
        // One additive statement preserves future confirmed commitments without renewing other
        // years.
        // Keeping this insert atomic also preserves metadata on cycles already registered manually.
        entities.flush();
        entities.createNativeQuery(
                        """
                INSERT INTO annual_enrollments(user_id,year,registered_by,registered_at,source)
                SELECT DISTINCT e.volunteer_id,EXTRACT(YEAR FROM c.start_at)::int,
                       CAST(:actor AS uuid),CAST(:at AS timestamp),'GRADUACION'
                FROM enrollments e JOIN callouts c ON c.id=e.callout_id
                WHERE e.volunteer_id=:user AND e.status='CONFIRMADA' AND c.start_at>=:at
                ON CONFLICT(user_id,year) DO NOTHING
                """)
                .setParameter("user", user)
                .setParameter("actor", actor)
                .setParameter("at", at)
                .executeUpdate();
    }

    @Override
    public void graduation(
            UUID user,
            UUID actor,
            LocalDateTime at,
            GraduationEligibilityData data,
            String reason) {
        entities.persist(GraduationHistoryEntity.approved(user, actor, at, data, reason));
        entities.flush();
    }
}
