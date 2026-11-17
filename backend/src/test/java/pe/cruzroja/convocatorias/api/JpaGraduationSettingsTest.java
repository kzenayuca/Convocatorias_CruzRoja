package pe.cruzroja.convocatorias.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import pe.cruzroja.convocatorias.operations.infrastructure.JpaGraduationSettings;

class JpaGraduationSettingsTest {
    @Test
    @SuppressWarnings("unchecked")
    void repositoryMapsEachConditionToItsConfiguredHours() {
        var entities = mock(EntityManager.class);
        TypedQuery<BigDecimal> query = mock(TypedQuery.class);
        when(entities.createQuery(anyString(), eq(BigDecimal.class))).thenReturn(query);
        when(query.setParameter(eq("condition"), anyString())).thenReturn(query);
        when(query.getSingleResult())
                .thenReturn(new BigDecimal("150.25"), new BigDecimal("210.75"));
        var settings = new JpaGraduationSettings(entities);

        assertEquals(150.25, settings.requiredHours(false));
        assertEquals(210.75, settings.requiredHours(true));
        verify(query).setParameter("condition", "FORMACION");
        verify(query).setParameter("condition", "GRADUADO");
    }
}
