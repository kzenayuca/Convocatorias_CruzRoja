package org.cruzroja.bc3_inscripciones.aplicacion.dto;

/** Datos del caso de uso para registrar una inscripción. */
public record RegistrarInscripcionComando(
        String nombre,
        String dni,
        String correo,
        String telefono,
        String convocatoria,
        String codigoConvocatoria,
        String experiencia) {
}
