package org.cruzroja.convocatorias.dominio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.Arrays;
import org.cruzroja.convocatorias.dominio.entities.Convocatoria;
import org.cruzroja.convocatorias.dominio.entities.EquipoAsignado;
import org.cruzroja.convocatorias.dominio.events.ConvocatoriaPublicadaEvent;
import org.cruzroja.convocatorias.dominio.events.EquipoAsignadoEvent;
import org.cruzroja.convocatorias.dominio.valueobjects.Horario;
import org.cruzroja.convocatorias.dominio.valueobjects.Ubicacion;
import org.cruzroja.shared.domain.AggregateRoot;
import org.cruzroja.shared.domain.DomainEvent;
import org.cruzroja.shared.domain.ValueObject;
import org.junit.jupiter.api.Test;

class ConvocatoriasDomainRefactorTest {
    @Test
    void lasEntidadesPrincipalesSonRaicesDeAgregado() {
        assertTrue(new Convocatoria("donacion", "Campaña", 2) instanceof AggregateRoot);
        assertTrue(new EquipoAsignado(1, "donacion", "Rosa", EquipoAsignado.Perfil.GRADUADO,
                "Enfermería", EquipoAsignado.EstadoParticipacion.ACTIVO, null)
                instanceof AggregateRoot);
    }

    @Test
    void horarioYUbicacionSonValueObjectsInmutables() {
        assertTrue(Horario.desdeTexto("08:00", "14:00") instanceof ValueObject);
        assertTrue(new Ubicacion("Cercado") instanceof ValueObject);
        assertTrue(Modifier.isFinal(Horario.class.getModifiers()));
        assertTrue(Arrays.stream(Horario.class.getDeclaredFields())
                .allMatch(campo -> Modifier.isFinal(campo.getModifiers())));
        assertTrue(Ubicacion.class.isRecord());
    }

    @Test
    void losValueObjectsSeComparanPorValor() {
        assertEquals(Horario.desdeTexto("08:00", "14:00"), Horario.desdeTexto("08:00", "14:00"));
        assertEquals(Horario.desdeTexto("08:00", "14:00").hashCode(),
                Horario.desdeTexto("08:00", "14:00").hashCode());
        assertNotEquals(Horario.desdeTexto("08:00", "14:00"), Horario.desdeTexto("08:00", "15:00"));
        assertEquals(new Ubicacion(" Camaná "), new Ubicacion("Camaná"));
    }

    @Test
    void losEventosDeConvocatoriasSonEventosDeDominio() {
        ConvocatoriaPublicadaEvent publicada = new ConvocatoriaPublicadaEvent(
                "donacion", "Campaña", LocalDate.parse("2026-07-25"), 3);
        EquipoAsignadoEvent asignado = new EquipoAsignadoEvent(
                1, "donacion", "Rosa Mendoza", "GRADUADO", null);

        assertTrue(publicada instanceof DomainEvent);
        assertTrue(asignado instanceof DomainEvent);
        assertEquals("donacion", publicada.codigoConvocatoria());
        assertNotNull(publicada.ocurridoEn());
        assertEquals("Rosa Mendoza", asignado.nombreVoluntario());
        assertNotNull(asignado.ocurridoEn());
    }
}
