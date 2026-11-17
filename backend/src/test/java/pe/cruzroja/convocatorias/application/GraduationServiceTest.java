package pe.cruzroja.convocatorias.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.cruzroja.convocatorias.operations.application.GraduationService;
import pe.cruzroja.convocatorias.operations.application.GraduationSettings;

@ExtendWith(MockitoExtension.class)
class GraduationServiceTest {
    @Mock GraduationSettings settings;
    GraduationService service;

    @BeforeEach
    void setUp() {
        service = new GraduationService(settings);
    }

    @Test
    void progressUsesTheRequirementFromRepository() {
        when(settings.requiredHours(false)).thenReturn(120.0);
        var result = service.progress(35, 5, 1, false);
        assertEquals(90, result.remaining());
        assertEquals(30, result.balance());
        verify(settings).requiredHours(false);
    }

    @Test
    void configurationMayStillBePending() {
        when(settings.requiredHours(true)).thenReturn(null);
        assertNull(service.progress(5, 0, 0, true).required());
        verify(settings).requiredHours(true);
    }

    @Test
    void graduatesUseTheirOwnTargetWithoutChangingTheBalance() {
        when(settings.requiredHours(true)).thenReturn(200.0);
        var result = service.progress(35, 5, 1, true);
        assertEquals(170, result.remaining());
        assertEquals(30, result.balance());
        verify(settings).requiredHours(true);
    }
}
