package pe.cruzroja.convocatorias.communications.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationTest {
    private final UUID recipient = UUID.randomUUID();
    private final LocalDateTime firstRead = LocalDateTime.of(2030, 3, 1, 9, 0);

    @Test
    void onlyTheRecipientCanMarkTheNoticeAsRead() {
        var notice = notice(false, null);
        assertThrows(
                IllegalArgumentException.class, () -> notice.readBy(UUID.randomUUID(), firstRead));
        assertFalse(notice.read());
    }

    @Test
    void readingTwicePreservesTheFirstInstantAndIdentity() {
        var original = notice(false, null);
        var read = original.readBy(recipient, firstRead);
        var repeated = read.readBy(recipient, firstRead.plusDays(1));

        assertTrue(read.read());
        assertEquals(firstRead, repeated.readAt());
        assertEquals(original, read);
        assertEquals(original.hashCode(), read.hashCode());
        assertFalse(original.read());
    }

    @Test
    void legacyReadWithoutTimestampGetsItsFirstKnownReadTime() {
        assertEquals(firstRead, notice(true, null).readBy(recipient, firstRead).readAt());
    }

    private Notification notice(boolean read, LocalDateTime at) {
        return new Notification(
                UUID.randomUUID(),
                recipient,
                "CUENTA",
                "Solicitud recibida",
                "/profile",
                read,
                at,
                null);
    }
}
