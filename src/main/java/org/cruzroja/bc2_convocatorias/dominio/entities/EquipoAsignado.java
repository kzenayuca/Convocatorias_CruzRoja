package org.cruzroja.bc2_convocatorias.dominio.entities;

/** Integrante del equipo operativo asignado a una convocatoria. */
public final class EquipoAsignado {
    public enum Perfil { GRADUADO, EN_FORMACION }
    public enum EstadoParticipacion { ACTIVO, PENDIENTE, RETIRADO }

    private final long id;
    private final String convocatoriaCodigo;
    private final String nombreVoluntario;
    private final Perfil perfil;
    private final String habilidadEspecialidad;
    private EstadoParticipacion estado;
    private final Long mentorId;

    public EquipoAsignado(
            long id, String convocatoriaCodigo, String nombreVoluntario,
            Perfil perfil, String habilidadEspecialidad,
            EstadoParticipacion estado, Long mentorId) {
        if (id <= 0) throw new IllegalArgumentException("El id debe ser mayor que cero");
        this.id = id;
        this.convocatoriaCodigo = texto(convocatoriaCodigo, "convocatoria");
        this.nombreVoluntario = texto(nombreVoluntario, "nombre");
        this.perfil = java.util.Objects.requireNonNull(perfil, "El perfil es obligatorio");
        this.habilidadEspecialidad = texto(habilidadEspecialidad, "habilidad o especialidad");
        this.estado = java.util.Objects.requireNonNull(estado, "El estado es obligatorio");
        if (perfil == Perfil.GRADUADO && mentorId != null) {
            throw new IllegalArgumentException("Un graduado no requiere mentor");
        }
        this.mentorId = mentorId;
    }

    private static String texto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio");
        }
        return valor.trim();
    }

    public void cambiarEstado(EstadoParticipacion nuevoEstado) {
        estado = java.util.Objects.requireNonNull(nuevoEstado);
    }

    public long getId() { return id; }
    public String getConvocatoriaCodigo() { return convocatoriaCodigo; }
    public String getNombreVoluntario() { return nombreVoluntario; }
    public Perfil getPerfil() { return perfil; }
    public String getHabilidadEspecialidad() { return habilidadEspecialidad; }
    public EstadoParticipacion getEstado() { return estado; }
    public Long getMentorId() { return mentorId; }
}
