package pe.cruzroja.convocatorias.operations.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import pe.cruzroja.convocatorias.shared.application.*;
import pe.cruzroja.convocatorias.membership.application.*;
import pe.cruzroja.convocatorias.communications.application.*;
import pe.cruzroja.convocatorias.communications.domain.Announcement;
import pe.cruzroja.convocatorias.operations.domain.Justification;

/** Ejecuta los casos de uso con puertos falsos: sin HTTP, Spring, JDBC ni PostgreSQL. */
class UseCasesTest {
    final UUID user=UUID.randomUUID(),callout=UUID.randomUUID(),chief=UUID.randomUUID();
    ExecutionContext context; NotificationPublisher notices;
    @BeforeEach void context() { context=mock(ExecutionContext.class); notices=mock(NotificationPublisher.class); when(context.actor()).thenReturn(new Actor(user,"VOLUNTARIO",false)); when(context.now()).thenReturn(LocalDateTime.of(2030,1,1,8,0)); }
    @Test void registrationHashesThroughAPortWithoutTouchingPersistenceDetails() {
        var repo=mock(VolunteerRepository.class); var passwords=mock(PasswordHasher.class); var participation=mock(ServiceParticipation.class); when(passwords.encode("contraseña-segura")).thenReturn("hash");
        var useCase=new AccountsUseCases(repo,passwords,participation,mock(GraduationEligibility.class),mock(AnnualEnrollmentRepository.class),context,notices);
        var r=new AccountsUseCases.Registration(new AccountsUseCases.Profile("Ana","Torres","ana@example.pe","12345678","Estudiante"),"contraseña-segura","900000001");
        useCase.register(r); verify(repo).register(any(UUID.class),eq(r),eq("hash")); verifyNoInteractions(participation);
    }
    @Test void selfAssignmentNeverCallsARepository() {
        var repo=mock(CalloutRepository.class); var enrollments=mock(EnrollmentRepository.class);
        var useCase=new ServicesUseCases(repo,enrollments,mock(ServiceMaintenance.class),mock(CalloutWorkflow.class),mock(MonthlyLedger.class),mock(ImageInspector.class),context,notices);
        assertThrows(AccessDenied.class,()->useCase.assign(callout,new ServicesUseCases.Assignment(user,"SOCORRISTA",true))); verifyNoInteractions(repo,enrollments);
    }
    @Test void schedulePdfIsProtectedWithoutEvenReadingTheSchedule() {
        var repo=mock(CalloutRepository.class);
        var useCase=new ServicesUseCases(repo,mock(EnrollmentRepository.class),mock(ServiceMaintenance.class),mock(CalloutWorkflow.class),mock(MonthlyLedger.class),mock(ImageInspector.class),context,notices);
        assertThrows(AccessDenied.class,()->useCase.schedulePdf(LocalDate.of(2030,1,1))); verifyNoInteractions(repo);
    }
    @Test void accountReviewCoordinatesServiceLocksBeforeSaving() {
        when(context.actor()).thenReturn(new Actor(chief,"JEFATURA",false)); var repo=mock(VolunteerRepository.class); var participation=mock(ServiceParticipation.class);
        when(repo.profile(user)).thenReturn(Map.of("graduated",true));
        var useCase=new AccountsUseCases(repo,mock(PasswordHasher.class),participation,mock(GraduationEligibility.class),mock(AnnualEnrollmentRepository.class),context,notices);
        useCase.review(user,new AccountsUseCases.Review("VOLUNTARIO",true,"ACTIVO","Verificado",true,false));
        var order=inOrder(repo,participation); order.verify(participation).lockCommitments(user); order.verify(repo).lock(user); order.verify(repo).save(any(),eq("Verificado")); order.verify(participation).reevaluate(user);
    }
    @Test void chiefHasNoHoursTargetWithoutQueryingHours() {
        when(context.actor()).thenReturn(new Actor(chief,"JEFATURA",true)); var repo=mock(AttendanceRepository.class);
        var useCase=new AttendanceUseCases(repo,mock(EnrollmentRepository.class),mock(CalloutRepository.class),mock(CalloutWorkflow.class),mock(ServiceMaintenance.class),mock(MonthlyLedger.class),mock(GraduationSettings.class),mock(ImageInspector.class),context,notices);
        assertEquals(false,((Map<?,?>)useCase.progress()).get("applicable")); verifyNoInteractions(repo);
    }
    @Test void justificationDecisionAppendsAnAdjustmentAfterMonthlyClosure() {
        when(context.actor()).thenReturn(new Actor(chief,"JEFATURA",false)); var repo=mock(AttendanceRepository.class); var workflow=mock(CalloutWorkflow.class); var ledger=mock(MonthlyLedger.class);
        UUID justification=UUID.randomUUID(); var start=LocalDateTime.of(2029,12,1,8,0);
        var j=Map.<String,Object>of("id",justification,"callout_id",callout,"volunteer_id",user,"status","PENDIENTE"); when(repo.justification(justification,false)).thenReturn(j); when(repo.justification(justification,true)).thenReturn(j);
        var c=Map.<String,Object>of("id",callout,"start_at",start); when(workflow.locked(callout)).thenReturn(c); when(ledger.closed(c)).thenReturn(true);
        var attendance=new HashMap<String,Object>(Map.of("id",UUID.randomUUID(),"start_at",start,"end_at",start.plusHours(3),"status","AUSENTE","policy_version",2,"justification_status","PENDIENTE","justification_kind","AUSENCIA")); when(repo.justificationAttendance(justification)).thenReturn(attendance);
        var useCase=new AttendanceUseCases(repo,mock(EnrollmentRepository.class),mock(CalloutRepository.class),workflow,mock(ServiceMaintenance.class),ledger,mock(GraduationSettings.class),mock(ImageInspector.class),context,notices);
        useCase.resolve(justification,new AttendanceUseCases.Resolution(true,"Evidencia verificada"));
        verify(repo).decision(argThat(d -> d.before()==6 && d.after()==0 && d.actor().equals(chief)));
        verify(repo).adjustment(eq(YearMonth.of(2029,12)),eq(user),any(Justification.Decision.class));
        verify(notices).publish(eq(user),eq("JUSTIFICACION"),contains("aprobada"),eq("/justifications?request="+justification));
    }
    @Test void announcementDeliveryUsesTheNotificationPort() {
        when(context.actor()).thenReturn(new Actor(chief,"JEFATURA",false)); var repo=mock(NoticeRepository.class); when(repo.recipients("TODOS")).thenReturn(List.of(user));
        new NoticesUseCases(repo,context,notices).publish(new Announcement("Aviso","Reunión de equipo","TODOS"));
        verify(repo).save(any(UUID.class),any(Announcement.class),eq(chief)); verify(notices).publish(eq(user),eq("COMUNICADO"),contains("Reunión"),eq("/notifications"));
    }
    @Test
    void attendanceConfirmationPersistsCalculatedHoursOnce() {
        // Arrange: servicio de 09:00 a 11:00 ya terminado y asistencia pendiente
        var repository = mock(AttendanceRepository.class);
        var workflow = mock(CalloutWorkflow.class);
        UUID attendance = UUID.randomUUID();
        var start = LocalDateTime.of(2029, 12, 31, 9, 0);
        var end = start.plusHours(2);
        when(repository.find(attendance, false)).thenReturn(new HashMap<>(Map.of(
            "id", attendance, "callout_id", callout, "volunteer_id", user,
            "status", "PENDIENTE_REVISION", "policy_version", 2)));
        when(workflow.locked(callout)).thenReturn(Map.of(
            "id", callout, "title", "Guardia", "status", "CERRADA",
            "start_at", start, "end_at", end, "deadline", start.minusDays(1)));
        when(repository.reviews(attendance)).thenReturn(List.of());
        when(repository.trainingVolunteer(user)).thenReturn(false);
        var useCase = new AttendanceUseCases(repository, mock(EnrollmentRepository.class), mock(CalloutRepository.class), workflow, mock(ServiceMaintenance.class), mock(MonthlyLedger.class), mock(GraduationSettings.class), mock(ImageInspector.class), context, notices);
        // Act
        useCase.confirm(attendance,
            new AttendanceUseCases.Confirmation(start, end, null, "Marcas verificadas"));
        // Assert: se guardan exactamente las 2 horas calculadas, una sola vez
        verify(repository).confirm(
            eq(attendance), eq(start), eq(end), eq(2.0), eq(user), eq("Marcas verificadas"));
    }
    @Test
    void invalidAttendanceMarksAreNotPersisted() {
        // Arrange
        var repository = mock(AttendanceRepository.class);
        var workflow = mock(CalloutWorkflow.class);
        UUID attendance = UUID.randomUUID();
        var start = LocalDateTime.of(2029, 12, 31, 9, 0);
        var end = start.plusHours(2);
        when(repository.find(attendance, false)).thenReturn(new HashMap<>(Map.of(
            "id", attendance, "callout_id", callout, "volunteer_id", user,
            "status", "PENDIENTE_REVISION", "policy_version", 2)));
        when(workflow.locked(callout)).thenReturn(Map.of(
            "id", callout, "title", "Guardia", "status", "CERRADA",
            "start_at", start, "end_at", end, "deadline", start.minusDays(1)));
        var useCase = new AttendanceUseCases(repository, mock(EnrollmentRepository.class), mock(CalloutRepository.class), workflow, mock(ServiceMaintenance.class), mock(MonthlyLedger.class), mock(GraduationSettings.class), mock(ImageInspector.class), context, notices);
        // Act + Assert: marcas fuera del horario del servicio
        assertThrows(IllegalArgumentException.class,
            () -> useCase.confirm(attendance,
                new AttendanceUseCases.Confirmation(
                    end.plusMinutes(1), end.plusMinutes(2), null, "Fuera de horario")));
        verify(repository, never()).confirm(
            any(), any(), any(), anyDouble(), any(), any());
    }
}
