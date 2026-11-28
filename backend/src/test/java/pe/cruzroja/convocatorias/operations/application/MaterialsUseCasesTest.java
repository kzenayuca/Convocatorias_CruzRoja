package pe.cruzroja.convocatorias.operations.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.cruzroja.convocatorias.shared.application.AccessDenied;
import pe.cruzroja.convocatorias.shared.application.Actor;
import pe.cruzroja.convocatorias.shared.application.ExecutionContext;
import pe.cruzroja.convocatorias.shared.application.NotificationPublisher;

/** JUnit (familia xUnit) y dobles Mockito: sin Spring, HTTP ni PostgreSQL. */
@ExtendWith(MockitoExtension.class)
class MaterialsUseCasesTest {
    private static final UUID CALLOUT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID MATERIAL = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 1, 8, 0);

    @Mock MaterialRepository repository;
    @Mock CalloutRepository callouts;
    @Mock CalloutWorkflow workflow;
    @Mock ExecutionContext context;
    @Mock NotificationPublisher notifications;
    private MaterialsUseCases useCases;

    @BeforeEach
    void setUp() {
        useCases = new MaterialsUseCases(repository, callouts, workflow, context, notifications);
    }

    @Test
    void catalogRequestUsesTheStoredNameInsteadOfTheSubmittedName() {
        // Arrange: respuestas preparadas (stubs) sobre los mocks.
        var snapshot = service("PUBLICADA", NOW.plusHours(1));
        var request = new MaterialsUseCases.Material(MATERIAL, "Nombre enviado", 2, "Botiquines");
        when(workflow.locked(CALLOUT)).thenReturn(snapshot);
        when(context.now()).thenReturn(NOW);
        when(context.actor()).thenReturn(new Actor(USER, "JEFATURA", false));
        when(repository.name(MATERIAL)).thenReturn("Nombre del catalogo");

        // Act: se ejecuta el caso de uso real, no un mock del caso de uso.
        useCases.request(CALLOUT, request);

        // Assert: autorizacion previa y escritura con el nombre confiable.
        var order = inOrder(workflow, repository);
        order.verify(workflow).locked(CALLOUT);
        order.verify(workflow).operationPermission(snapshot);
        order.verify(repository).name(MATERIAL);
        order.verify(repository).request(CALLOUT, USER, request, "Nombre del catalogo");
    }

    @Test
    void customMaterialKeepsItsNameWithoutLookingUpTheCatalog() {
        // Arrange
        var request = new MaterialsUseCases.Material(null, "Manta termica", 3, "Para emergencias");
        when(workflow.locked(CALLOUT)).thenReturn(service("PUBLICADA", NOW.plusHours(1)));
        when(context.now()).thenReturn(NOW);
        when(context.actor()).thenReturn(new Actor(USER, "JEFATURA", false));

        // Act
        useCases.request(CALLOUT, request);

        // Assert
        verify(repository).request(CALLOUT, USER, request, "Manta termica");
        verify(repository, never()).name(any());
    }

    @Test
    void deniedPermissionPreventsCatalogReadsAndWrites() {
        // Arrange
        var snapshot = service("PUBLICADA", NOW.plusHours(1));
        var denied = new AccessDenied("Sin permiso de operacion");
        var request = new MaterialsUseCases.Material(MATERIAL, "Botiquin", 1, "Completo");
        when(workflow.locked(CALLOUT)).thenReturn(snapshot);
        doThrow(denied).when(workflow).operationPermission(snapshot);

        // Act + Assert
        assertSame(
                denied, assertThrows(AccessDenied.class, () -> useCases.request(CALLOUT, request)));
        verifyNoInteractions(repository, context);
    }

    @Test
    void cancelledServiceCannotReceiveMaterialRequests() {
        assertRequestRejected("CANCELADA", NOW.plusHours(1));
    }

    @Test
    void endedServiceCannotReceiveMaterialRequests() {
        assertRequestRejected("PUBLICADA", NOW.minusSeconds(1));
    }

    @Test
    void serviceEndingExactlyNowCannotReceiveMaterialRequests() {
        assertRequestRejected("PUBLICADA", NOW);
    }

    @Test
    void deniedListingDoesNotReadMaterials() {
        // Arrange
        var snapshot = service("PUBLICADA", NOW.plusHours(1));
        when(callouts.snapshot(CALLOUT, false)).thenReturn(snapshot);
        doThrow(new AccessDenied("Sin permiso")).when(workflow).operationPermission(snapshot);

        // Act + Assert
        assertThrows(AccessDenied.class, () -> useCases.list(CALLOUT));
        verifyNoInteractions(repository);
    }

    private void assertRequestRejected(String status, LocalDateTime end) {
        // Arrange
        var request = new MaterialsUseCases.Material(MATERIAL, "Botiquin", 1, "Completo");
        when(workflow.locked(CALLOUT)).thenReturn(service(status, end));
        when(context.now()).thenReturn(NOW);

        // Act + Assert
        var error =
                assertThrows(
                        IllegalArgumentException.class, () -> useCases.request(CALLOUT, request));
        assertEquals("El servicio ya terminó o se canceló.", error.getMessage());
        verifyNoInteractions(repository);
    }

    private Map<String, Object> service(String status, LocalDateTime end) {
        return Map.of("status", status, "end_at", end);
    }
}
