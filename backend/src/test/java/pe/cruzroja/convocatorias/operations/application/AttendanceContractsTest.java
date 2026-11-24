package pe.cruzroja.convocatorias.operations.application;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class AttendanceContractsTest {
    @Test
    void ledgerDependsOnlyOnTheClosureContract() {
        var constructor = MonthlyLedger.class.getConstructors()[0];
        assertEquals(
                List.of(MonthlyReportClosureRepository.class),
                List.of(constructor.getParameterTypes()));
    }

    @Test
    void reportContractExtendsTheClosureContract() {
        assertTrue(
                MonthlyReportClosureRepository.class.isAssignableFrom(
                        MonthlyReportRepository.class));
    }

    @Test
    void everyContractIsSmallerThanThe39MethodRepository() {
        for (Class<?> contract :
                List.of(
                        AttendanceRecordRepository.class,
                        AttendanceReviewRepository.class,
                        JustificationRepository.class,
                        MonthlyReportRepository.class,
                        AttendanceDirectoryRepository.class))
            assertTrue(contract.getMethods().length <= 14, contract.getSimpleName());
    }
}
