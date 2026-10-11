package org.cruzroja.bc3_inscripciones.dominio.enums;

/** Estados permitidos para una inscripción. */
public enum EstadoInscripcion {
    PENDIENTE("Pendiente"),
    APROBADA("Aprobada"),
    RECHAZADA("Rechazada"),
    CANCELADA("Cancelada");

    private final String descripcion;

    EstadoInscripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public static EstadoInscripcion desdeTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El estado es obligatorio");
        }

        try {
            return EstadoInscripcion.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Estado inválido. Use PENDIENTE, APROBADA, RECHAZADA o CANCELADA",
                    exception
            );
        }
    }
}
