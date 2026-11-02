package pe.cruzroja.convocatorias.attendance.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttendanceTest {
    @Test void constructor_checkInLuegoDeTolerancia_estadoTarde() {
        var start = LocalDateTime.of(2026, 11, 14, 8, 0);
        var record = new AttendanceRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), start,
                start.plusMinutes(16), start.plusHours(4), UUID.randomUUID(), false);
        assertEquals(AttendanceStatus.TARDE, record.status());
    }

    @Test void hours_horarioValido_calculaCuatroHorasYMedia() {
        var in = LocalDateTime.of(2026, 11, 14, 8, 0);
        assertEquals(4.5, new ServiceHoursCalculator().calculate(in, in.plusMinutes(270)));
    }

    @Test void constructor_ausente_sumaCeroHoras() {
        var record = new AttendanceRecord(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                LocalDateTime.now(), null, null, UUID.randomUUID(), true);
        assertEquals(0, record.hours());
    }
}
