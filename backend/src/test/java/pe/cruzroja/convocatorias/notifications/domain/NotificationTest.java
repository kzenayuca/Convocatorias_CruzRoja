package pe.cruzroja.convocatorias.notifications.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationTest {
    @Test void markRead_dosVeces_permaneceLeida() {
        var notification = new Notification(UUID.randomUUID(), UUID.randomUUID(), "INSCRIPCION", "Confirmada", "/enrollments");
        notification.markRead();
        notification.markRead();
        assertTrue(notification.read());
    }

    @Test void announcement_sinTitulo_lanzaExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> new Announcement(UUID.randomUUID(), "", "Mensaje",
                AnnouncementAudience.TODOS, UUID.randomUUID()));
    }
}
