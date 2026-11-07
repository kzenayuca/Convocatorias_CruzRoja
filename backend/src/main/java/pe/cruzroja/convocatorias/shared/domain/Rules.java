package pe.cruzroja.convocatorias.shared.domain;

/** Invariantes del negocio; ninguna dependencia de transporte o persistencia. */
public final class Rules {
    private Rules() {}

    public static void valid(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public static void required(String value, int max, String name) {
        valid(
                value != null && !value.isBlank() && value.length() <= max,
                name + " es obligatorio y admite hasta " + max + " caracteres.");
    }
}
