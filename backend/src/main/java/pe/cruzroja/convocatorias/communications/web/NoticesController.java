package pe.cruzroja.convocatorias.communications.web;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import pe.cruzroja.convocatorias.communications.application.NoticesUseCases;
import pe.cruzroja.convocatorias.communications.domain.Announcement;

@RestController
@RequestMapping("/api/v1")
public class NoticesController {
    private final NoticesUseCases useCases;
    public NoticesController(NoticesUseCases useCases) { this.useCases=useCases; }
    @GetMapping("/notifications") public List<Map<String,Object>> notices() { return useCases.notices(); }
    @PostMapping("/notifications/{id}/read") public void read(@PathVariable UUID id) { useCases.read(id); }
    @PostMapping("/announcements") public void publish(@RequestBody Announcement r) { useCases.publish(r); }
}
