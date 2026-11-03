package pe.cruzroja.convocatorias.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ServiceRulesTest {
    @Test void hoursAreCalculatedWithoutDeductingBreaks() {
        var start = LocalDateTime.of(2026, 11, 14, 8, 0);
        assertEquals(6, ServiceRules.hours(start, start.plusHours(6)));
    }

    @Test void approvedJustificationRemovesDoubleDeduction() {
        assertEquals(12, ServiceRules.absenceDeduction(6, false));
        assertEquals(0, ServiceRules.absenceDeduction(6, true));
    }
}
