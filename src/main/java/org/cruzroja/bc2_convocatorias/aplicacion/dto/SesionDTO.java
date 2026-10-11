package org.cruzroja.bc2_convocatorias.aplicacion.dto;

public record SesionDTO(
        String usuario,
        String nombre,
        String rol,
        boolean graduado,
        boolean puedeEditarHorarios) {
}
