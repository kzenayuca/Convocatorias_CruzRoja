package pe.cruzroja.convocatorias.operations.infrastructure;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.cruzroja.convocatorias.operations.application.*;
import pe.cruzroja.convocatorias.operations.domain.*;
import pe.cruzroja.convocatorias.shared.infrastructure.JdbcSupport;

public class JdbcCalloutRepository extends JdbcSupport implements CalloutRepository {
    public JdbcCalloutRepository(JdbcTemplate db) {
        super(db);
    }

    private static final String FIELDS =
            "id,title,start_at,end_at,deadline,kind,status,created_by,responsible_id,opened_at,closed_at,total_volunteers,socorrista_slots,graduate_slots,trainee_slots,radio_slots,pilot_slots,service_code,requirements,photo_uploaded_at,service_category,code_year,code_sequence,code_created_at";
    private static final String VIEW =
            """
        SELECT c.id,c.title,c.description,c.start_at,c.end_at,c.deadline,c.location,c.requirements,
        c.status,c.kind,c.area,c.contact,c.responsible_id,c.created_by,c.opened_at,c.closed_at,c.observations,
        c.service_code,c.service_type,c.service_category,c.code_year,c.code_sequence,c.code_created_at,c.total_volunteers,c.socorrista_slots,c.graduate_slots,c.trainee_slots,c.radio_slots,c.pilot_slots,c.planned_hours,
        (c.team_photo IS NOT NULL) AS has_photo,c.photo_uploaded_at,u.name AS responsible_name
        FROM callouts c LEFT JOIN users u ON u.id=c.responsible_id
        """;

    public Map<String, Object> snapshot(UUID id, boolean lock) {
        return row(
                "SELECT " + FIELDS + " FROM callouts WHERE id=?" + (lock ? " FOR UPDATE" : ""), id);
    }

    public Map<String, Object> view(UUID id) {
        return row(VIEW + " WHERE c.id=?", id);
    }

    public List<Map<String, Object>> views() {
        return rows(VIEW + " ORDER BY c.start_at,c.id");
    }

    public List<Map<String, Object>> schedule(LocalDate day, UUID user) {
        String own =
                user == null
                        ? ""
                        : " AND EXISTS(SELECT 1 FROM enrollments e WHERE e.callout_id=c.id AND e.volunteer_id=? AND e.status='CONFIRMADA')";
        return user == null
                ? rows(
                        VIEW + " WHERE c.start_at<? AND c.end_at>? ORDER BY c.start_at,c.id",
                        day.plusDays(1).atStartOfDay(),
                        day.atStartOfDay())
                : rows(
                        VIEW
                                + " WHERE c.start_at<? AND c.end_at>?"
                                + own
                                + " ORDER BY c.start_at,c.id",
                        day.plusDays(1).atStartOfDay(),
                        day.atStartOfDay(),
                        user);
    }

    public List<Callout.Member> members(UUID id) {
        return db.query(
                "SELECT e.volunteer_id,e.position,u.graduated FROM enrollments e JOIN users u ON u.id=e.volunteer_id WHERE e.callout_id=? AND e.status='CONFIRMADA'",
                (r, n) ->
                        new Callout.Member(
                                r.getObject(1, UUID.class), r.getString(2), r.getBoolean(3)),
                id);
    }

    public Participant participant(UUID id) {
        var p =
                row(
                        "SELECT u.status,u.role,u.graduated,p.radio_qualified,p.pilot_qualified FROM users u JOIN volunteer_profiles p ON p.user_id=u.id WHERE u.id=?",
                        id);
        return new Participant(
                "ACTIVO".equals(p.get("status")),
                "VOLUNTARIO".equals(p.get("role")),
                Boolean.TRUE.equals(p.get("graduated")),
                Boolean.TRUE.equals(p.get("radio_qualified")),
                Boolean.TRUE.equals(p.get("pilot_qualified")));
    }

    public boolean overlaps(UUID callout, UUID user, LocalDateTime start, LocalDateTime end) {
        return db.queryForObject(
                        "SELECT count(*) FROM enrollments e JOIN callouts c ON c.id=e.callout_id WHERE e.volunteer_id=? AND e.status='CONFIRMADA' AND c.status<>'CANCELADA' AND c.id<>? AND c.start_at<? AND c.end_at>?",
                        Integer.class,
                        user,
                        callout,
                        end,
                        start)
                > 0;
    }

    public boolean attended(UUID id, UUID user) {
        return db.queryForObject(
                        "SELECT count(*) FROM attendance_records WHERE callout_id=? AND volunteer_id=? AND check_in IS NOT NULL",
                        Integer.class,
                        id,
                        user)
                > 0;
    }

    public List<String> replacementPositions(UUID id) {
        return db.queryForList(
                "SELECT DISTINCT position FROM enrollments WHERE callout_id=? AND status='CONFIRMADA' AND cancellation_requested_at IS NOT NULL",
                String.class,
                id);
    }

    public String responsiblePhone(UUID id, UUID user) {
        return db
                .queryForList(
                        "SELECT p.phone FROM volunteer_profiles p JOIN enrollments e ON e.volunteer_id=p.user_id WHERE p.user_id=? AND e.callout_id=? AND e.position='RESPONSABLE' AND e.status='CONFIRMADA'",
                        String.class,
                        user,
                        id)
                .stream()
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public List<UUID> activeUsers() {
        return db.queryForList("SELECT id FROM users WHERE status='ACTIVO'", UUID.class);
    }

    public boolean annualEligible(UUID user, int year) {
        return db.queryForObject(
                "SELECT NOT u.graduated OR EXISTS(SELECT 1 FROM annual_enrollments a WHERE a.user_id=u.id AND a.year=?) FROM users u WHERE u.id=?",
                Boolean.class,
                year,
                user);
    }

    public int nextCodeSequence(String category, int year) {
        return db.queryForObject(
                "INSERT INTO service_code_counters(category,year,last_value) VALUES (?,?,1) ON CONFLICT(category,year) DO UPDATE SET last_value=service_code_counters.last_value+1 RETURNING last_value",
                Integer.class,
                category,
                year);
    }

    public boolean codeReserved(String code) {
        return db.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM service_code_history WHERE lower(service_code)=lower(?)) OR EXISTS(SELECT 1 FROM callouts WHERE lower(service_code)=lower(?))",
                Boolean.class,
                code,
                code);
    }

    public void generatedCode(UUID id, ServiceCode code) {
        db.update(
                "UPDATE callouts SET service_category=?,code_year=?,code_sequence=?,code_created_at=? WHERE id=?",
                code.category(),
                code.createdAt().getYear(),
                code.sequence(),
                code.createdAt(),
                id);
    }

    public void create(UUID id, ServicesUseCases.Callout r, UUID creator, String code) {
        int total = r.totalVolunteers();
        db.update(
                """
            INSERT INTO callouts(id,title,description,start_at,end_at,deadline,location,requirements,minimum_level,
            capacity,minimum_participants,status,kind,area,contact,created_by,service_code,service_type,
            socorrista_slots,radio_slots,pilot_slots,graduate_slots,total_volunteers) VALUES (?,?,?,?,?,?,?,?,?,?,?,'BORRADOR',?,?,?,?,?,?,?,?,?,?,?)
            """,
                id,
                r.title(),
                r.description(),
                r.start(),
                r.end(),
                r.deadline(),
                r.location(),
                r.requirements(),
                "BASICO",
                total,
                total,
                r.kind(),
                r.area(),
                "",
                creator,
                code,
                r.serviceType(),
                total - r.radioSlots() - r.pilotSlots(),
                r.radioSlots(),
                r.pilotSlots(),
                r.graduateSlots(),
                total);
    }

    public void edit(UUID id, ServicesUseCases.Callout r, String code) {
        int total = r.totalVolunteers();
        db.update(
                "UPDATE callouts SET title=?,description=?,start_at=?,end_at=?,deadline=?,location=?,requirements=?,capacity=?,minimum_participants=?,kind=?,area=?,service_code=?,service_type=?,socorrista_slots=?,radio_slots=?,pilot_slots=?,graduate_slots=?,trainee_slots=0,total_volunteers=? WHERE id=?",
                r.title(),
                r.description(),
                r.start(),
                r.end(),
                r.deadline(),
                r.location(),
                r.requirements(),
                total,
                total,
                r.kind(),
                r.area(),
                code,
                r.serviceType(),
                total - r.radioSlots() - r.pilotSlots(),
                r.radioSlots(),
                r.pilotSlots(),
                r.graduateSlots(),
                total,
                id);
    }

    public void recordCode(UUID id, String code, String type, UUID actor) {
        if (code != null)
            db.update(
                    "INSERT INTO service_code_history(id,callout_id,service_code,service_type,assigned_by) VALUES (?,?,?,?,?)",
                    UUID.randomUUID(),
                    id,
                    code,
                    type,
                    actor);
    }

    public List<Map<String, Object>> codes() {
        return rows(
                "SELECT h.*,u.name AS assigned_name FROM service_code_history h LEFT JOIN users u ON u.id=h.assigned_by ORDER BY h.assigned_at DESC,h.id DESC LIMIT 100");
    }

    public void status(UUID id, String status) {
        db.update("UPDATE callouts SET status=? WHERE id=?", status, id);
    }

    public void cancel(UUID id, String reason) {
        db.update("UPDATE callouts SET status='CANCELADA',observations=? WHERE id=?", reason, id);
        db.update(
                "UPDATE enrollments SET status='CANCELADA',comment=? WHERE callout_id=? AND status IN ('CONFIRMADA','PENDIENTE','PENDIENTE_REEMPLAZO')",
                reason,
                id);
    }

    public void operation(UUID id, String action, String observations, LocalDateTime at) {
        if (action.equals("ABRIR"))
            db.update(
                    "UPDATE callouts SET opened_at=?,observations=? WHERE id=?",
                    at,
                    observations,
                    id);
        else
            db.update(
                    "UPDATE callouts SET closed_at=?,status='FINALIZADA',observations=? WHERE id=?",
                    at,
                    observations,
                    id);
    }

    public List<Map<String, Object>> responsibleCandidates(UUID id) {
        return rows(
                "SELECT u.id,u.name,u.surnames FROM users u JOIN enrollments e ON e.volunteer_id=u.id WHERE u.status='ACTIVO' AND u.role='VOLUNTARIO' AND u.graduated=true AND e.callout_id=? AND e.status='CONFIRMADA' AND e.position IN ('SOCORRISTA','GRADUADO','FORMACION') AND e.cancellation_requested_at IS NULL ORDER BY u.name,u.surnames",
                id);
    }

    public void changeResponsible(
            UUID id, UUID previous, UUID next, UUID actor, LocalDateTime at, String reason) {
        db.update(
                "UPDATE enrollments SET position='SOCORRISTA',comment='Responsabilidad retirada; conserva su inscripción como voluntario' WHERE callout_id=? AND position='RESPONSABLE' AND status='CONFIRMADA'",
                id);
        if (next != null)
            db.update(
                    "UPDATE enrollments SET position='RESPONSABLE',reviewed_at=?,assigned_by=?,assigned_at=?,requirements_accepted=true,comment='Responsabilidad reasignada por jefatura' WHERE callout_id=? AND volunteer_id=?",
                    at,
                    actor,
                    at,
                    id,
                    next);
        auditResponsible(id, previous, next, actor, at, reason);
        db.update("UPDATE callouts SET responsible_id=? WHERE id=?", next, id);
    }

    public void synchronizeResponsible(
            UUID id, UUID previous, UUID next, UUID actor, LocalDateTime at) {
        if (!Objects.equals(previous, next))
            auditResponsible(
                    id,
                    previous,
                    next,
                    actor,
                    at,
                    "Actualización por revisión de inscripción, cuenta o habilitaciones");
        db.update("UPDATE callouts SET responsible_id=? WHERE id=?", next, id);
    }

    private void auditResponsible(
            UUID id, UUID previous, UUID next, UUID actor, LocalDateTime at, String reason) {
        db.update(
                "INSERT INTO responsible_history(id,callout_id,previous_user_id,next_user_id,changed_by,changed_at,reason) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                id,
                previous,
                next,
                actor,
                at,
                reason);
    }

    public List<Map<String, Object>> responsibleHistory(UUID id) {
        return rows(
                "SELECT h.*,u.name AS changed_name,o.name AS previous_name,n.name AS next_name FROM responsible_history h JOIN users u ON u.id=h.changed_by LEFT JOIN users o ON o.id=h.previous_user_id LEFT JOIN users n ON n.id=h.next_user_id WHERE h.callout_id=? ORDER BY h.changed_at DESC,h.id",
                id);
    }

    public void photo(UUID id, byte[] bytes, String type, UUID actor, LocalDateTime at) {
        db.update(
                "INSERT INTO callout_photo_archive(id,callout_id,photo,photo_type,uploaded_by,uploaded_at) SELECT ?,id,team_photo,photo_type,photo_uploaded_by,photo_uploaded_at FROM callouts WHERE id=? AND team_photo IS NOT NULL",
                UUID.randomUUID(),
                id);
        db.update(
                "UPDATE callouts SET team_photo=?,photo_type=?,photo_uploaded_by=?,photo_uploaded_at=? WHERE id=?",
                bytes,
                type,
                actor,
                at,
                id);
    }

    public TeamPhoto photo(UUID id) {
        var c = row("SELECT team_photo,photo_type FROM callouts WHERE id=?", id);
        if (c.get("team_photo") == null)
            throw new pe.cruzroja.convocatorias.shared.application.NotFound();
        return new TeamPhoto((byte[]) c.get("team_photo"), (String) c.get("photo_type"));
    }

    public List<Map<String, Object>> photos(UUID user) {
        return rows(
                "SELECT c.id,c.title,c.start_at FROM callouts c WHERE c.team_photo IS NOT NULL AND EXISTS(SELECT 1 FROM attendance_records a WHERE a.callout_id=c.id AND a.volunteer_id=? AND a.check_in IS NOT NULL) ORDER BY c.start_at DESC",
                user);
    }
}
