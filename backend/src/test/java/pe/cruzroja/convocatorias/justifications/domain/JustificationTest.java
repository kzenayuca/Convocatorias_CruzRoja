package pe.cruzroja.convocatorias.justifications.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JustificationTest {
    @Test void constructor_fueraDe72Horas_lanzaExcepcion() {
        var absence = LocalDateTime.now().minusHours(73);
        assertThrows(IllegalArgumentException.class, () -> new Justification(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Salud", absence, LocalDateTime.now()));
    }

    @Test void reject_sinComentario_lanzaExcepcion() {
        var now = LocalDateTime.now();
        var justification = new Justification(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Salud", now, now);
        assertThrows(IllegalArgumentException.class, () -> justification.reject(""));
    }

    @Test void shouldSuspend_tresAusenciasEnNoventaDias_verdadero() {
        var now = LocalDateTime.now();
        assertTrue(new PenaltyPolicy().shouldSuspend(java.util.List.of(now.minusDays(1), now.minusDays(20), now.minusDays(89)), now));
    }
}
