package org.cruzroja.bc1_usuarios.aplicacion.dto;

/** Datos independientes de HTTP necesarios para registrar una postulación. */
public record RegistrarPostulacionComando(
        String dni,
        String nombreCompleto,
        String nivelFormacion,
        String especialidad,
        String habilidades,
        String disponibilidad,
        String codigoConvocatoria) {
}
