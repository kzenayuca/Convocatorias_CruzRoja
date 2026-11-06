package pe.cruzroja.convocatorias.bootstrap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import pe.cruzroja.convocatorias.shared.application.*;
import pe.cruzroja.convocatorias.operations.application.*;

class TransactionBoundaryTest {
    @Test void frameworkDecoratorRollsBackWhenAUseCaseFailsAfterSaving() {
        var repository=mock(CalloutRepository.class); var tx=mock(PlatformTransactionManager.class); var status=new SimpleTransactionStatus(); when(tx.getTransaction(any())).thenReturn(status);
        var context=mock(ExecutionContext.class); when(context.actor()).thenReturn(new Actor(UUID.randomUUID(),"JEFATURA",false)); when(context.now()).thenReturn(LocalDateTime.of(2030,1,1,8,0));
        doThrow(new IllegalStateException("Código reservado")).when(repository).recordCode(any(),any(),any(),any());
        when(repository.nextCodeSequence("I",2030)).thenReturn(1);
        var service=new UseCaseConfiguration().services(repository,mock(EnrollmentRepository.class),mock(ServiceMaintenance.class),mock(CalloutWorkflow.class),mock(MonthlyLedger.class),mock(ImageInspector.class),context,mock(NotificationPublisher.class),tx);
        var start=LocalDateTime.of(2030,1,5,8,0);
        var command=new ServicesUseCases.Callout("Guardia","Apoyo comunitario",start,start.plusHours(3),start.minusDays(1),"Arequipa","Curso afín",2,0,0,"SERVICIO","Salud","T-001","Guardia",0);
        assertThrows(IllegalStateException.class,()->service.create(command)); verify(repository).create(any(),eq(command),any(),eq("I-ENE-2030-001")); verify(tx).rollback(status); verify(tx,never()).commit(any());
    }
}
