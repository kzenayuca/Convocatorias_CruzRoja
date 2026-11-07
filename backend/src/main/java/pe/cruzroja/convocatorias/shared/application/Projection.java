package pe.cruzroja.convocatorias.shared.application;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/** Modelo de lectura compatible con la API existente; nunca representa un agregado de dominio. */
public final class Projection {
    private Projection() {}

    public static LocalDateTime time(Map<String, Object> row, String key) {
        return (LocalDateTime) row.get(key);
    }

    public static UUID id(Map<String, Object> row, String key) {
        return (UUID) row.get(key);
    }

    public static String text(Map<String, Object> row, String key) {
        return String.valueOf(row.getOrDefault(key, ""));
    }
}
