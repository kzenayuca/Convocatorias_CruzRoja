package org.cruzroja.usuarios.dominio.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.cruzroja.shared.domain.AggregateRoot;
import org.cruzroja.usuarios.dominio.enums.EstadoPostulacion;
import org.cruzroja.usuarios.dominio.enums.TipoVoluntario;
import org.cruzroja.usuarios.dominio.events.VoluntarioRegistradoEvent;
import org.cruzroja.usuarios.dominio.valueobjects.Perfil;
import org.junit.jupiter.api.Test;

class UsuariosDomainRefactorTest {
    @Test
    void postulacionYVoluntarioSonAggregatesDelDominio() {
        Perfil perfil = new Perfil(TipoVoluntario.GRADUADO, "Primeros auxilios", List.of("RCP"));
        Voluntario voluntario = new Voluntario("12345678", "Ana Torres", perfil, "Fines de semana");
        Postulacion postulacion = new Postulacion(1, voluntario, "donacion");

        assertTrue(postulacion instanceof AggregateRoot);
        assertTrue(voluntario instanceof AggregateRoot);
        assertEquals(EstadoPostulacion.REGISTRADO, postulacion.getEstado());
    }

    @Test
    void elEventoDeRegistroExisteYExponeLosDatosClave() {
        VoluntarioRegistradoEvent evento = new VoluntarioRegistradoEvent("12345678", "Ana Torres");

        assertEquals("12345678", evento.dni());
        assertEquals("Ana Torres", evento.nombreCompleto());
    }
}
