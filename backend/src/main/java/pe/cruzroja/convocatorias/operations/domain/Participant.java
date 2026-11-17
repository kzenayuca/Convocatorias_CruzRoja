package pe.cruzroja.convocatorias.operations.domain;

/** Proyección del contrato de identidad requerida por Operaciones, sin conocer cuentas JDBC. */
public record Participant(
        boolean active,
        boolean volunteer,
        boolean graduated,
        boolean radioQualified,
        boolean pilotQualified) {
    public boolean qualified(String position) {
        if (!active || !volunteer) return false;
        return switch (position) {
            case "SOCORRISTA" -> true;
            case "FORMACION" -> !graduated;
            case "GRADUADO", "RESPONSABLE" -> graduated;
            case "RADIOPERADOR" -> graduated && radioQualified;
            case "PILOTO" -> graduated && pilotQualified;
            default -> false;
        };
    }
}
