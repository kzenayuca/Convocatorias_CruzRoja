package org.cruzroja.bc2_convocatorias.dominio.valueobjects;

/** Value Object para el lugar donde se realizará el servicio. */
public record Ubicacion(String lugar) {
    public Ubicacion {
        if (lugar == null || lugar.isBlank()) {
            throw new IllegalArgumentException("El lugar es obligatorio");
        }
        lugar = lugar.trim();
    }
}
