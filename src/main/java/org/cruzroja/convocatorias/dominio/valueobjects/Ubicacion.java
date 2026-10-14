package org.cruzroja.convocatorias.dominio.valueobjects;

import org.cruzroja.shared.domain.ValueObject;

/** Value Object inmutable (record) para el lugar donde se realizará el servicio. */
public record Ubicacion(String lugar) implements ValueObject {
    public Ubicacion {
        if (lugar == null || lugar.isBlank()) {
            throw new IllegalArgumentException("El lugar es obligatorio");
        }
        lugar = lugar.trim();
    }
}
