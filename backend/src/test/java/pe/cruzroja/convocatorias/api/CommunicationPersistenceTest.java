package pe.cruzroja.convocatorias.api;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.cruzroja.convocatorias.communications.domain.Announcement;
import pe.cruzroja.convocatorias.communications.domain.NoticeRepository;
import pe.cruzroja.convocatorias.communications.infrastructure.persistence.AnnouncementEntity;
import pe.cruzroja.convocatorias.communications.web.NotificationResponse;
import pe.cruzroja.convocatorias.shared.application.NotificationPublisher;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
@SpringBootTest(
        properties = {
            "spring.datasource.url=${TEST_DB_URL}",
            "spring.datasource.username=${TEST_DB_USER:convocatorias}",
            "spring.datasource.password=${TEST_DB_PASSWORD:convocatorias}"
        })
class CommunicationPersistenceTest {
    private static final UUID CHIEF = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    @Autowired NoticeRepository notices;
    @Autowired NotificationPublisher publisher;
    @Autowired PlatformTransactionManager transactions;
    @Autowired EntityManager entities;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate sql;

    @Test
    void announcementsPersistWithHibernateAndKeepTheirAudience() {
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            UUID id = UUID.randomUUID();
                            notices.save(
                                    id, new Announcement("Aviso", "Reunión", "VOLUNTARIO"), CHIEF);
                            entities.clear();

                            assertNotNull(entities.find(AnnouncementEntity.class, id));
                            assertTrue(notices.recipients("VOLUNTARIO").contains(USER));
                            assertFalse(notices.recipients("VOLUNTARIO").contains(CHIEF));
                            status.setRollbackOnly();
                        });
    }

    @Test
    void privateReadsAreIdempotentAndJsonRemainsCompatible() {
        new TransactionTemplate(transactions)
                .executeWithoutResult(
                        status -> {
                            String message = "ORM-" + UUID.randomUUID();
                            publisher.publish(USER, "PRUEBA", message, "/profile");
                            var notice =
                                    notices.notices(USER).stream()
                                            .filter(n -> n.message().equals(message))
                                            .findFirst()
                                            .orElseThrow();
                            LocalDateTime at = LocalDateTime.of(2030, 1, 1, 9, 0);

                            notices.read(notice.id(), CHIEF, at);
                            assertFalse(
                                    notices.notices(USER).stream()
                                            .filter(n -> n.id().equals(notice.id()))
                                            .findFirst()
                                            .orElseThrow()
                                            .read());
                            notices.read(notice.id(), USER, at);
                            notices.read(notice.id(), USER, at.plusDays(1));
                            var read =
                                    notices.notices(USER).stream()
                                            .filter(n -> n.id().equals(notice.id()))
                                            .findFirst()
                                            .orElseThrow();
                            assertEquals(at, read.readAt());
                            var response = json.valueToTree(NotificationResponse.from(read));
                            assertTrue(response.get("is_read").asBoolean());
                            assertEquals(USER.toString(), response.get("recipient_id").asText());
                            assertTrue(response.has("read_at"));
                            assertTrue(response.has("event_key"));
                            assertFalse(response.has("recipientId"));
                            status.setRollbackOnly();
                        });
    }

    @Test
    void noticeAndBusinessChangesRollbackTogether() {
        String message = "rollback-" + UUID.randomUUID();
        assertThrows(
                IllegalStateException.class,
                () ->
                        new TransactionTemplate(transactions)
                                .executeWithoutResult(
                                        status -> {
                                            publisher.publish(USER, "PRUEBA", message, null);
                                            throw new IllegalStateException("Simulación");
                                        }));
        assertEquals(
                0,
                sql.queryForObject(
                        "SELECT count(*) FROM notifications WHERE message=?",
                        Integer.class,
                        message));
    }

    @Test
    void concurrentUniqueDeliveryProducesOneNoticeWithoutPoisoningTransactions() throws Exception {
        String event = "contract-" + UUID.randomUUID();
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first =
                    executor.submit(
                            () -> publisher.publishOnce(USER, event, "PRUEBA", event, null));
            var second =
                    executor.submit(
                            () -> publisher.publishOnce(USER, event, "PRUEBA", event, null));
            first.get(15, TimeUnit.SECONDS);
            second.get(15, TimeUnit.SECONDS);
            assertEquals(
                    1,
                    sql.queryForObject(
                            "SELECT count(*) FROM notifications WHERE recipient_id=? AND event_key=?",
                            Integer.class,
                            USER,
                            event));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
            sql.update(
                    "DELETE FROM notifications WHERE recipient_id=? AND event_key=?", USER, event);
        }
    }
}
