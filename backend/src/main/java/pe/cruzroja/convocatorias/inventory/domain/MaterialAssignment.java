package pe.cruzroja.convocatorias.inventory.domain;

import java.util.UUID;

public record MaterialAssignment(UUID id, UUID materialId, UUID calloutId, int quantity) {
    public MaterialAssignment {
        if (id == null || materialId == null || calloutId == null || quantity <= 0) {
            throw new IllegalArgumentException("La asignación de material es inválida");
        }
    }
}
