package org.cruzroja.usuarios.presentacion.requests;

import org.cruzroja.usuarios.aplicacion.dto.RegistrarPostulacionComando;

public record PostulacionRequest(
        String dni,
        String nombreCompleto,
        String nivelFormacion,
        String especialidad,
        String habilidades,
        String disponibilidad,
        String codigoConvocatoria) {

    public RegistrarPostulacionComando aComando() {
        return new RegistrarPostulacionComando(
                dni, nombreCompleto, nivelFormacion, especialidad,
                habilidades, disponibilidad, codigoConvocatoria);
    }
}
