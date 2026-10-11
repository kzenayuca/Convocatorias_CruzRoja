package org.cruzroja.bc2_convocatorias.aplicacion.dto;

public record ConvocatoriaDTO(
        String codigo,
        String nombre,
        int minimoParticipantes,
        long inscritosActuales,
        boolean minimoAlcanzado,
        int participantesFaltantes,
        String estado,
        String aviso,
        boolean puedeConfirmar,
        String fechaServicio,
        String lugar,
        String horaInicio,
        String horaFinalizacion,
        String duracionTotal,
        boolean horarioDefinido,
        String mensajeHorario,
        java.util.List<String> requisitos) {
}
