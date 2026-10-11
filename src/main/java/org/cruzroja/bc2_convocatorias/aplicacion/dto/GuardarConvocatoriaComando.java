package org.cruzroja.bc2_convocatorias.aplicacion.dto;

import java.util.List;

/** Datos de entrada del caso de uso de creación o edición de convocatorias. */
public record GuardarConvocatoriaComando(
        String codigo,
        String titulo,
        String fecha,
        String horaInicio,
        String horaFin,
        String ubicacion,
        List<String> requisitos,
        int minimoParticipantes) {
}
