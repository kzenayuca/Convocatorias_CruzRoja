package pe.cruzroja.convocatorias.operations.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReplacementRulesTest {
    final LocalDateTime start=LocalDateTime.of(2030,1,5,8,0);
    final UUID user=UUID.randomUUID(),replacement=UUID.randomUUID();
    @Test void requestIncludesFullNameAndSustainingReason() {
        assertThrows(IllegalArgumentException.class,()->ReplacementRequest.submit("", "Ana Torres",start,start.minusDays(3)));
        assertThrows(IllegalArgumentException.class,()->ReplacementRequest.submit("Motivo", " ",start,start.minusDays(3)));
        assertDoesNotThrow(()->ReplacementRequest.submit("Motivo documentado","Ana Torres",start,start.minusDays(3)));
    }
    @Test void exact48HoursAreAllowedButNotOneSecondLater() {
        assertDoesNotThrow(()->ReplacementRequest.submit("Motivo","Ana Torres",start,start.minusHours(48)));
        assertThrows(IllegalArgumentException.class,()->ReplacementRequest.submit("Motivo","Ana Torres",start,start.minusHours(48).plusSeconds(1)));
    }
    @Test void approvalAlwaysNeedsAnotherPersonAndVerifiedRequirements() {
        assertThrows(IllegalArgumentException.class,()->ReplacementRequest.approve(user,null,true,start,start.minusDays(1)));
        assertThrows(IllegalArgumentException.class,()->ReplacementRequest.approve(user,user,true,start,start.minusDays(1)));
        assertThrows(IllegalArgumentException.class,()->ReplacementRequest.approve(user,replacement,false,start,start.minusDays(1)));
        assertDoesNotThrow(()->ReplacementRequest.approve(user,replacement,true,start,start.minusSeconds(1)));
        assertThrows(IllegalArgumentException.class,()->ReplacementRequest.approve(user,replacement,true,start,start));
    }
}
