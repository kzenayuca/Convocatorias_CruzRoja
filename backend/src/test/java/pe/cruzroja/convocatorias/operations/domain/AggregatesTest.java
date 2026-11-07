package pe.cruzroja.convocatorias.operations.domain;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import pe.cruzroja.convocatorias.membership.domain.Volunteer;
import pe.cruzroja.convocatorias.administration.domain.HourTargets;
import java.math.BigDecimal;

class AggregatesTest {
    final LocalDateTime start=LocalDateTime.of(2030,1,10,8,0);
    final UUID first=UUID.randomUUID(),second=UUID.randomUUID();
    Callout callout(List<Callout.Member> team,UUID responsible) { return new Callout(UUID.randomUUID(),new ServiceWindow(start,start.plusHours(3),start.minusDays(1)),"SERVICIO","ABIERTA",responsible,null,null,new TeamCapacity(4,1,1,1),team); }
    @Test void calloutAggregateProtectsInclusiveCapacityAndRequiredFunctions() {
        var team=List.of(new Callout.Member(first,"RESPONSABLE",true),new Callout.Member(second,"SOCORRISTA",false));
        var c=callout(team,first); assertTrue(c.fits()); assertFalse(c.complete()); assertFalse(c.canAdmit(false,"SOCORRISTA",null));
        assertTrue(c.canAdmit(true,"PILOTO",null)); assertTrue(c.canAdmit(true,"RADIOPERADOR",null));
        var full=callout(List.of(team.get(0),team.get(1),new Callout.Member(UUID.randomUUID(),"PILOTO",true),new Callout.Member(UUID.randomUUID(),"RADIOPERADOR",true)),first);
        assertTrue(full.complete()); assertFalse(full.canAdmit(true,"SOCORRISTA",null));
    }
    @Test void responsibilityRequiresExplicitChangeAndCurrentIdentity() {
        var c=callout(List.of(),first); assertThrows(IllegalArgumentException.class,()->c.requireInitialResponsible(second));
        assertThrows(IllegalArgumentException.class,()->c.requireResponsibleChange(second,null,"Pantalla desactualizada"));
        assertThrows(IllegalArgumentException.class,()->c.requireResponsibleChange(first,second," "));
        assertDoesNotThrow(()->c.requireResponsibleChange(first,null,"Retiro explícito"));
    }
    @Test void enrollmentRequiresAcceptedRequirementsAndAnOpenPeriod() {
        var c=callout(List.of(),null); assertThrows(IllegalArgumentException.class,()->c.requireEnrollment(false,false,false,start.minusDays(2)));
        assertThrows(IllegalArgumentException.class,()->c.requireEnrollment(true,true,false,start.minusHours(1)));
        assertDoesNotThrow(()->c.requireEnrollment(true,false,true,start.minusHours(1)));
    }
    @Test void specialistEligibilityIsNotAGlobalAdministrativeRole() {
        assertFalse(new Participant(true,false,true,true,true).qualified("SOCORRISTA"));
        assertFalse(new Participant(true,true,false,true,true).qualified("PILOTO"));
        assertTrue(new Participant(true,true,true,true,false).qualified("RADIOPERADOR"));
        assertFalse(new Participant(false,true,true,true,true).qualified("RESPONSABLE"));
    }
    @Test void serviceWindowsAllowAdjacentActivitiesButNotOverlap() {
        var a=new ServiceWindow(start,start.plusHours(3),start); assertFalse(a.overlaps(new ServiceWindow(start.plusHours(3),start.plusHours(4),start)));
        assertTrue(a.overlaps(new ServiceWindow(start.plusHours(2),start.plusHours(4),start)));
        assertThrows(IllegalArgumentException.class,()->new ServiceWindow(start,start.plusHours(49),start).validatePlanning(start.minusDays(1)));
        assertDoesNotThrow(()->new ServiceWindow(start.minusYears(10),start.minusYears(10).plusHours(72),start.minusYears(10)));
    }
    @Test void attendanceConfirmationAndJustificationNeverInventTime() {
        var a=new Attendance(first,new ServiceWindow(start,start.plusHours(3),start),"CONFIRMADA",start.plusMinutes(20),2,false,false);
        assertEquals(40.0/60,a.deduction(false),.0001); assertEquals(0,a.deduction(true));
        assertEquals(2+40.0/60,a.confirm(start.plusMinutes(20),start.plusHours(3),null,false,start.plusDays(1)),.0001);
        assertThrows(IllegalArgumentException.class,()->a.confirm(start,start.plusHours(3),8.0,false,start.plusDays(1)));
        assertEquals(0,new Attendance(first,a.service(),"AUSENTE",null,2,true,false).deduction(false));
    }
    @Test void resolutionIsAnImmutableAuditableFactAndHasASignedDelta() {
        var j=new Justification(first,"PENDIENTE"); var d=j.resolve(true,"Evidencia comprobada",second,start,2,0);
        assertEquals(-2,d.deductionDelta()); assertEquals("APROBADA",d.status()); assertEquals("PENDIENTE",j.status()); assertEquals(second,d.actor());
        assertEquals(0,new Justification(first,"APROBADA").resolve(true,"Revisión",second,start,0,0).deductionDelta());
    }
    @Test void monthlySnapshotAndPostCloseAdjustmentsRemainDistinct() {
        var balance=new MonthlyBalance(3,1,2,-1,false); assertEquals(2,balance.originalHours()); assertEquals(3,balance.hours()); assertEquals(0,balance.deductions());
        assertEquals(2,new MonthlyBalance(3,1,2,0,false).hours()); assertEquals(0,new MonthlyBalance(0,6,-6,0,true).hours());
    }
    @Test void materialReceiptAcceptsExactlyRequestedQuantity() {
        assertDoesNotThrow(() ->
            new MaterialRequest(UUID.randomUUID(), 3, 0, false, false)
                .requireHandover("RECEPCION", 3));
    }
    @Test void materialRequestCannotReceiveOrReturnMoreThanAllowed() {
        assertThrows(IllegalArgumentException.class,()->new MaterialRequest(first,3,0,false,false).requireHandover("RECEPCION",4));
        assertDoesNotThrow(()->new MaterialRequest(first,3,2,true,false).requireHandover("DEVOLUCION",1));
        assertThrows(IllegalArgumentException.class,()->new MaterialRequest(first,3,2,true,true).requireHandover("DEVOLUCION",1));
    }
    @Test void volunteerAggregateProtectsAdministrationAndTargetsStayPositive() {
        assertThrows(IllegalArgumentException.class,()->new Volunteer(first,"RECLUTADOR","ACTIVO",true,true,true));
        assertThrows(IllegalArgumentException.class,()->new Volunteer(first,"VOLUNTARIO","ACTIVO",true,false,false).protectOwnAdministration(first));
        assertDoesNotThrow(()->new HourTargets(new BigDecimal("120"),new BigDecimal("200")));
        assertThrows(IllegalArgumentException.class,()->new HourTargets(BigDecimal.ZERO,BigDecimal.TEN));
    }
    @Test
    void attendanceConfirmCreditsScheduledTwoHours() {
        var start = LocalDateTime.of(2029, 12, 31, 9, 0);
        var end = start.plusHours(2);
        var attendance = new Attendance(
            UUID.randomUUID(),
            new ServiceWindow(start, end, start.minusDays(1)),
            "PENDIENTE_REVISION", null, 2, false, false);
        double hours = attendance.confirm(
            start, end, null, false, end.plusHours(1));
        assertEquals(2.0, hours, 0.0001);
    }
    @Test void calloutCannotAdmitMemberWhenInclusiveTotalIsFull() {
        var start = LocalDateTime.of(2030, 1, 10, 8, 0);
        var members = List.of( new Callout.Member(UUID.randomUUID(), "SOCORRISTA", false), new Callout.Member(UUID.randomUUID(), "SOCORRISTA", false));
        var callout = new Callout( UUID.randomUUID(), new ServiceWindow(start, start.plusHours(3), start.minusDays(1)), "SERVICIO", "ABIERTA", null, null, null, new TeamCapacity(2, 0, 0, 0), members);
        assertFalse(callout.canAdmit(false, "FORMACION", null));
    }
}
