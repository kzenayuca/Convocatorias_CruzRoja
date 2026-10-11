package org.cruzroja.bc3_inscripciones.aplicacion.dto;

import org.cruzroja.bc3_inscripciones.dominio.entities.Inscripcion;

/** Datos que se envían desde Java hacia la interfaz web. */
public final class InscripcionDTO {
    private final long id;
    private final String nombre;
    private final String dni;
    private final String correo;
    private final String telefono;
    private final String convocatoria;
    private final String codigoConvocatoria;
    private final String fechaInscripcion;
    private final String estado;
    private final String experiencia;

    private InscripcionDTO(Inscripcion inscripcion) {
        this.id = inscripcion.getId();
        this.nombre = inscripcion.getNombre();
        this.dni = inscripcion.getDni();
        this.correo = inscripcion.getCorreo();
        this.telefono = inscripcion.getTelefono();
        this.convocatoria = inscripcion.getConvocatoria();
        this.codigoConvocatoria = inscripcion.getCodigoConvocatoria();
        this.fechaInscripcion = inscripcion.getFechaInscripcion().toString();
        this.estado = inscripcion.getEstado().name();
        this.experiencia = inscripcion.getExperiencia();
    }

    public static InscripcionDTO desde(Inscripcion inscripcion) {
        return new InscripcionDTO(inscripcion);
    }

    public long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDni() {
        return dni;
    }

    public String getCorreo() {
        return correo;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getConvocatoria() {
        return convocatoria;
    }

    public String getCodigoConvocatoria() {
        return codigoConvocatoria;
    }

    public String getFechaInscripcion() {
        return fechaInscripcion;
    }

    public String getEstado() {
        return estado;
    }

    public String getExperiencia() {
        return experiencia;
    }
}
