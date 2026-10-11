package org.cruzroja.bc1_usuarios.dominio.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.cruzroja.bc1_usuarios.dominio.enums.EstadoPostulacion;
import org.cruzroja.bc1_usuarios.dominio.enums.TipoVoluntario;
import org.cruzroja.bc1_usuarios.dominio.valueobjects.Perfil;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostulacionTest {
    @Test
    void unaPostulacionNuevaQuedaRegistrada() {
        Perfil perfil = new Perfil(TipoVoluntario.GRADUADO, "Primeros auxilios",
                List.of("RCP"));
        Voluntario voluntario = new Voluntario(
                "12345678", "Ana Torres", perfil, "Fines de semana");
        Postulacion postulacion = new Postulacion(1, voluntario, "donacion");
        assertEquals(EstadoPostulacion.REGISTRADO, postulacion.getEstado());
        assertEquals("donacion", postulacion.getCodigoConvocatoria());
    }

    @Test
    void exigeUnaConvocatoria() {
        Perfil perfil = new Perfil(TipoVoluntario.EN_FORMACION, "Logística", List.of());
        Voluntario voluntario = new Voluntario("12345678", "Luis Pérez", perfil, "Tardes");
        assertThrows(IllegalArgumentException.class,
                () -> new Postulacion(1, voluntario, " "));
    }
}
