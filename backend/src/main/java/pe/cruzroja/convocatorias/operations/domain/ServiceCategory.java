package pe.cruzroja.convocatorias.operations.domain;

import static pe.cruzroja.convocatorias.shared.domain.Rules.valid;

public enum ServiceCategory {
    E,
    I,
    G;

    public static ServiceCategory parse(String value) {
        valid(value != null, "Selecciona E (Externo), I (Interno) o G (Guardia).");
        try {
            return valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(
                    "Selecciona E (Externo), I (Interno) o G (Guardia).", error);
        }
    }
}
