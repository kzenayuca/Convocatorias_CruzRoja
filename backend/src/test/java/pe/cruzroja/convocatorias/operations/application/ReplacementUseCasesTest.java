package pe.cruzroja.convocatorias.operations.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.cruzroja.convocatorias.operations.domain.EnrollmentRepository;
import pe.cruzroja.convocatorias.shared.application.*;

/**
 * Pruebas de caracterización de solicitudes de reemplazo. Fijan el comportamiento actual antes de
 * refactorizar (issue #70).
 */
class ReplacementUseCasesTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 10, 9, 0);

    private final UUID volunteer = UUID.randomUUID();
    private final UUID chief = UUID.randomUUID();
    private final UUID responsible = UUID.randomUUID();
    private final UUID enrollmentId = UUID.randomUUID();
    private final UUID calloutId = UUID.randomUUID();
    private final UUID requestId = UUID.randomUUID();

    private ExecutionContext context;
    private NotificationPublisher notifications;
    private JustificationRepository justificationRecords;
    private AttendanceDirectoryRepository userDirectory;
    private EnrollmentRepository enrollments;
    private CalloutWorkflow workflow;
    private ReplacementUseCases useCases;

    private Map<String, Object> enrollment;
    private Map<String, Object> callout;
    private Map<String, Object> request;

    @BeforeEach
    void setUp() {
        context = mock(ExecutionContext.class);
        notifications = mock(NotificationPublisher.class);
        justificationRecords = mock(JustificationRepository.class);
        userDirectory = mock(AttendanceDirectoryRepository.class);
        enrollments = mock(EnrollmentRepository.class);
        workflow = mock(CalloutWorkflow.class);
        when(context.actor()).thenReturn(new Actor(volunteer, "VOLUNTARIO", false));
        when(context.now()).thenReturn(NOW);

        enrollment = new HashMap<>();
        enrollment.put("id", enrollmentId);
        enrollment.put("volunteer_id", volunteer);
        enrollment.put("callout_id", calloutId);
        enrollment.put("status", "CONFIRMADA");
        enrollment.put("position", "SOCORRISTA");

        callout = new HashMap<>();
        callout.put("id", calloutId);
        callout.put("kind", "SERVICIO");
        callout.put("status", "ABIERTA");
        callout.put("title", "Guardia de prueba");
        callout.put("start_at", NOW.plusDays(5));
        callout.put("end_at", NOW.plusDays(5).plusHours(4));
        callout.put("responsible_id", responsible);

        request = new HashMap<>();
        request.put("id", requestId);
        request.put("enrollment_id", enrollmentId);
        request.put("callout_id", calloutId);
        request.put("volunteer_id", volunteer);
        request.put("name", "Ana");
        request.put("surnames", "Torres");
        request.put("kind", "REEMPLAZO");
        request.put("status", "PENDIENTE");

        when(enrollments.find(enrollmentId)).thenReturn(enrollment);
        when(enrollments.findForUpdate(enrollmentId)).thenReturn(enrollment);
        when(workflow.locked(calloutId)).thenReturn(callout);
        when(userDirectory.chiefs()).thenReturn(List.of(chief));
        when(justificationRecords.justification(any(UUID.class))).thenReturn(request);
        when(justificationRecords.justificationForUpdate(any(UUID.class))).thenReturn(request);

        useCases =
                new ReplacementUseCases(
                        justificationRecords,
                        userDirectory,
                        enrollments,
                        workflow,
                        mock(MonthlyLedger.class),
                        mock(ImageInspector.class),
                        context,
                        notifications);
    }

    private void actingAsChief() {
        when(context.actor()).thenReturn(new Actor(chief, "JEFATURA", false));
    }

    private ReplacementUseCases.Request reason() {
        return new ReplacementUseCases.Request("Viaje familiar", null);
    }

    // ---------- submit ----------

    @Test
    void submit_jefatura_lanzaAccessDeniedSinLeerInscripciones() {
        actingAsChief();

        assertThrows(AccessDenied.class, () -> useCases.submit(enrollmentId, reason(), null));
        verifyNoInteractions(enrollments);
    }

    @Test
    void submit_inscripcionDeOtroVoluntario_lanzaAccessDenied() {
        enrollment.put("volunteer_id", UUID.randomUUID());

        assertThrows(AccessDenied.class, () -> useCases.submit(enrollmentId, reason(), null));
        verify(justificationRecords, never())
                .requestReplacement(any(), any(), any(), any(), any(), any());
    }

    @Test
    void submit_servicioYaAbierto_rechazaLaSolicitud() {
        callout.put("opened_at", NOW);

        assertThrows(
                IllegalArgumentException.class,
                () -> useCases.submit(enrollmentId, reason(), null));
        verify(justificationRecords, never())
                .requestReplacement(any(), any(), any(), any(), any(), any());
    }

    @Test
    void submit_conSolicitudPendiente_noDuplicaLaSolicitud() {
        when(justificationRecords.justifications(volunteer))
                .thenReturn(
                        List.of(
                                Map.of(
                                        "enrollment_id",
                                        enrollmentId,
                                        "kind",
                                        "REEMPLAZO",
                                        "status",
                                        "PENDIENTE")));

        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> useCases.submit(enrollmentId, reason(), null));
        assertTrue(error.getMessage().contains("pendiente"));
        verify(justificationRecords, never())
                .requestReplacement(any(), any(), any(), any(), any(), any());
    }

    @Test
    void submit_valida_registraSolicitudYNotificaAJefaturaResponsableYSolicitante() {
        var result = useCases.submit(enrollmentId, reason(), null);

        UUID created = result.get("id");
        assertNotNull(created);
        verify(justificationRecords)
                .requestReplacement(
                        eq(created),
                        eq(enrollmentId),
                        eq(volunteer),
                        eq("Viaje familiar"),
                        isNull(),
                        eq(NOW));
        verify(enrollments).cancellation(enrollmentId, "Viaje familiar", NOW);
        String link = "/justifications?request=" + created;
        verify(notifications)
                .publish(eq(chief), eq("REEMPLAZO"), contains("solicita reemplazo"), eq(link));
        verify(notifications)
                .publish(
                        eq(responsible), eq("REEMPLAZO"), contains("solicita reemplazo"), eq(link));
        verify(notifications)
                .publish(eq(volunteer), eq("REEMPLAZO"), contains("Solicitud recibida"), eq(link));
    }

    // ---------- candidates ----------

    @Test
    void candidates_sinSerJefatura_lanzaAccessDenied() {
        assertThrows(AccessDenied.class, () -> useCases.candidates(requestId));
        verifyNoInteractions(justificationRecords);
    }

    @Test
    void candidates_solicitudYaResuelta_devuelveListaVacia() {
        actingAsChief();
        request.put("status", "APROBADA");

        assertTrue(useCases.candidates(requestId).isEmpty());
        verify(enrollments, never()).candidates(any());
    }

    // ---------- resolve ----------

    @Test
    void resolve_rechazada_restauraLaInscripcionYNotifica() {
        actingAsChief();

        useCases.resolve(
                requestId, new ReplacementUseCases.Resolution(false, "Sin sustento", null, false));

        verify(enrollments).clearCancellation(enrollmentId);
        verify(justificationRecords)
                .decision(argThat(decision -> decision.status().equals("RECHAZADA")));
        String link = "/justifications?request=" + requestId;
        verify(notifications)
                .publish(eq(volunteer), eq("REEMPLAZO"), contains("rechazada"), eq(link));
        verify(notifications)
                .publish(eq(responsible), eq("REEMPLAZO"), contains("RECHAZADA"), eq(link));
    }

    @Test
    void resolve_solicitudYaResuelta_noModificaLaInscripcion() {
        actingAsChief();
        request.put("status", "APROBADA");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        useCases.resolve(
                                requestId,
                                new ReplacementUseCases.Resolution(
                                        false, "Sin sustento", null, false)));
        verify(enrollments, never()).clearCancellation(any());
    }
}
