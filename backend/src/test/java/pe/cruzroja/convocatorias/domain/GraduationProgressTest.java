package pe.cruzroja.convocatorias.domain;
import pe.cruzroja.convocatorias.operations.domain.GraduationProgress;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GraduationProgressTest {
    @Test void debtIsNotProgress() {
        var p=GraduationProgress.of(4,10,100.0,2);
        assertEquals(-6,p.balance()); assertEquals(6,p.debt()); assertEquals(106,p.remaining()); assertEquals(2,p.pending());
    }
    @Test void targetIsNotAssumed() {
        var p=GraduationProgress.of(10,0,null,0); assertNull(p.required()); assertNull(p.remaining());
    }
    @Test void completedTargetNeverHasNegativeRemaining() {
        assertEquals(0,GraduationProgress.of(120,10,100.0,0).remaining());
    }
    @Test void invalidTotalsAndTargetsAreRejected() {
        assertThrows(IllegalArgumentException.class,() -> GraduationProgress.of(-1,0,100.0,0));
        assertThrows(IllegalArgumentException.class,() -> GraduationProgress.of(1,Double.NaN,100.0,0));
        assertThrows(IllegalArgumentException.class,() -> GraduationProgress.of(1,0,0.0,0));
        assertThrows(IllegalArgumentException.class,() -> GraduationProgress.of(1,0,100.0,-1));
    }
}
