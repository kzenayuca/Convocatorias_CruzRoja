package pe.cruzroja.convocatorias.operations.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.cruzroja.convocatorias.operations.domain.EnrollmentRepository;
import pe.cruzroja.convocatorias.shared.application.*;

/**
 * Pruebas de caracterización de marcación, revisión de asistencia y rechazo de revisiones. Fijan el
 * comportamiento actual antes de refactorizar (issue #70).
 */
class AttendanceUseCasesTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 10, 9, 0);

    private final UUID volunteer = UUID.randomUUID();
    private final UUID chief = UUID.randomUUID();
    private final UUID responsible = UUID.randomUUID();
    private final UUID enrollmentId = UUID.randomUUID();
    private final UUID calloutId = UUID.randomUUID();
    private final UUID attendanceId = UUID.randomUUID();

    private ExecutionContext context;
    private NotificationPublisher notifications;
    private AttendanceRecordRepository attendanceRecords;
    private AttendanceReviewRepository attendanceReviews;
    private AttendanceDirectoryRepository userDirectory;
    private EnrollmentRepository enrollments;
    private CalloutWorkflow workflow;
    private AttendanceUseCases useCases;

    private Map<String, Object> enrollment;
    private Map<String, Object> callout;
    private Map<String, Object> attendance;

    @BeforeEach
    void setUp() {
        context = mock(ExecutionContext.class);
        notifications = mock(NotificationPublisher.class);
        attendanceRecords = mock(AttendanceRecordRepository.class);
        attendanceReviews = mock(AttendanceReviewRepository.class);
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

        callout = new HashMap<>();
        callout.put("id", calloutId);
        callout.put("kind", "SERVICIO");
        callout.put("status", "ABIERTA");
        callout.put("title", "Guardia de prueba");
        callout.put("opened_at", NOW.minusMinutes(30));
        callout.put("start_at", NOW);
        callout.put("end_at", NOW.plusHours(4));
        callout.put("responsible_id", responsible);

        attendance = new HashMap<>();
        attendance.put("id", attendanceId);

        when(enrollments.find(enrollmentId, false)).thenReturn(enrollment);
        when(workflow.locked(calloutId)).thenReturn(callout);
        when(attendanceRecords.byEnrollment(enrollmentId)).thenReturn(attendance);
        when(userDirectory.chiefs()).thenReturn(List.of(chief));
        when(userDirectory.userName(volunteer)).thenReturn("Ana Torres");

        useCases =
                new AttendanceUseCases(
                        attendanceRecords,
                        attendanceReviews,
                        mock(JustificationRepository.class),
                        mock(MonthlyReportRepository.class),
                        userDirectory,
                        enrollments,
                        mock(CalloutRepository.class),
                        workflow,
                        mock(ServiceMaintenance.class),
                        mock(MonthlyLedger.class),
                        mock(GraduationSettings.class),
                        mock(ImageInspector.class),
                        context,
                        notifications);
    }

    private AttendanceUseCases.Mark entrada() {
        return new AttendanceUseCases.Mark("ENTRADA");
    }

    private AttendanceUseCases.Mark salida() {
        return new AttendanceUseCases.Mark("SALIDA");
    }

    // ---------- mark ----------

    @Test
    void mark_inscripcionDeOtroVoluntario_lanzaAccessDeniedSinMarcar() {
        enrollment.put("volunteer_id", UUID.randomUUID());

        assertThrows(AccessDenied.class, () -> useCases.mark(enrollmentId, entrada()));
        verify(attendanceRecords, never()).mark(any(), any(), any());
    }

    @Test
    void mark_inscripcionNoConfirmada_rechazaLaMarcacion() {
        enrollment.put("status", "PENDIENTE");

        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> useCases.mark(enrollmentId, entrada()));
        assertTrue(error.getMessage().contains("inscripción confirmada"));
        verify(attendanceRecords, never()).mark(any(), any(), any());
    }

    @Test
    void mark_servicioSinAbrir_rechazaLaMarcacion() {
        callout.remove("opened_at");

        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> useCases.mark(enrollmentId, entrada()));
        assertTrue(error.getMessage().contains("abrir el servicio"));
    }

    @Test
    void mark_fueraDeLaVentanaDelServicio_rechazaLaMarcacion() {
        when(context.now()).thenReturn(NOW.plusHours(6));

        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> useCases.mark(enrollmentId, entrada()));
        assertTrue(error.getMessage().contains("fuera de la ventana"));
    }

    @Test
    void mark_entradaValida_registraLaMarcaSinNotificar() {
        useCases.mark(enrollmentId, entrada());

        verify(attendanceRecords).prepareMark(enrollmentId, volunteer, calloutId, responsible);
        verify(attendanceRecords).mark(attendanceId, "ENTRADA", NOW);
        verifyNoInteractions(notifications);
    }

    @Test
    void mark_servicioSinResponsable_usaAlVoluntarioComoRevisor() {
        callout.remove("responsible_id");

        useCases.mark(enrollmentId, entrada());

        verify(attendanceRecords).prepareMark(enrollmentId, volunteer, calloutId, volunteer);
    }

    @Test
    void mark_entradaRepetida_rechazaLaMarcacion() {
        attendance.put("check_in", NOW.minusMinutes(5));

        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> useCases.mark(enrollmentId, entrada()));
        assertTrue(error.getMessage().contains("Ya registraste"));
        verify(attendanceRecords, never()).mark(any(), any(), any());
    }

    @Test
    void mark_salidaSinEntrada_rechazaLaMarcacion() {
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> useCases.mark(enrollmentId, salida()));
        assertTrue(error.getMessage().contains("Registra primero tu entrada"));
        verify(attendanceRecords, never()).mark(any(), any(), any());
    }

    @Test
    void mark_accionDesconocida_rechazaLaMarcacion() {
        assertThrows(
                IllegalArgumentException.class,
                () -> useCases.mark(enrollmentId, new AttendanceUseCases.Mark("DESCANSO")));
        verify(attendanceRecords, never()).mark(any(), any(), any());
    }

    @Test
    void mark_salidaValida_registraYNotificaARevisoresMenosAlVoluntario() {
        attendance.put("check_in", NOW.minusHours(1));
        String link = "/callouts/" + calloutId + "?attendance=" + attendanceId;

        useCases.mark(enrollmentId, salida());

        verify(attendanceRecords).mark(attendanceId, "SALIDA", NOW);
        verify(notifications).publish(eq(chief), eq("REVISION"), contains("Ana Torres"), eq(link));
        verify(notifications)
                .publish(eq(responsible), eq("REVISION"), contains("Ana Torres"), eq(link));
        verify(notifications, never()).publish(eq(volunteer), any(), any(), any());
    }

    // ---------- correction ----------

    @Test
    void correction_conRevisionPendiente_noCreaOtraSolicitud() {
        when(attendanceReviews.reviews(attendanceId))
                .thenReturn(List.of(Map.of("status", "PENDIENTE")));

        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                useCases.correction(
                                        enrollmentId,
                                        new AttendanceUseCases.Reason("Olvidé marcar")));
        assertTrue(error.getMessage().contains("pendiente"));
        verify(attendanceReviews, never()).createReview(any(), any(), any(), any(), any());
    }

    @Test
    void correction_servicioNoIniciado_rechazaLaSolicitud() {
        callout.put("start_at", NOW.plusHours(1));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        useCases.correction(
                                enrollmentId, new AttendanceUseCases.Reason("Olvidé marcar")));
        verify(attendanceRecords, never()).correction(any(), any(), any(), any(), any());
    }

    @Test
    void correction_valida_creaRevisionYNotificaARevisoresYAlSolicitante() {
        useCases.correction(enrollmentId, new AttendanceUseCases.Reason("Olvidé marcar"));

        verify(attendanceRecords)
                .correction(enrollmentId, volunteer, calloutId, responsible, "Olvidé marcar");
        verify(attendanceReviews)
                .createReview(
                        any(UUID.class),
                        eq(attendanceId),
                        eq(volunteer),
                        eq("Olvidé marcar"),
                        eq(NOW));
        verify(notifications)
                .publish(
                        eq(chief),
                        eq("REVISION"),
                        contains("solicita revisión"),
                        eq("/callouts/" + calloutId + "?attendance=" + attendanceId));
        verify(notifications).publish(eq(volunteer), eq("REVISION"), contains("recibida"), any());
    }

    // ---------- rejectReview ----------

    @Test
    void rejectReview_intentoDeAprobar_seRechaza() {
        UUID reviewId = givenPendingReview();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        useCases.rejectReview(
                                reviewId, new AttendanceUseCases.Resolution(true, "Correcto")));
        verify(attendanceReviews, never()).resolveReview(any());
    }

    @Test
    void rejectReview_valida_resuelveYNotificaAlVoluntario() {
        UUID reviewId = givenPendingReview();

        useCases.rejectReview(
                reviewId, new AttendanceUseCases.Resolution(false, "Marcas correctas"));

        verify(attendanceReviews)
                .resolveReview(
                        argThat(
                                decision ->
                                        decision.status().equals("RECHAZADA")
                                                && decision.request().equals(reviewId)));
        verify(notifications)
                .publish(
                        eq(volunteer),
                        eq("REVISION"),
                        contains("rechazada"),
                        eq("/services?review=" + reviewId));
    }

    private UUID givenPendingReview() {
        when(context.actor()).thenReturn(new Actor(chief, "JEFATURA", false));
        UUID reviewId = UUID.randomUUID();
        var review = new HashMap<String, Object>();
        review.put("id", reviewId);
        review.put("callout_id", calloutId);
        review.put("volunteer_id", volunteer);
        review.put("status", "PENDIENTE");
        when(attendanceReviews.review(reviewId, false)).thenReturn(review);
        when(attendanceReviews.review(reviewId, true)).thenReturn(review);
        return reviewId;
    }
}
