package org.cruzroja.bc3_inscripciones.dominio.entities;

import org.cruzroja.bc3_inscripciones.dominio.enums.EstadoInscripcion;

import java.time.LocalDate;
import java.util.Objects;

/** Entidad del dominio BC3 que representa una inscripción. */
public final class Inscripcion {
    private final long id;
    private final String nombre;
    private final String dni;
    private final String correo;
    private final String telefono;
    private final String convocatoria;
    private final String codigoConvocatoria;
    private final LocalDate fechaInscripcion;
    private final String experiencia;
    private EstadoInscripcion estado;

    public Inscripcion(
            long id,
            String nombre,
            String dni,
            String correo,
            String telefono,
            String convocatoria,
            String codigoConvocatoria,
            LocalDate fechaInscripcion,
            EstadoInscripcion estado,
            String experiencia) {

        if (id <= 0) {
            throw new IllegalArgumentException("El identificador debe ser mayor que cero");
        }

        this.id = id;
        this.nombre = validarTexto(nombre, "nombre");
        this.dni = validarDni(dni);
        this.correo = validarCorreo(correo);
        this.telefono = validarTexto(telefono, "teléfono");
        this.convocatoria = validarTexto(convocatoria, "convocatoria");
        this.codigoConvocatoria = validarTexto(codigoConvocatoria, "código de convocatoria");
        this.fechaInscripcion = Objects.requireNonNull(
                fechaInscripcion,
                "La fecha de inscripción es obligatoria"
        );
        this.estado = Objects.requireNonNull(estado, "El estado es obligatorio");
        this.experiencia = experiencia == null ? "" : experiencia.trim();
    }

    public void cambiarEstado(EstadoInscripcion nuevoEstado) {
        estado = Objects.requireNonNull(nuevoEstado, "El nuevo estado es obligatorio");
    }

    private static String validarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio");
        }
        return valor.trim();
    }

    private static String validarDni(String valor) {
        String dniValidado = validarTexto(valor, "DNI");
        if (!dniValidado.matches("\\d{8}")) {
            throw new IllegalArgumentException("El DNI debe contener exactamente 8 dígitos");
        }
        return dniValidado;
    }

    private static String validarCorreo(String valor) {
        String correoValidado = validarTexto(valor, "correo");
        if (!correoValidado.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("El correo no tiene un formato válido");
        }
        return correoValidado;
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

    public LocalDate getFechaInscripcion() {
        return fechaInscripcion;
    }

    public EstadoInscripcion getEstado() {
        return estado;
    }

    public String getExperiencia() {
        return experiencia;
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof Inscripcion)) {
            return false;
        }
        Inscripcion otraInscripcion = (Inscripcion) otro;
        return id == otraInscripcion.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }
}
