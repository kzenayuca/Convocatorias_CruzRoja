package pe.cruzroja.convocatorias.operations.infrastructure;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.cruzroja.convocatorias.operations.infrastructure.persistence.AttendanceEntity;
import pe.cruzroja.convocatorias.shared.infrastructure.ManagedEntities;

/** Monthly snapshots, append-only decisions and historical SQL remain explicit during migration. */
@Repository
@Transactional
public class JpaAttendanceRepository extends JdbcAttendanceRepository {
    private final ManagedEntities entities;

    public JpaAttendanceRepository(JdbcTemplate db, ManagedEntities entities) {
        super(db);
        this.entities = entities;
    }

    @Override
    public void mark(UUID id, String action, LocalDateTime at) {
        entities.lock(AttendanceEntity.class, id).storeMark(action, at);
        entities.flush();
    }

    @Override
    public void confirm(
            UUID id,
            LocalDateTime in,
            LocalDateTime out,
            double hours,
            UUID actor,
            String observations) {
        entities.lock(AttendanceEntity.class, id)
                .storeConfirmation(in, out, hours, actor, observations);
        entities.flush();
    }
}
