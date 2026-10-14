package org.cruzroja.seguridad.dominio.entities;

import org.cruzroja.seguridad.dominio.enums.RolAcceso;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsuarioAccesoTest {
    @Test
    void reclutadorDebeSerGraduado() {
        assertThrows(IllegalArgumentException.class, () -> new UsuarioAcceso(
                "reclutador", "clave", "Persona",
                RolAcceso.RECLUTADOR, false));
    }

    @Test
    void aceptaGraduadoDesignadoComoReclutador() {
        UsuarioAcceso usuario = new UsuarioAcceso(
                "reclutador", "clave", "Elena",
                RolAcceso.RECLUTADOR, true);

        assertEquals(RolAcceso.RECLUTADOR, usuario.getRol());
        assertTrue(usuario.isGraduado());
    }
}
