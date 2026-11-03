package pe.cruzroja.convocatorias.api;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Convocatorias desde PostgreSQL; las otras consultas siguen siendo demostrativas. */
@RestController
@RequestMapping("/api/v1")
public class QueryController {
    private final JdbcTemplate jdbc;

    public QueryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/callouts")
    public List<Map<String, Object>> callouts() {
        return jdbc.query("""
                SELECT c.*, count(e.id) FILTER (WHERE e.status = 'CONFIRMADA') AS enrolled
                FROM callouts c LEFT JOIN enrollments e ON e.callout_id = c.id
                GROUP BY c.id
                ORDER BY (c.start_at < CURRENT_TIMESTAMP), c.start_at, c.id
                """, (rs, row) -> Map.<String, Object>of(
                        "id", rs.getString("id"),
                        "title", rs.getString("title"),
                        "description", rs.getString("description") == null ? "" : rs.getString("description"),
                        "status", rs.getString("status"),
                        "date", rs.getTimestamp("start_at").toLocalDateTime().toString(),
                        "endDate", rs.getTimestamp("end_at").toLocalDateTime().toString(),
                        "location", rs.getString("location") == null ? "" : rs.getString("location"),
                        "capacity", rs.getInt("capacity"),
                        "enrolled", rs.getInt("enrolled"),
                        "minimumLevel", rs.getString("minimum_level")));
    }

    @GetMapping("/enrollments")
    public List<Map<String, String>> enrollments() {
        return List.of(Map.of("callout", "Campaña de donación de sangre", "status", "PENDIENTE"));
    }

    @GetMapping("/attendance/hours")
    public Map<String, Object> hours() {
        return Map.of("hours", 24.5, "message", "Datos de demostración");
    }

    @GetMapping("/inventory")
    public List<Map<String, Object>> inventory() {
        return List.of(Map.of("name", "Botiquín portátil", "total", 20, "reserved", 6));
    }

    @GetMapping("/justifications")
    public List<Map<String, String>> justifications() {
        return List.of(Map.of("status", "PENDIENTE", "remaining", "48 h"));
    }

    @GetMapping("/notifications")
    public List<Map<String, Object>> notifications() {
        return List.of(Map.of("message", "Tu inscripción está pendiente", "link", "/services", "read", false));
    }
}
