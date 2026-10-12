package org.cruzroja.usuarios.aplicacion.dto;

import org.cruzroja.usuarios.dominio.entities.Postulacion;
import java.util.List;

public record PostulacionDTO(
        long id,
        String dni,
        String nombreCompleto,
        String nivelFormacion,
        String especialidad,
        List<String> habilidades,
        String disponibilidad,
        String codigoConvocatoria,
        String fechaRegistro,
        String estado) {

    public static PostulacionDTO desde(Postulacion postulacion) {
        var voluntario = postulacion.getVoluntario();
        return new PostulacionDTO(
                postulacion.getId(), voluntario.dni(), voluntario.nombreCompleto(),
                voluntario.perfil().nivelFormacion().name(),
                voluntario.perfil().especialidad(), voluntario.perfil().habilidades(),
                voluntario.disponibilidad(), postulacion.getCodigoConvocatoria(),
                postulacion.getFechaRegistro().toString(), postulacion.getEstado().name());
    }
}
