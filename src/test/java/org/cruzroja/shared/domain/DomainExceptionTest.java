package org.cruzroja.shared.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class DomainExceptionTest {
    @Test
    void conservaElMensajeYLaCausa() {
        IllegalStateException causa = new IllegalStateException("regla incumplida");

        DomainException excepcion = new DomainException("Operacion invalida", causa);

        assertEquals("Operacion invalida", excepcion.getMessage());
        assertSame(causa, excepcion.getCause());
    }
}
