package pe.cruzroja.convocatorias.api;

import static pe.cruzroja.convocatorias.api.Store.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class NoticesController {
    private final JdbcTemplate db;
    public NoticesController(JdbcTemplate db) { this.db=db; }
    public record Announcement(@NotBlank @Size(max=150) String title,
                               @NotBlank @Size(max=5000) String body, @NotBlank String audience) { }
    @GetMapping("/notifications") public List<Map<String,Object>> notices() {
        return db.queryForList("SELECT * FROM notifications WHERE recipient_id=? ORDER BY is_read,id",actor().id());
    }
    @PostMapping("/notifications/{id}/read") public void read(@PathVariable UUID id) {
        db.update("UPDATE notifications SET is_read=true WHERE id=? AND recipient_id=?",id,actor().id());
    }
    @PostMapping("/announcements") @Transactional
    public void publish(@Valid @RequestBody Announcement r) {
        require(chief(),"Solo jefatura publica comunicados.");
        valid(Set.of("TODOS","VOLUNTARIO","RECLUTADOR","JEFATURA").contains(r.audience()),"Audiencia no válida.");
        db.update("INSERT INTO announcements(id,title,body,audience,published_by) VALUES (?,?,?,?,?)",UUID.randomUUID(),r.title(),r.body(),r.audience(),actor().id());
        for(var user:db.queryForList("SELECT id FROM users WHERE status='ACTIVO' AND (?='TODOS' OR role=?)",r.audience(),r.audience()))
            Store.notify(db,id(user,"id"),"COMUNICADO",r.title()+": "+r.body(),"/notifications");
    }
}
