package pe.cruzroja.convocatorias.administration.infrastructure;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.cruzroja.convocatorias.administration.domain.AdministrationRepository;
import pe.cruzroja.convocatorias.administration.domain.HourTargets;
import pe.cruzroja.convocatorias.shared.infrastructure.JdbcSupport;

public class JdbcAdministrationRepository extends JdbcSupport implements AdministrationRepository {
    public JdbcAdministrationRepository(JdbcTemplate db) {
        super(db);
    }

    public Map<String, Object> settings() {
        return row(
                "SELECT MAX(CASE WHEN condition='FORMACION' THEN hours END) AS training_hours,MAX(CASE WHEN condition='GRADUADO' THEN hours END) AS graduate_hours,MAX(updated_at) AS updated_at FROM volunteer_hour_targets");
    }

    public void save(HourTargets t, UUID actor, LocalDateTime at) {
        db.update(
                "UPDATE volunteer_hour_targets SET hours=?,updated_by=?,updated_at=? WHERE condition='FORMACION'",
                t.trainingHours(),
                actor,
                at);
        db.update(
                "UPDATE volunteer_hour_targets SET hours=?,updated_by=?,updated_at=? WHERE condition='GRADUADO'",
                t.graduateHours(),
                actor,
                at);
    }
}
