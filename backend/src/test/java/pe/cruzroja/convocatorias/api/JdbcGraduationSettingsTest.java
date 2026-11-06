package pe.cruzroja.convocatorias.api;
import pe.cruzroja.convocatorias.operations.infrastructure.JdbcGraduationSettings;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JdbcGraduationSettingsTest {
    @Test void repositoryMapsTheConfiguredHours() {
        var db=mock(JdbcTemplate.class); when(db.queryForObject("SELECT hours FROM volunteer_hour_targets WHERE condition=?",Double.class,"FORMACION")).thenReturn(150.0);
        when(db.queryForObject("SELECT hours FROM volunteer_hour_targets WHERE condition=?",Double.class,"GRADUADO")).thenReturn(200.0);
        assertEquals(150,new JdbcGraduationSettings(db).requiredHours(false));
        assertEquals(200,new JdbcGraduationSettings(db).requiredHours(true));
        verify(db).queryForObject("SELECT hours FROM volunteer_hour_targets WHERE condition=?",Double.class,"FORMACION");
        verify(db).queryForObject("SELECT hours FROM volunteer_hour_targets WHERE condition=?",Double.class,"GRADUADO");
    }
}
