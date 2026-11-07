package pe.cruzroja.convocatorias.membership.infrastructure;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.cruzroja.convocatorias.membership.application.*;
import pe.cruzroja.convocatorias.membership.domain.Volunteer;
import pe.cruzroja.convocatorias.shared.infrastructure.JdbcSupport;

public class JdbcVolunteerRepository extends JdbcSupport implements VolunteerRepository {
    public JdbcVolunteerRepository(JdbcTemplate db) {
        super(db);
    }

    private static final String PROFILES =
            "SELECT u.id,u.name,u.surnames,u.email,u.role,u.graduated,u.status,u.occupation,u.review_comment,p.document,p.phone,p.availability,p.radio_qualified,p.pilot_qualified FROM users u LEFT JOIN volunteer_profiles p ON p.user_id=u.id ";

    public void register(UUID id, AccountsUseCases.Registration r, String hash) {
        var p = r.profile();
        db.update(
                "INSERT INTO users(id,name,surnames,email,password_hash,role,graduated,status,occupation) VALUES (?,?,?,?,?,'VOLUNTARIO',false,'PENDIENTE',?)",
                id,
                p.name().trim(),
                p.surnames().trim(),
                p.email().trim().toLowerCase(Locale.ROOT),
                hash,
                p.occupation().trim());
        db.update(
                "INSERT INTO volunteer_profiles(user_id,document,phone) VALUES (?,?,?)",
                id,
                p.document(),
                r.phone());
    }

    public Map<String, Object> profile(UUID id) {
        return row(PROFILES + " WHERE u.id=?", id);
    }

    public List<Map<String, Object>> users() {
        return rows(PROFILES + " ORDER BY u.status,u.name");
    }

    public void phone(UUID id, String phone) {
        db.update("UPDATE volunteer_profiles SET phone=? WHERE user_id=?", phone, id);
    }

    public void requestChange(UUID id, AccountsUseCases.Profile p) {
        db.update(
                """
            INSERT INTO profile_changes(user_id,name,surnames,email,document,occupation) VALUES (?,?,?,?,?,?)
            ON CONFLICT(user_id) DO UPDATE SET name=EXCLUDED.name,surnames=EXCLUDED.surnames,email=EXCLUDED.email,
            document=EXCLUDED.document,occupation=EXCLUDED.occupation,submitted_at=CURRENT_TIMESTAMP,request_id=gen_random_uuid()
            """,
                id,
                p.name().trim(),
                p.surnames().trim(),
                p.email().trim().toLowerCase(Locale.ROOT),
                p.document(),
                p.occupation());
        db.update(
                "INSERT INTO profile_change_requests(id,user_id,name,surnames,email,document,occupation,submitted_at) SELECT request_id,user_id,name,surnames,email,document,occupation,submitted_at FROM profile_changes WHERE user_id=?",
                id);
    }

    public boolean hasPendingChange(UUID user) {
        return db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM profile_changes WHERE user_id=?)",
                Boolean.class,
                user);
    }

    public List<UUID> chiefs() {
        return db.queryForList(
                "SELECT id FROM users WHERE role='JEFATURA' AND status='ACTIVO'", UUID.class);
    }

    public List<Map<String, Object>> profileRequests(UUID user) {
        return rows(
                "SELECT r.*,u.name AS resolved_name FROM profile_change_requests r LEFT JOIN users u ON u.id=r.resolved_by WHERE r.user_id=? ORDER BY r.submitted_at DESC,r.id",
                user);
    }

    public Map<String, Object> profileRequest(UUID request) {
        return row(
                "SELECT r.*,u.name AS resolved_name FROM profile_change_requests r LEFT JOIN users u ON u.id=r.resolved_by WHERE r.id=?",
                request);
    }

    public void resolveChange(
            UUID request,
            boolean approved,
            String comment,
            UUID actor,
            java.time.LocalDateTime at) {
        db.update(
                "UPDATE profile_change_requests SET status=?,resolution_comment=?,resolved_by=?,resolved_at=? WHERE id=?",
                approved ? "APROBADA" : "RECHAZADA",
                comment,
                actor,
                at,
                request);
    }

    public List<Map<String, Object>> changes() {
        return rows("SELECT * FROM profile_changes ORDER BY submitted_at");
    }

    public Map<String, Object> lockChange(UUID id) {
        return row("SELECT * FROM profile_changes WHERE user_id=? FOR UPDATE", id);
    }

    public void approveChange(UUID id, Map<String, Object> p) {
        db.update(
                "UPDATE users SET name=?,surnames=?,email=?,occupation=? WHERE id=?",
                p.get("name"),
                p.get("surnames"),
                p.get("email"),
                p.get("occupation"),
                id);
        db.update(
                "UPDATE volunteer_profiles SET document=? WHERE user_id=?", p.get("document"), id);
    }

    public void deleteChange(UUID id) {
        db.update("DELETE FROM profile_changes WHERE user_id=?", id);
    }

    public void lock(UUID id) {
        row("SELECT id FROM users WHERE id=? FOR UPDATE", id);
    }

    public void save(Volunteer v, String comment) {
        db.update(
                "UPDATE users SET role=?,graduated=?,status=?,review_comment=? WHERE id=?",
                v.role(),
                v.graduated(),
                v.status(),
                comment,
                v.id());
        db.update(
                "UPDATE volunteer_profiles SET radio_qualified=?,pilot_qualified=? WHERE user_id=?",
                v.radioQualified(),
                v.pilotQualified(),
                v.id());
    }
}
