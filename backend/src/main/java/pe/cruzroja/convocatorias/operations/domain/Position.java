package pe.cruzroja.convocatorias.operations.domain;

import static pe.cruzroja.convocatorias.shared.domain.Rules.valid;

/** FORMACION remains readable for historical commitments, not for new enrollment. */
public enum Position {
    RESPONSABLE,
    SOCORRISTA,
    GRADUADO,
    FORMACION,
    RADIOPERADOR,
    PILOTO;

    public static Position parse(String value) {
        valid(value != null, "Selecciona un puesto.");
        try {
            return valueOf(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Puesto no válido.", error);
        }
    }

    public boolean isGeneralVolunteer() {
        return this == SOCORRISTA || this == GRADUADO || this == FORMACION;
    }

    public void requireSelfEnrollment() {
        valid(this != RESPONSABLE, "El responsable lo asigna jefatura y cuenta dentro del total.");
        valid(this != FORMACION, "No hay nuevas plazas exclusivas para formación.");
    }
}
