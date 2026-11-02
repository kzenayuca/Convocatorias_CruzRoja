package pe.cruzroja.convocatorias.enrollments.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import pe.cruzroja.convocatorias.callouts.domain.CalloutStatus;

class EnrollmentPolicyTest {
    private final EnrollmentPolicy policy = new EnrollmentPolicy();

    @Test void validate_convocatoriaBorrador_rechazaInscripcion() {
        assertThrows(IllegalStateException.class, () -> policy.validate(CalloutStatus.BORRADOR, true, true, false, false));
    }

    @Test void validate_nivelIncompatible_rechazaInscripcion() {
        assertThrows(IllegalStateException.class, () -> policy.validate(CalloutStatus.PUBLICADA, false, true, false, false));
    }

    @Test void validate_sinCupo_rechazaInscripcion() {
        assertThrows(IllegalStateException.class, () -> policy.validate(CalloutStatus.PUBLICADA, true, false, false, false));
    }

    @Test void validate_duplicada_rechazaInscripcion() {
        assertThrows(IllegalStateException.class, () -> policy.validate(CalloutStatus.PUBLICADA, true, true, true, false));
    }

    @Test void validate_suspendido_rechazaInscripcion() {
        assertThrows(IllegalStateException.class, () -> policy.validate(CalloutStatus.PUBLICADA, true, true, false, true));
    }
}
