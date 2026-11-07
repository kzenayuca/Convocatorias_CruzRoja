package pe.cruzroja.convocatorias.api;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import pe.cruzroja.convocatorias.operations.application.ServicesUseCases;
import pe.cruzroja.convocatorias.operations.domain.EnrollmentStatus;
import pe.cruzroja.convocatorias.operations.infrastructure.JpaCalloutRepository;
import pe.cruzroja.convocatorias.operations.infrastructure.JpaEnrollmentRepository;
import pe.cruzroja.convocatorias.operations.infrastructure.persistence.CalloutEntity;
import pe.cruzroja.convocatorias.operations.infrastructure.persistence.EnrollmentEntity;
import pe.cruzroja.convocatorias.shared.application.NotFound;

/** Real Hibernate mappings against PostgreSQL; every fixture rolls back independently. */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(
        properties = {
            "spring.datasource.url=${TEST_DB_URL}",
            "spring.datasource.username=${TEST_DB_USER:convocatorias}",
            "spring.datasource.password=${TEST_DB_PASSWORD:convocatorias}"
        })
@Transactional
class JpaPersistenceTest {
    private static final UUID CHIEF = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID VOLUNTEER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDateTime START = LocalDateTime.of(2040, 3, 1, 9, 0);

    @Autowired JpaCalloutRepository callouts;
    @Autowired JpaEnrollmentRepository enrollments;
    @Autowired EntityManager entities;
    @Autowired JdbcTemplate sql;

    @Test
    void ormCommandsAreVisibleToLegacyQueriesInTheSameTransaction() {
        UUID id = draft();

        assertNotNull(entities.find(CalloutEntity.class, id));
        assertEquals("BORRADOR", callouts.snapshot(id, false).get("status"));
        callouts.status(id, "ABIERTA");
        assertEquals(
                "ABIERTA",
                sql.queryForObject("SELECT status FROM callouts WHERE id=?", String.class, id));
    }

    @Test
    void stalePersistenceContextDoesNotOverwriteAnAuditedSqlChange() {
        UUID id = draft();
        entities.find(CalloutEntity.class, id);
        sql.update("UPDATE callouts SET observations='Historial conservado' WHERE id=?", id);

        callouts.status(id, "ABIERTA");

        assertEquals(
                "Historial conservado",
                sql.queryForObject(
                        "SELECT observations FROM callouts WHERE id=?", String.class, id));
    }

    @Test
    void enrollmentRoundTripsThroughHibernateAndCannotBeSilentlyReactivated() {
        UUID callout = draft();
        enrollments.save(null, VOLUNTEER, callout, "SOCORRISTA", null, null, START.minusDays(1));
        UUID id = (UUID) enrollments.byUser(callout, VOLUNTEER, false).get(0).get("id");
        entities.clear();

        assertEquals(
                EnrollmentStatus.CONFIRMADA,
                entities.find(EnrollmentEntity.class, id).toDomain().status());
        enrollments.cancelReplaced(id);
        assertEquals("CANCELADA", enrollments.find(id, false).get("status"));
        assertThrows(
                IllegalArgumentException.class,
                () -> enrollments.save(id, VOLUNTEER, callout, "SOCORRISTA", null, null, START));
    }

    @Test
    void unknownCommandTargetsUseTheSameNotFoundContract() {
        assertThrows(NotFound.class, () -> callouts.status(UUID.randomUUID(), "ABIERTA"));
    }

    private UUID draft() {
        UUID id = UUID.randomUUID();
        var request =
                new ServicesUseCases.Callout(
                        "Contrato ORM",
                        "Fixture aislado",
                        START,
                        START.plusHours(3),
                        START.minusDays(1),
                        "Arequipa",
                        "Uniforme",
                        2,
                        0,
                        0,
                        "SERVICIO",
                        "Voluntariado",
                        null,
                        "Apoyo",
                        0,
                        "I");
        callouts.create(id, request, CHIEF, "ORM-" + id);
        return id;
    }
}
