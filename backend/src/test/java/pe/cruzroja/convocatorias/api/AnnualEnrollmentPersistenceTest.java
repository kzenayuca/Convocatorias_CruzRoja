package pe.cruzroja.convocatorias.api;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.cruzroja.convocatorias.membership.application.AnnualEnrollmentRepository;
import pe.cruzroja.convocatorias.membership.application.AnnualEnrollmentRepository.GraduationEligibilityData;
import pe.cruzroja.convocatorias.shared.application.NotificationPublisher;

/**
 * Real PostgreSQL contracts; fixtures roll back except the explicitly cleaned concurrency fixture.
 */
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(
        properties = {
            "spring.datasource.url=${TEST_DB_URL}",
            "spring.datasource.username=${TEST_DB_USER:convocatorias}",
            "spring.datasource.password=${TEST_DB_PASSWORD:convocatorias}"
        })
class AnnualEnrollmentPersistenceTest {
    private static final UUID CHIEF = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final LocalDateTime AT = LocalDateTime.of(2040, 3, 1, 9, 0);
    @Autowired AnnualEnrollmentRepository repository;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager entities;
    @Autowired JdbcTemplate sql;
    @Autowired ObjectMapper json;
    @Autowired NotificationPublisher notifications;

    @Test
    void registrationIsIdempotentAndPreservesMetadataAndResponseShape() {
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            UUID user = createVolunteer();
                            assertTrue(repository.register(user, 2040, CHIEF, AT));
                            assertTrue(repository.register(user, 2042, CHIEF, AT));
                            assertFalse(repository.register(user, 2040, user, AT.plusDays(1)));
                            entities.clear();

                            var cycles = repository.list(user);
                            assertEquals(
                                    List.of(2042, 2040),
                                    cycles.stream().map(row -> row.get("year")).toList());
                            var original = cycles.get(1);
                            assertEquals(CHIEF, original.get("registered_by"));
                            assertEquals(AT, original.get("registered_at"));
                            assertEquals("INSCRIPCION", original.get("source"));
                            assertEquals(
                                    Set.of("year", "registered_by", "registered_at", "source"),
                                    original.keySet());
                            var response = json.valueToTree(original);
                            assertEquals(CHIEF.toString(), response.get("registered_by").asText());
                            assertTrue(response.has("registered_at"));
                            status.setRollbackOnly();
                        });
    }

    @Test
    void legacyCyclesWithoutAnActorRemainReadableAndListsArePersonal() {
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            UUID user = createVolunteer();
                            UUID other = createVolunteer();
                            sql.update(
                                    "INSERT INTO annual_enrollments(user_id,year,registered_at,source) VALUES (?,2040,?,'MIGRACION')",
                                    user,
                                    AT);
                            repository.register(other, 2042, CHIEF, AT);

                            var cycles = repository.list(user);
                            assertEquals(1, cycles.size());
                            assertEquals(2040, cycles.get(0).get("year"));
                            assertEquals("MIGRACION", cycles.get(0).get("source"));
                            assertTrue(cycles.get(0).containsKey("registered_by"));
                            assertNull(cycles.get(0).get("registered_by"));
                            assertTrue(repository.list(UUID.randomUUID()).isEmpty());
                            status.setRollbackOnly();
                        });
    }

    @Test
    void graduationPreservesOnlyFutureConfirmedCommitmentsWithoutRewritingExistingCycles() {
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            UUID user = createVolunteer();
                            UUID other = createVolunteer();
                            repository.register(user, 2042, user, AT.minusDays(1));
                            commitment(user, AT, "CONFIRMADA");
                            commitment(user, AT.plusYears(2), "CONFIRMADA");
                            commitment(user, AT.plusYears(2).plusDays(1), "CONFIRMADA");
                            commitment(user, AT.minusYears(1), "CONFIRMADA");
                            commitment(user, AT.plusYears(3), "CANCELADA");
                            commitment(user, AT.plusYears(4), "PENDIENTE");
                            commitment(other, AT.plusYears(5), "CONFIRMADA");

                            repository.preserveCommitments(user, CHIEF, AT);
                            repository.preserveCommitments(user, CHIEF, AT.plusSeconds(1));
                            entities.clear();

                            var cycles = repository.list(user);
                            assertEquals(
                                    List.of(2042, 2040),
                                    cycles.stream().map(row -> row.get("year")).toList());
                            assertEquals("INSCRIPCION", cycles.get(0).get("source"));
                            assertEquals(user, cycles.get(0).get("registered_by"));
                            assertEquals(AT.minusDays(1), cycles.get(0).get("registered_at"));
                            assertEquals("GRADUACION", cycles.get(1).get("source"));
                            assertEquals(CHIEF, cycles.get(1).get("registered_by"));
                            assertEquals(AT, cycles.get(1).get("registered_at"));
                            assertTrue(repository.list(other).isEmpty());
                            status.setRollbackOnly();
                        });
    }

    @Test
    void graduationHistoryAppendsApprovalsWithTheirOriginalBalanceAndReason() {
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            UUID user = createVolunteer();
                            repository.graduation(
                                    user,
                                    CHIEF,
                                    AT,
                                    new GraduationEligibilityData(150.125, 150),
                                    "Saldo revisado");
                            repository.graduation(
                                    user,
                                    CHIEF,
                                    AT.plusDays(1),
                                    new GraduationEligibilityData(160, 155),
                                    "Nueva revisión");
                            entities.clear();

                            var history =
                                    sql.queryForList(
                                            "SELECT * FROM graduation_history WHERE user_id=? ORDER BY approved_at",
                                            user);
                            assertEquals(2, history.size());
                            var original = history.get(0);
                            assertEquals(CHIEF, original.get("approved_by"));
                            assertEquals(
                                    AT,
                                    ((java.sql.Timestamp) original.get("approved_at"))
                                            .toLocalDateTime());
                            assertEquals(new BigDecimal("150.1250"), original.get("net_hours"));
                            assertEquals(
                                    new BigDecimal("150.0000"), original.get("required_hours"));
                            assertEquals("Saldo revisado", original.get("reason"));
                            assertNotEquals(original.get("id"), history.get(1).get("id"));
                            status.setRollbackOnly();
                        });
    }

    @Test
    void cyclesHistoryAndNotificationsRollbackTogether() {
        UUID user = UUID.randomUUID();
        assertThrows(
                IllegalStateException.class,
                () ->
                        new TransactionTemplate(transactions)
                                .executeWithoutResult(
                                        status -> {
                                            createVolunteer(user);
                                            commitment(user, AT.plusYears(1), "CONFIRMADA");
                                            repository.register(user, 2040, CHIEF, AT);
                                            repository.graduation(
                                                    user,
                                                    CHIEF,
                                                    AT,
                                                    new GraduationEligibilityData(150, 150),
                                                    "Revisión");
                                            repository.preserveCommitments(user, CHIEF, AT);
                                            notifications.publish(
                                                    user,
                                                    "CUENTA",
                                                    "Graduación aprobada",
                                                    "/profile");
                                            assertEquals(2, repository.list(user).size());
                                            throw new IllegalStateException(
                                                    "Simulación de fallo posterior");
                                        }));

        assertTrue(repository.list(user).isEmpty());
        assertEquals(
                0,
                sql.queryForObject(
                        "SELECT count(*) FROM graduation_history WHERE user_id=?",
                        Integer.class,
                        user));
        assertEquals(
                0,
                sql.queryForObject(
                        "SELECT count(*) FROM notifications WHERE recipient_id=?",
                        Integer.class,
                        user));
        assertEquals(
                0,
                sql.queryForObject("SELECT count(*) FROM users WHERE id=?", Integer.class, user));
    }

    @Test
    void simultaneousRegistrationCreatesOneCycleAndBothTransactionsRemainUsable() throws Exception {
        UUID user = createVolunteer();
        var executor = Executors.newFixedThreadPool(2);
        var start = new CyclicBarrier(2);
        Callable<Boolean> register =
                () -> {
                    start.await(15, TimeUnit.SECONDS);
                    return new TransactionTemplate(transactions)
                            .execute(
                                    status -> {
                                        boolean created =
                                                repository.register(user, 2040, CHIEF, AT);
                                        assertEquals(1, repository.list(user).size());
                                        return created;
                                    });
                };
        try {
            var first = executor.submit(register);
            var second = executor.submit(register);
            assertNotEquals(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            assertEquals(1, repository.list(user).size());
        } finally {
            executor.shutdownNow();
            boolean stopped = executor.awaitTermination(30, TimeUnit.SECONDS);
            sql.update("DELETE FROM annual_enrollments WHERE user_id=?", user);
            sql.update("DELETE FROM users WHERE id=?", user);
            assertTrue(stopped);
        }
    }

    private UUID createVolunteer() {
        UUID user = UUID.randomUUID();
        createVolunteer(user);
        return user;
    }

    private void createVolunteer(UUID user) {
        sql.update(
                """
                INSERT INTO users(id,name,email,password_hash,role,graduated,status)
                SELECT ?,'Ciclo ORM',?,password_hash,'VOLUNTARIO',true,'ACTIVO' FROM users WHERE id=?
                """,
                user,
                "cycle-" + user + "@test.invalid",
                CHIEF);
    }

    private void commitment(UUID user, LocalDateTime start, String status) {
        UUID callout = UUID.randomUUID();
        sql.update(
                """
                INSERT INTO callouts(id,title,start_at,end_at,minimum_level,capacity,minimum_participants,status)
                VALUES (?,'Compromiso ORM',?,?,'BASICO',1,1,'ABIERTA')
                """,
                callout,
                start,
                start.plusHours(2));
        sql.update(
                "INSERT INTO enrollments(id,volunteer_id,callout_id,status) VALUES (?,?,?,?)",
                UUID.randomUUID(),
                user,
                callout,
                status);
    }
}
