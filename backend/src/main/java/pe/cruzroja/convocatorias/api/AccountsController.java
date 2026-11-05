package pe.cruzroja.convocatorias.api;

import static pe.cruzroja.convocatorias.api.Store.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AccountsController {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final CalloutWorkflow workflow;
    public AccountsController(JdbcTemplate db, PasswordEncoder passwords, CalloutWorkflow workflow) { this.db = db; this.passwords = passwords; this.workflow=workflow; }

    public record Profile(@NotBlank @Size(max=120) String name, @NotBlank @Size(max=160) String surnames,
                          @Email @NotBlank @Size(max=180) String email,
                          @Pattern(regexp="[0-9]{8}") @NotNull String document,
                          @NotBlank @Size(max=160) String occupation) { }
    public record Registration(@Valid @NotNull Profile profile,
                               @NotBlank @Size(min=8,max=72) String password,
                               @NotBlank @Size(max=30) String phone) { }
    public record Phone(@NotBlank @Size(max=30) String phone) { }
    public record Review(@NotBlank String role, @NotBlank String level, boolean graduated,
                         @NotBlank String status, @NotBlank @Size(max=2000) String comment,
                         boolean radioQualified, boolean pilotQualified) { }
    public record Decision(boolean approved, @NotBlank @Size(max=2000) String comment) { }

    @PostMapping("/auth/register") @Transactional
    public Map<String, String> register(@Valid @RequestBody Registration request) {
        var p = request.profile(); var id = UUID.randomUUID();
        db.update("INSERT INTO users(id,name,surnames,email,password_hash,role,graduated,status,occupation) VALUES (?,?,?,?,?,'VOLUNTARIO',false,'PENDIENTE',?)",
                id, p.name().trim(), p.surnames().trim(), p.email().trim().toLowerCase(Locale.ROOT), passwords.encode(request.password()), p.occupation().trim());
        db.update("INSERT INTO volunteer_profiles(user_id,document,phone,training_level) VALUES (?,?,?,'BASICO')", id, p.document(), request.phone());
        return Map.of("mensaje", "Solicitud registrada. Jefatura debe activar tu cuenta antes de iniciar sesión.");
    }

    private static final String PROFILES = "SELECT u.id,u.name,u.surnames,u.email,u.role,u.graduated,u.status,u.occupation,u.review_comment,p.document,p.phone,p.training_level,p.availability,p.radio_qualified,p.pilot_qualified FROM users u LEFT JOIN volunteer_profiles p ON p.user_id=u.id ";

    @GetMapping("/profile")
    public Map<String, Object> profile() { return one(db, PROFILES + " WHERE u.id=?", actor().id()); }

    @PutMapping("/profile/phone")
    public void phone(@Valid @RequestBody Phone request) { db.update("UPDATE volunteer_profiles SET phone=? WHERE user_id=?", request.phone(), actor().id()); }

    @PostMapping("/profile/changes")
    public void change(@Valid @RequestBody Profile p) {
        db.update("""
            INSERT INTO profile_changes(user_id,name,surnames,email,document,occupation) VALUES (?,?,?,?,?,?)
            ON CONFLICT(user_id) DO UPDATE SET name=EXCLUDED.name,surnames=EXCLUDED.surnames,email=EXCLUDED.email,
            document=EXCLUDED.document,occupation=EXCLUDED.occupation,submitted_at=CURRENT_TIMESTAMP
            """, actor().id(), p.name().trim(), p.surnames().trim(), p.email().trim().toLowerCase(Locale.ROOT), p.document(), p.occupation());
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        require(chief(), "Solo jefatura puede consultar los datos personales de las cuentas.");
        return db.queryForList(PROFILES + " ORDER BY u.status,u.name");
    }

    @GetMapping("/users/changes")
    public List<Map<String, Object>> changes() {
        require(chief(), "Solo jefatura revisa cambios de perfil.");
        return db.queryForList("SELECT * FROM profile_changes ORDER BY submitted_at");
    }

    @PostMapping("/users/{id}/changes") @Transactional
    public void resolveProfile(@PathVariable UUID id, @Valid @RequestBody Decision decision) {
        require(chief(), "Solo jefatura revisa cambios de perfil.");
        var p = one(db, "SELECT * FROM profile_changes WHERE user_id=? FOR UPDATE", id);
        if (decision.approved()) {
            db.update("UPDATE users SET name=?,surnames=?,email=?,occupation=? WHERE id=?", p.get("name"), p.get("surnames"), p.get("email"), p.get("occupation"), id);
            db.update("UPDATE volunteer_profiles SET document=? WHERE user_id=?", p.get("document"), id);
        }
        db.update("DELETE FROM profile_changes WHERE user_id=?", id);
        Store.notify(db, id, "PERFIL", "Cambio de perfil " + (decision.approved() ? "aprobado: " : "rechazado: ") + decision.comment(), "/profile");
    }

    @PutMapping("/users/{id}") @Transactional
    public void review(@PathVariable UUID id, @Valid @RequestBody Review r) {
        require(chief(), "Solo jefatura administra cuentas.");
        valid(Set.of("VOLUNTARIO", "JEFATURA").contains(r.role()), "Rol no válido. Responsable es una función por servicio, no un rol de cuenta.");
        valid(Set.of("BASICO", "INTERMEDIO", "AVANZADO").contains(r.level()), "Nivel no válido.");
        valid(Set.of("ACTIVO", "PENDIENTE", "RECHAZADO", "INACTIVO", "SUSPENDIDO").contains(r.status()), "Estado no válido.");
        // Mismo orden de bloqueo que la aprobación de inscripciones: servicio antes del perfil.
        var affected = db.queryForList("SELECT DISTINCT callout_id FROM enrollments WHERE volunteer_id=? ORDER BY callout_id", id);
        for (var c : affected) one(db, "SELECT id FROM callouts WHERE id=? FOR UPDATE", c.get("callout_id"));
        var old = one(db, "SELECT u.*,p.training_level FROM users u LEFT JOIN volunteer_profiles p ON p.user_id=u.id WHERE u.id=? FOR UPDATE OF u", id);
        valid(!r.level().equals("BASICO") || Objects.equals(old.get("training_level"), "BASICO"), "No se puede reducir el nivel a BASICO.");
        valid(!id.equals(actor().id()) || r.status().equals("ACTIVO") && r.role().equals("JEFATURA"), "No puedes retirar tu propio acceso de jefatura.");
        db.update("UPDATE users SET role=?,graduated=?,status=?,review_comment=? WHERE id=?", r.role(), r.graduated(), r.status(), r.comment(), id);
        db.update("UPDATE volunteer_profiles SET training_level=?,radio_qualified=?,pilot_qualified=? WHERE user_id=?", r.level(),r.radioQualified(),r.pilotQualified(),id);
        for (var entry : affected) {
            var c=workflow.locked(id(entry,"callout_id"));
            if(!time(c,"start_at").isAfter(now())) continue;
            for(var e:db.queryForList("SELECT * FROM enrollments WHERE callout_id=? AND volunteer_id=? AND status IN ('PENDIENTE','CONFIRMADA','PENDIENTE_REEMPLAZO')",c.get("id"),id)) {
                if(!workflow.qualified(c,id,text(e,"position"))) db.update("UPDATE enrollments SET status='CANCELADA',comment='Cuenta no activa o habilitación retirada por jefatura' WHERE id=?",e.get("id"));
            }
            workflow.assignResponsible(c); workflow.synchronize(c);
        }
        Store.notify(db, id, "CUENTA", "Estado de cuenta: " + r.status() + ". " + Objects.toString(r.comment(), ""), "/profile");
    }
}
