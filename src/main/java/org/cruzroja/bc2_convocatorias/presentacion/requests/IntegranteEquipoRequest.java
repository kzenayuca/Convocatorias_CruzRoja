package org.cruzroja.bc2_convocatorias.presentacion.requests;

public record IntegranteEquipoRequest(
        String nombreVoluntario,
        String perfil,
        String habilidadEspecialidad,
        String estadoParticipacion,
        Long mentorId) {

    public org.cruzroja.bc2_convocatorias.aplicacion.dto.AgregarIntegranteComando aComando() {
        return new org.cruzroja.bc2_convocatorias.aplicacion.dto.AgregarIntegranteComando(
                nombreVoluntario, perfil, habilidadEspecialidad,
                estadoParticipacion, mentorId);
    }
}
