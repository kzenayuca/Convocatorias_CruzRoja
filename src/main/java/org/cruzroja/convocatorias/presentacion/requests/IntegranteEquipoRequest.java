package org.cruzroja.convocatorias.presentacion.requests;

public record IntegranteEquipoRequest(
        String nombreVoluntario,
        String perfil,
        String habilidadEspecialidad,
        String estadoParticipacion,
        Long mentorId) {

    public org.cruzroja.convocatorias.aplicacion.dto.AgregarIntegranteComando aComando() {
        return new org.cruzroja.convocatorias.aplicacion.dto.AgregarIntegranteComando(
                nombreVoluntario, perfil, habilidadEspecialidad,
                estadoParticipacion, mentorId);
    }
}
