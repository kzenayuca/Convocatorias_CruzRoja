package pe.cruzroja.convocatorias.api;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import pe.cruzroja.convocatorias.auth.AuthenticatedUser;

/** Utilidades pequeñas compartidas por los casos de uso JDBC. */
public final class Store {
    private Store() { }
    public static LocalDateTime now() { return LocalDateTime.now(ZoneId.of("America/Lima")); }
    public static AuthenticatedUser actor() {
        return (AuthenticatedUser) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
    public static boolean chief() { return actor().role().equals("JEFATURA"); }
    public static boolean manager() { return chief(); }
    public static void require(boolean condition, String message) {
        if (!condition) throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }
    public static void valid(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    public static Map<String, Object> one(JdbcTemplate db, String sql, Object... args) {
        var rows = db.queryForList(sql, args);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Registro no encontrado.");
        return rows.get(0);
    }
    public static LocalDateTime time(Map<String, Object> row, String key) {
        return row.get(key) == null ? null : ((Timestamp) row.get(key)).toLocalDateTime();
    }
    public static UUID id(Map<String, Object> row, String key) { return (UUID) row.get(key); }
    public static String text(Map<String, Object> row, String key) { return String.valueOf(row.getOrDefault(key, "")); }
    public static void notify(JdbcTemplate db, UUID user, String type, String message, String link) {
        db.update("INSERT INTO notifications(id,recipient_id,type,message,link) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), user, type, message, link);
    }
}
