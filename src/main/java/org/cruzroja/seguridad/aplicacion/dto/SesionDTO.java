package org.cruzroja.seguridad.aplicacion.dto;

public record SesionDTO(
        String usuario,
        String nombre,
        String rol,
        boolean graduado,
        boolean puedeEditarHorarios) {
}
