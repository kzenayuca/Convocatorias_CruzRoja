package org.cruzroja.bc1_usuarios.dominio.entities;

import org.cruzroja.bc1_usuarios.dominio.enums.EstadoPostulacion;
import java.time.LocalDateTime;

public final class Postulacion {
    private final long id;
    private final Voluntario voluntario;
    private final String codigoConvocatoria;
    private final LocalDateTime fechaRegistro;
    private EstadoPostulacion estado;

    public Postulacion(long id, Voluntario voluntario, String codigoConvocatoria) {
        this(id, voluntario, codigoConvocatoria, LocalDateTime.now(),
                EstadoPostulacion.REGISTRADO);
    }

    public Postulacion(long id, Voluntario voluntario, String codigoConvocatoria,
                       LocalDateTime fechaRegistro, EstadoPostulacion estado) {
        this.id = id;
        this.voluntario = java.util.Objects.requireNonNull(voluntario);
        if (codigoConvocatoria == null || codigoConvocatoria.isBlank()) {
            throw new IllegalArgumentException("La convocatoria es obligatoria");
        }
        this.codigoConvocatoria = codigoConvocatoria.trim();
        this.fechaRegistro = java.util.Objects.requireNonNull(fechaRegistro);
        this.estado = java.util.Objects.requireNonNull(estado);
    }

    public long getId() { return id; }
    public Voluntario getVoluntario() { return voluntario; }
    public String getCodigoConvocatoria() { return codigoConvocatoria; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public EstadoPostulacion getEstado() { return estado; }
}
