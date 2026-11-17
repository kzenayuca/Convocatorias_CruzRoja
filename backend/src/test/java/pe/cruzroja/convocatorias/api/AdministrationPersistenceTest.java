package pe.cruzroja.convocatorias.api;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.cruzroja.convocatorias.administration.domain.AdministrationRepository;
import pe.cruzroja.convocatorias.administration.domain.HourTargets;
import pe.cruzroja.convocatorias.operations.application.GraduationSettings;
import pe.cruzroja.convocatorias.shared.application.NotFound;

/** ORM contract against an exclusive PostgreSQL database; each fixture rolls back. */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(
        properties = {
            "spring.datasource.url=${TEST_DB_URL}",
            "spring.datasource.username=${TEST_DB_USER:convocatorias}",
            "spring.datasource.password=${TEST_DB_PASSWORD:convocatorias}"
        })
class AdministrationPersistenceTest {
    private static final UUID CHIEF = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final LocalDateTime AT = LocalDateTime.of(2040, 3, 1, 9, 0);

    @Autowired AdministrationRepository repository;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager entities;
    @Autowired JdbcTemplate sql;
    @Autowired ObjectMapper json;
    @Autowired GraduationSettings graduationSettings;

    @Test
    void graduationSeesNewTargetsEvenAfterReadingThePreviousConfiguration() {
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            graduationSettings.requiredHours(false);
                            graduationSettings.requiredHours(true);
                            repository.save(
                                    new HourTargets(
                                            new BigDecimal("151.25"), new BigDecimal("211.75")),
                                    CHIEF,
                                    AT);

                            assertEquals(151.25, graduationSettings.requiredHours(false));
                            assertEquals(211.75, graduationSettings.requiredHours(true));
                            status.setRollbackOnly();
                        });
    }

    @Test
    void missingGraduationTargetPreservesTheDataAccessErrorContract() {
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            sql.update(
                                    "DELETE FROM volunteer_hour_targets WHERE condition='FORMACION'");

                            assertThrows(
                                    EmptyResultDataAccessException.class,
                                    () -> graduationSettings.requiredHours(false));
                            status.setRollbackOnly();
                        });
    }

    @Test
    void ormRoundTripPreservesDecimalHoursAuditAndJsonFields() {
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            repository.save(
                                    new HourTargets(
                                            new BigDecimal("150.25"), new BigDecimal("210.75")),
                                    CHIEF,
                                    AT);
                            entities.clear();

                            var settings = repository.settings();
                            assertEquals(new BigDecimal("150.25"), settings.get("training_hours"));
                            assertEquals(new BigDecimal("210.75"), settings.get("graduate_hours"));
                            assertEquals(AT, settings.get("updated_at"));
                            var response = json.valueToTree(settings);
                            assertEquals(3, response.size());
                            assertEquals(150.25, response.get("training_hours").asDouble());
                            assertEquals(210.75, response.get("graduate_hours").asDouble());
                            assertTrue(response.has("updated_at"));
                            assertEquals(
                                    2,
                                    sql.queryForObject(
                                            "SELECT count(*) FROM volunteer_hour_targets WHERE updated_by=? AND updated_at=?",
                                            Integer.class,
                                            CHIEF,
                                            AT));
                            status.setRollbackOnly();
                        });
    }

    @Test
    void emptyConfigurationKeepsAllNullableResponseFields() {
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            sql.update("DELETE FROM volunteer_hour_targets");
                            entities.clear();

                            var settings = repository.settings();
                            assertEquals(3, settings.size());
                            assertNull(settings.get("training_hours"));
                            assertNull(settings.get("graduate_hours"));
                            assertNull(settings.get("updated_at"));
                            status.setRollbackOnly();
                        });
    }

    @Test
    void latestTimestampIsReturnedRegardlessOfWhichTargetWasUpdated() {
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            sql.update(
                                    "UPDATE volunteer_hour_targets SET updated_at=? WHERE condition='FORMACION'",
                                    AT);
                            sql.update(
                                    "UPDATE volunteer_hour_targets SET updated_at=? WHERE condition='GRADUADO'",
                                    AT.minusDays(1));
                            entities.clear();

                            assertEquals(AT, repository.settings().get("updated_at"));
                            status.setRollbackOnly();
                        });
    }

    @Test
    void failureOnSecondTargetRollsBackTheFirstUpdate() {
        var transaction = new TransactionTemplate(transactions);
        Map<String, Object> before = repository.settings();
        try {
            assertThrows(
                    NotFound.class,
                    () ->
                            transaction.executeWithoutResult(
                                    status -> {
                                        sql.update(
                                                "DELETE FROM volunteer_hour_targets WHERE condition='GRADUADO'");
                                        repository.save(
                                                new HourTargets(
                                                        new BigDecimal("111.25"),
                                                        new BigDecimal("222.50")),
                                                CHIEF,
                                                AT);
                                    }));
        } finally {
            assertEquals(before, repository.settings());
        }
    }
}
