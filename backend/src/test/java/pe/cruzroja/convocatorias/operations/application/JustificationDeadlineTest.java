package pe.cruzroja.convocatorias.operations.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import pe.cruzroja.convocatorias.shared.application.*;

class JustificationDeadlineTest {
    @Test void absenceAndTardinessAcceptExactly72HoursAndRejectTheNextSecond() {
        for(boolean tardy:List.of(false,true)) for(boolean expired:List.of(false,true)) {
            UUID user=UUID.randomUUID(),attendance=UUID.randomUUID(),callout=UUID.randomUUID(); var end=LocalDateTime.of(2030,1,2,11,0); var start=end.minusHours(3);
            var context=mock(ExecutionContext.class); when(context.actor()).thenReturn(new Actor(user,"VOLUNTARIO",false)); when(context.now()).thenReturn(end.plusHours(72).plusSeconds(expired?1:0));
            var repo=mock(AttendanceRepository.class); var workflow=mock(CalloutWorkflow.class); var notifications=mock(NotificationPublisher.class);
            var a=new HashMap<String,Object>(Map.of("id",attendance,"callout_id",callout,"volunteer_id",user,"status",tardy?"CONFIRMADA":"AUSENTE","policy_version",2)); if(tardy) a.put("check_in",start.plusMinutes(20));
            when(repo.find(attendance,false)).thenReturn(a); when(repo.find(attendance,true)).thenReturn(a); when(workflow.locked(callout)).thenReturn(Map.of("start_at",start,"end_at",end,"title","Guardia"));
            when(repo.justification(any(UUID.class),eq(false))).thenReturn(Map.of("name","Ana","surnames","Torres","kind",tardy?"TARDANZA":"AUSENCIA"));
            var useCase=new AttendanceUseCases(repo,mock(EnrollmentRepository.class),mock(CalloutRepository.class),workflow,mock(ServiceMaintenance.class),mock(MonthlyLedger.class),mock(GraduationSettings.class),mock(ImageInspector.class),context,notifications);
            var reason=new AttendanceUseCases.Reason("Motivo presentado a tiempo");
            if(expired) { assertThrows(IllegalArgumentException.class,()->useCase.justify(attendance,reason)); verify(repo,never()).justify(any(),any(),any(),any(),any(),anyBoolean(),any()); }
            else { useCase.justify(attendance,reason); verify(repo).justify(any(),eq(attendance),eq(user),eq(reason.reason()),eq(end.plusHours(72)),eq(false),eq(tardy?"TARDANZA":"AUSENCIA")); }
        }
    }
}
