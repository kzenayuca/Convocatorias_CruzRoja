package pe.cruzroja.convocatorias.operations.application;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import pe.cruzroja.convocatorias.operations.domain.EnrollmentRepository;

class LockingContractTest {
    private static final Set<String> LOCKABLE =
            Set.of("find", "review", "justification", "byUser", "snapshot");

    private static boolean isApprovalFlag(Class<?> repository, String method) {
        return repository == EnrollmentRepository.class && method.equals("review");
    }

    @Test
    void lockingIsExpressedByMethodNameNotByBooleanFlag() {
        for (Class<?> repository :
                List.of(
                        AttendanceRecordRepository.class,
                        AttendanceReviewRepository.class,
                        JustificationRepository.class,
                        EnrollmentRepository.class,
                        MaterialRepository.class,
                        CalloutRepository.class))
            for (var method : repository.getMethods())
                if (LOCKABLE.contains(method.getName())
                        && !isApprovalFlag(repository, method.getName()))
                    assertFalse(
                            Arrays.asList(method.getParameterTypes()).contains(boolean.class),
                            repository.getSimpleName() + "." + method.getName());
    }

    @Test
    void everyLockableReadHasAnExplicitForUpdateVariant() throws Exception {
        assertNotNull(AttendanceRecordRepository.class.getMethod("findForUpdate", UUID.class));
        assertNotNull(AttendanceReviewRepository.class.getMethod("reviewForUpdate", UUID.class));
        assertNotNull(
                JustificationRepository.class.getMethod("justificationForUpdate", UUID.class));
        assertNotNull(EnrollmentRepository.class.getMethod("findForUpdate", UUID.class));
        assertNotNull(
                EnrollmentRepository.class.getMethod("byUserForUpdate", UUID.class, UUID.class));
        assertNotNull(MaterialRepository.class.getMethod("findForUpdate", UUID.class));
        assertNotNull(CalloutRepository.class.getMethod("snapshotForUpdate", UUID.class));
    }
}
