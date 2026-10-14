package org.cruzroja.convocatorias.dominio.entities;

import org.cruzroja.convocatorias.dominio.enums.EstadoConvocatoria;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.cruzroja.convocatorias.dominio.valueobjects.Horario;
import org.cruzroja.convocatorias.dominio.valueobjects.Ubicacion;
import java.time.LocalDate;
import java.util.List;

class ConvocatoriaTest {
    @Test
    void muestraCuantosParticipantesFaltan() {
        Convocatoria convocatoria = new Convocatoria("medica", "Campaña Médica", 3);

        assertEquals(EstadoConvocatoria.MINIMO_NO_ALCANZADO, convocatoria.estadoPara(2));
        assertEquals(1, convocatoria.faltantesPara(2));
    }

    @Test
    void alcanzaElMinimo() {
        Convocatoria convocatoria = new Convocatoria("medica", "Campaña Médica", 3);

        assertEquals(EstadoConvocatoria.MINIMO_ALCANZADO, convocatoria.estadoPara(3));
        assertEquals(0, convocatoria.faltantesPara(3));
    }

    @Test
    void impideConfirmarAntesDeAlcanzarElMinimo() {
        Convocatoria convocatoria = new Convocatoria("medica", "Campaña Médica", 3);

        assertThrows(IllegalStateException.class, () -> convocatoria.confirmar(2));
    }

    @Test
    void editarActualizaTodosLosDatosYReabreLaConfirmacion() {
        Convocatoria convocatoria = new Convocatoria("medica", "Campaña Médica", 1);
        convocatoria.confirmar(1);

        convocatoria.editar(
                "Campaña actualizada", LocalDate.parse("2026-09-21"),
                new Ubicacion("Cayma"), Horario.desdeTexto("08:30", "14:00"),
                List.of("RCP", "Enfermería"), 3);

        assertEquals("Campaña actualizada", convocatoria.getNombre());
        assertEquals("Cayma", convocatoria.getUbicacion().lugar());
        assertEquals(3, convocatoria.getMinimoParticipantes());
        assertEquals(List.of("RCP", "Enfermería"), convocatoria.getRequisitos());
        assertFalse(convocatoria.isConfirmada());
    }

    @Test
    void editarRechazaRequisitosVacios() {
        Convocatoria convocatoria = new Convocatoria("medica", "Campaña Médica", 1);
        assertThrows(IllegalArgumentException.class, () -> convocatoria.editar(
                "Campaña", LocalDate.now(), new Ubicacion("Arequipa"),
                Horario.desdeTexto("08:00", "10:00"), List.of(), 2));
    }
}
