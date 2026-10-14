package org.cruzroja.convocatorias.dominio.valueobjects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HorarioTest {
    @Test
    void calculaDuracionDeSeisHoras() {
        Horario horario = Horario.desdeTexto("08:00", "14:00");
        assertEquals("6 h", horario.duracionLegible());
    }

    @Test
    void calculaDuracionConMinutos() {
        Horario horario = Horario.desdeTexto("07:30", "17:00");
        assertEquals("9 h 30 min", horario.duracionLegible());
    }

    @Test
    void rechazaHoraFinalAnterior() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> Horario.desdeTexto("14:00", "08:00"));
        assertEquals(
                "La hora de finalización debe ser posterior a la hora de inicio",
                error.getMessage());
    }

    @Test
    void rechazaHorasIguales() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Horario.desdeTexto("08:00", "08:00"));
    }
}
