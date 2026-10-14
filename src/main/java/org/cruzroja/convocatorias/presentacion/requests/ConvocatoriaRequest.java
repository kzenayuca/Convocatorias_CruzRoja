package org.cruzroja.convocatorias.presentacion.requests;

import java.util.List;

public record ConvocatoriaRequest(
        String codigo,
        String titulo,
        String fecha,
        String horaInicio,
        String horaFin,
        String ubicacion,
        List<String> requisitos,
        int minimoParticipantes) {

    public org.cruzroja.convocatorias.aplicacion.dto.GuardarConvocatoriaComando aComando() {
        return new org.cruzroja.convocatorias.aplicacion.dto.GuardarConvocatoriaComando(
                codigo, titulo, fecha, horaInicio, horaFin, ubicacion,
                requisitos, minimoParticipantes);
    }
}
