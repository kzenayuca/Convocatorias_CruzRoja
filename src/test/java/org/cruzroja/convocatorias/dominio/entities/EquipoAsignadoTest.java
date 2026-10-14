package org.cruzroja.convocatorias.dominio.entities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EquipoAsignadoTest {
    @Test
    void conservaPerfilEspecialidadYParticipacion() {
        EquipoAsignado integrante = new EquipoAsignado(
                1, "donacion", "Rosa Mendoza",
                EquipoAsignado.Perfil.GRADUADO, "Enfermería",
                EquipoAsignado.EstadoParticipacion.ACTIVO, null);

        assertEquals(EquipoAsignado.Perfil.GRADUADO, integrante.getPerfil());
        assertEquals("Enfermería", integrante.getHabilidadEspecialidad());
        assertEquals(EquipoAsignado.EstadoParticipacion.ACTIVO, integrante.getEstado());
    }

    @Test
    void unGraduadoNoPuedeTenerMentor() {
        assertThrows(IllegalArgumentException.class, () -> new EquipoAsignado(
                1, "donacion", "Rosa Mendoza",
                EquipoAsignado.Perfil.GRADUADO, "Enfermería",
                EquipoAsignado.EstadoParticipacion.ACTIVO, 2L));
    }
}
