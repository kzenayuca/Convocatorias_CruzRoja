package pe.cruzroja.convocatorias.operations.domain;

import static pe.cruzroja.convocatorias.shared.domain.Rules.valid;

public enum CalloutStatus {
    BORRADOR,
    ABIERTA,
    CERRADA,
    CANCELADA,
    FINALIZADA,
    /** Read-only compatibility with pre-migration records. */
    PUBLICADA,
    CONFIRMADA;

    public static CalloutStatus parse(String value) {
        valid(value != null, "Indica el estado de la convocatoria.");
        try {
            return valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Estado de convocatoria no válido.", error);
        }
    }

    public boolean canEditBeforeOpening() {
        return this == BORRADOR || this == ABIERTA || this == CERRADA;
    }

    public boolean isPublished() {
        return this == ABIERTA || this == CERRADA;
    }
}
