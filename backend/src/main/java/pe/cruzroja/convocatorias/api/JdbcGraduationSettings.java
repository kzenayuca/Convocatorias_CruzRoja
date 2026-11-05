package pe.cruzroja.convocatorias.api;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.cruzroja.convocatorias.domain.GraduationSettings;

@Repository
public class JdbcGraduationSettings implements GraduationSettings {
    private final JdbcTemplate db;
    public JdbcGraduationSettings(JdbcTemplate db) { this.db=db; }
    public Double requiredHours(boolean graduated) {
        return db.queryForObject("SELECT hours FROM volunteer_hour_targets WHERE condition=?",Double.class,graduated?"GRADUADO":"FORMACION");
    }
}
