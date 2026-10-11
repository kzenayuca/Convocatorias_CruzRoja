package org.cruzroja.bc2_convocatorias.aplicacion.dto;

/** Datos del caso de uso para asignar un voluntario a un equipo. */
public record AgregarIntegranteComando(
        String nombreVoluntario,
        String perfil,
        String habilidadEspecialidad,
        String estadoParticipacion,
        Long mentorId) {
}
