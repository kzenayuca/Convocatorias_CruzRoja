package pe.cruzroja.convocatorias.membership.infrastructure;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import pe.cruzroja.convocatorias.membership.application.*;
import pe.cruzroja.convocatorias.membership.domain.Volunteer;
import pe.cruzroja.convocatorias.shared.application.NotFound;
import pe.cruzroja.convocatorias.shared.infrastructure.JdbcSupport;

public class JdbcVolunteerRepository extends JdbcSupport implements VolunteerRepository {
    public JdbcVolunteerRepository(JdbcTemplate db) {
        super(db);
    }

    private static final String PROFILES =
            "SELECT u.id,u.name,u.surnames,u.email,u.role,u.graduated,u.status,u.occupation,u.review_comment,p.document,p.phone,p.availability,p.radio_qualified,p.pilot_qualified FROM users u LEFT JOIN volunteer_profiles p ON p.user_id=u.id ";

    private static final RowMapper<VolunteerProfile> VOLUNTEER_PROFILE =
            (result, rowNumber) ->
                    new VolunteerProfile(
                            result.getObject("id", UUID.class),
                            result.getString("name"),
                            result.getString("surnames"),
                            result.getString("email"),
                            result.getString("role"),
                            result.getBoolean("graduated"),
                            result.getString("status"),
                            result.getString("occupation"),
                            result.getString("review_comment"),
                            result.getString("document"),
                            result.getString("phone"),
                            result.getString("availability"),
                            result.getObject("radio_qualified", Boolean.class),
                            result.getObject("pilot_qualified", Boolean.class));

    private static final RowMapper<PendingProfileChange> PENDING_PROFILE_CHANGE =
            (result, rowNumber) ->
                    new PendingProfileChange(
                            result.getObject("user_id", UUID.class),
                            result.getString("name"),
                            result.getString("surnames"),
                            result.getString("email"),
                            result.getString("document"),
                            result.getString("occupation"),
                            result.getObject("submitted_at", LocalDateTime.class),
                            result.getObject("request_id", UUID.class));

    private static final RowMapper<ProfileChangeRequest> PROFILE_CHANGE_REQUEST =
            (result, rowNumber) ->
                    new ProfileChangeRequest(
                            result.getObject("id", UUID.class),
                            result.getObject("user_id", UUID.class),
                            result.getString("name"),
                            result.getString("surnames"),
                            result.getString("email"),
                            result.getString("document"),
                            result.getString("occupation"),
                            result.getObject("submitted_at", LocalDateTime.class),
                            result.getString("status"),
                            result.getString("resolution_comment"),
                            result.getObject("resolved_by", UUID.class),
                            result.getObject("resolved_at", LocalDateTime.class),
                            result.getString("resolved_name"));

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

    public VolunteerProfile profile(UUID id) {
        return requiredRow(PROFILES + " WHERE u.id=?", VOLUNTEER_PROFILE, id);
    }

    public List<VolunteerProfile> users() {
        return db.query(PROFILES + " ORDER BY u.status,u.name", VOLUNTEER_PROFILE);
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

    public List<ProfileChangeRequest> profileRequests(UUID user) {
        return db.query(
                "SELECT r.*,u.name AS resolved_name FROM profile_change_requests r LEFT JOIN users u ON u.id=r.resolved_by WHERE r.user_id=? ORDER BY r.submitted_at DESC,r.id",
                PROFILE_CHANGE_REQUEST,
                user);
    }

    public ProfileChangeRequest profileRequest(UUID request) {
        return requiredRow(
                "SELECT r.*,u.name AS resolved_name FROM profile_change_requests r LEFT JOIN users u ON u.id=r.resolved_by WHERE r.id=?",
                PROFILE_CHANGE_REQUEST,
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

    public List<PendingProfileChange> changes() {
        return db.query(
                "SELECT * FROM profile_changes ORDER BY submitted_at", PENDING_PROFILE_CHANGE);
    }

    public PendingProfileChange lockChange(UUID id) {
        return requiredRow(
                "SELECT * FROM profile_changes WHERE user_id=? FOR UPDATE",
                PENDING_PROFILE_CHANGE,
                id);
    }

    public void approveChange(UUID id, PendingProfileChange p) {
        db.update(
                "UPDATE users SET name=?,surnames=?,email=?,occupation=? WHERE id=?",
                p.name(),
                p.surnames(),
                p.email(),
                p.occupation(),
                id);
        db.update("UPDATE volunteer_profiles SET document=? WHERE user_id=?", p.document(), id);
    }

    public void deleteChange(UUID id) {
        db.update("DELETE FROM profile_changes WHERE user_id=?", id);
    }

    public void lock(UUID id) {
        row("SELECT id FROM users WHERE id=? FOR UPDATE", id);
    }

    private <T> T requiredRow(String sql, RowMapper<T> mapper, UUID id) {
        var results = db.query(sql, mapper, id);
        if (results.isEmpty()) throw new NotFound();
        return results.get(0);
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
