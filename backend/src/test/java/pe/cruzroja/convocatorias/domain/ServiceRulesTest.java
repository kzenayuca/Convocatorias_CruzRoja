package pe.cruzroja.convocatorias.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class ServiceRulesTest {
    private final LocalDateTime start=LocalDateTime.of(2026,11,14,14,0);

    @Test void marksAreClippedToScheduledTime() {
        assertEquals(1,ServiceRules.recognizedHours(start,start.plusHours(1),start.minusMinutes(10),start.plusMinutes(70)));
        assertEquals(.5,ServiceRules.recognizedHours(start,start.plusHours(1),start.plusMinutes(30),start.plusHours(1)));
        assertEquals(.25,ServiceRules.recognizedHours(start,start.plusHours(1),start.plusMinutes(30),start.plusMinutes(45)));
        assertEquals(0,ServiceRules.recognizedHours(start,start.plusHours(1),start.plusHours(2),start.plusHours(3)));
        assertEquals(0,ServiceRules.recognizedHours(start,start.plusHours(1),start,null));
    }
    @Test void justificationDeadlineStartsAtServiceEnd() {
        var end=start.plusHours(3);
        assertFalse(ServiceRules.isLateJustification(end,end.plusHours(48)));
        assertTrue(ServiceRules.isLateJustification(end,end.plusHours(48).plusSeconds(1)));
        assertTrue(ServiceRules.canJustify(start,end,end.plusHours(48),start.minusMinutes(1),true));
        assertFalse(ServiceRules.canJustify(start,end,end.plusHours(1),start.plusMinutes(1),true));
    }
    @Test void reviewDeadlineSkipsWeekends() {
        var friday=LocalDateTime.of(2026,11,6,10,0);
        assertEquals(friday.plusDays(7),ServiceRules.reviewDeadline(friday));
    }
    @Test void balancesCanBeNegativeAndApprovalDoesNotAwardUnworkedHours() {
        assertEquals(-6,0-ServiceRules.absenceDeduction(3,false));
        assertEquals(18,24-ServiceRules.absenceDeduction(3,false));
        assertEquals(24,24-ServiceRules.absenceDeduction(3,true));
    }
    @Test void hoursAreCalculatedWithoutDeductingBreaks() {
        var start = LocalDateTime.of(2026, 11, 14, 8, 0);
        assertEquals(6, ServiceRules.hours(start, start.plusHours(6)));
    }

    @Test void approvedJustificationRemovesDoubleDeduction() {
        assertEquals(12, ServiceRules.absenceDeduction(6, false));
        assertEquals(0, ServiceRules.absenceDeduction(6, true));
    }
}
