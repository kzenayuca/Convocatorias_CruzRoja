package pe.cruzroja.convocatorias.operations.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EnrollmentTest {
    private final UUID id = UUID.randomUUID();
    private final UUID volunteer = UUID.randomUUID();
    private final UUID callout = UUID.randomUUID();

    @Test
    void acceptanceCreatesAConfirmedCommitment() {
        var enrollment = Enrollment.accept(id, volunteer, callout, Position.SOCORRISTA);

        assertEquals(EnrollmentStatus.CONFIRMADA, enrollment.status());
        assertThrows(IllegalArgumentException.class, enrollment::requireNewRequest);
    }

    @Test
    void responsibilityChangesFunctionWithoutCreatingAnotherPerson() {
        var enrollment = Enrollment.accept(id, volunteer, callout, Position.GRADUADO);
        var responsible = enrollment.assignResponsible();
        var removed = responsible.removeResponsibility();

        assertEquals(id, responsible.id());
        assertEquals(volunteer, responsible.volunteerId());
        assertEquals(Position.RESPONSABLE, responsible.position());
        assertEquals(Position.SOCORRISTA, removed.position());
        assertEquals(EnrollmentStatus.CONFIRMADA, removed.status());
        assertEquals(Position.GRADUADO, enrollment.position());
    }

    @Test
    void replacementCancellationDoesNotMutateTheOriginalValue() {
        var enrollment = Enrollment.accept(id, volunteer, callout, Position.PILOTO);

        assertEquals(EnrollmentStatus.CANCELADA, enrollment.cancelForReplacement().status());
        assertEquals(EnrollmentStatus.CONFIRMADA, enrollment.status());
        assertThrows(IllegalArgumentException.class, () -> enrollment.assignResponsible());
    }

    @Test
    void cancelledCommitmentsCannotBeReusedOrCancelledAgain() {
        var cancelled =
                Enrollment.accept(id, volunteer, callout, Position.SOCORRISTA)
                        .cancelForReplacement();

        assertThrows(IllegalArgumentException.class, cancelled::requireNewRequest);
        assertThrows(IllegalArgumentException.class, cancelled::cancelForReplacement);
    }

    @Test
    void onlyHistoricalPendingAndExpiredRecordsCanBeRequestedAgain() {
        for (var status : EnrollmentStatus.values()) {
            var enrollment = new Enrollment(id, volunteer, callout, status, Position.SOCORRISTA);
            if (status == EnrollmentStatus.PENDIENTE || status == EnrollmentStatus.EXPIRADA) {
                assertDoesNotThrow(enrollment::requireNewRequest);
            } else {
                assertThrows(IllegalArgumentException.class, enrollment::requireNewRequest);
            }
        }
    }

    @Test
    void identityDoesNotDependOnMutableBusinessState() {
        var enrollment = Enrollment.accept(id, volunteer, callout, Position.SOCORRISTA);

        assertEquals(enrollment, enrollment.assignResponsible());
        assertEquals(enrollment.hashCode(), enrollment.assignResponsible().hashCode());
    }

    @Test
    void unknownPositionsAndReservedSelfEnrollmentAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Position.parse("ADMIN"));
        assertThrows(IllegalArgumentException.class, () -> Position.parse(null));
        assertThrows(
                IllegalArgumentException.class, () -> Position.RESPONSABLE.requireSelfEnrollment());
        assertThrows(
                IllegalArgumentException.class, () -> Position.FORMACION.requireSelfEnrollment());
    }

    @Test
    void capacitiesProtectTheirOwnBoundaries() {
        assertDoesNotThrow(() -> new TeamCapacity(10000, 10000, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new TeamCapacity(10001, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TeamCapacity(3, 0, 2, 0));
        assertThrows(IllegalArgumentException.class, () -> new TeamCapacity(3, 0, 0, 2));
    }
}
