package pe.cruzroja.convocatorias.operations.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;
import pe.cruzroja.convocatorias.membership.domain.AnnualEnrollment;
import pe.cruzroja.convocatorias.shared.application.GraduationEligibility;

class ServiceCycleTest {
    @Test
    void codeUsesCreationMonthButSequenceDoesNotRestartInMarch() {
        assertEquals(
                "I-FEB-2027-024",
                new ServiceCode("I", LocalDateTime.of(2027, 2, 28, 23, 59), 24).value());
        assertEquals(
                "I-MAR-2027-025",
                new ServiceCode("I", LocalDateTime.of(2027, 3, 1, 0, 0), 25).value());
        assertEquals(
                "G-ENE-2028-001",
                new ServiceCode("G", LocalDateTime.of(2028, 1, 1, 0, 0), 1).value());
        assertThrows(
                IllegalArgumentException.class, () -> new ServiceCode("X", LocalDateTime.now(), 1));
    }

    @Test
    void annualCycleAlwaysRunsFromJanuaryThroughDecember() {
        var cycle = new AnnualEnrollment(2027);
        assertEquals(LocalDate.of(2027, 1, 1), cycle.startsOn());
        assertEquals(LocalDate.of(2027, 12, 31), cycle.endsOn());
        assertThrows(IllegalArgumentException.class, () -> new AnnualEnrollment(0));
    }

    @Test
    void graduationDependsOnNetBalanceNotGrossHoursOrCalendarYear() {
        assertFalse(new GraduationEligibility.Assessment(199.9999, 200.0).eligible());
        assertTrue(new GraduationEligibility.Assessment(200, 200.0).eligible());
        assertFalse(new GraduationEligibility.Assessment(300, null).eligible());
        assertEquals(200, GraduationProgress.of(240, 40, 200.0, 0).balance());
    }
}
