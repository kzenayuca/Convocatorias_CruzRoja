package pe.cruzroja.convocatorias.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TeamCapacityTest {
    @Test void fourPeopleIncludeTheSpecialistsAndTheGraduateMinimum() {
        var limits=new TeamCapacity(4,1,1,1);
        assertTrue(limits.complete(4,3,1,1));
        assertFalse(limits.complete(3,3,1,1));
        assertFalse(limits.fits(5,4,1,1));
    }
    @Test void generalApplicantsCannotConsumeSpaceReservedForSpecialists() {
        var limits=new TeamCapacity(4,1,1,1);
        assertTrue(limits.fits(2,0,0,0));
        assertFalse(limits.fits(3,1,0,0));
        assertFalse(limits.complete(4,4,0,1));
    }
    @Test void aVolunteerCanBeInTrainingOrGraduatedButARequiredGraduateCannotBeReplacedByATrainee() {
        assertTrue(new TeamCapacity(1,0,0,0).fits(1,0,0,0));
        assertTrue(new TeamCapacity(1,0,0,0).fits(1,1,0,0));
        assertFalse(new TeamCapacity(1,1,0,0).fits(1,0,0,0));
        assertTrue(new TeamCapacity(1,1,0,0).complete(1,1,0,0));
    }
    @Test void inconsistentRequirementsAreRejected() {
        assertThrows(IllegalArgumentException.class,() -> new TeamCapacity(0,0,0,0));
        assertThrows(IllegalArgumentException.class,() -> new TeamCapacity(1,2,0,0));
        assertThrows(IllegalArgumentException.class,() -> new TeamCapacity(1,0,1,1));
    }
    @Test void roleAndGraduationReservationsCanBeCoveredByTheSamePeople() {
        var limits=new TeamCapacity(4,3,1,1);
        assertTrue(limits.fits(2,1,0,0));
        assertTrue(limits.complete(4,3,1,1));
        assertFalse(limits.fits(2,0,0,0));
    }
}
