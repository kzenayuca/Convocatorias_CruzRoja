package pe.cruzroja.convocatorias.api;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consultas de demostración conservadas mientras se completa la persistencia. */
@RestController
@RequestMapping("/api/v1")
public class QueryController {
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/callouts")
    public List<Map<String, Object>> callouts() {
        return List.of(Map.of(
                "id", "10000000-0000-0000-0000-000000000001",
                "title", "Campaña de donación de sangre",
                "status", "PUBLICADA",
                "date", LocalDateTime.now().plusDays(7).toString(),
                "location", "Cercado",
                "capacity", 40,
                "enrolled", 32));
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
