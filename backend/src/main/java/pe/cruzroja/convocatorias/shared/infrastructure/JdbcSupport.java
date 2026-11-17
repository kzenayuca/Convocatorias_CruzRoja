package pe.cruzroja.convocatorias.shared.infrastructure;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.cruzroja.convocatorias.shared.application.NotFound;

public abstract class JdbcSupport {
    protected final JdbcTemplate db;

    protected JdbcSupport(JdbcTemplate db) {
        this.db = db;
    }

    protected Map<String, Object> row(String sql, Object... args) {
        var rows = rows(sql, args);
        if (rows.isEmpty()) throw new NotFound();
        return rows.get(0);
    }

    protected List<Map<String, Object>> rows(String sql, Object... args) {
        return db.queryForList(sql, args).stream().map(this::normalize).toList();
    }

    private Map<String, Object> normalize(Map<String, Object> row) {
        var result = new LinkedHashMap<String, Object>();
        row.forEach(
                (k, v) ->
                        result.put(
                                k,
                                v instanceof java.sql.Timestamp t
                                        ? t.toLocalDateTime()
                                        : v instanceof java.sql.Date d ? d.toLocalDate() : v));
        return result;
    }
}
