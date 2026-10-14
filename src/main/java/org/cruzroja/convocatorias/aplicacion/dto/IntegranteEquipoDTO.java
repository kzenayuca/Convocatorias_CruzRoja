package org.cruzroja.convocatorias.aplicacion.dto;

public record IntegranteEquipoDTO(
        long id,
        String convocatoriaCodigo,
        String nombreVoluntario,
        String perfil,
        String habilidadEspecialidad,
        String estadoParticipacion,
        Long mentorId,
        String nombreMentor) {
}
