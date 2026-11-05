package pe.cruzroja.convocatorias.api;

import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/** Solo contra una base explícita de pruebas; no usa el volumen local habitual. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=${TEST_DB_URL}","spring.datasource.username=${TEST_DB_USER:convocatorias}",
    "spring.datasource.password=${TEST_DB_PASSWORD:convocatorias}"})
class WorkflowTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    final UUID volunteer=UUID.fromString("00000000-0000-0000-0000-000000000001");
    final UUID maria=UUID.fromString("00000000-0000-0000-0000-000000000004");
    final UUID lucia=UUID.fromString("00000000-0000-0000-0000-000000000006");
    final UUID chief=UUID.fromString("00000000-0000-0000-0000-000000000003");
    class Client {
        HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        HttpResponse<String> request(String path,String method,Object body) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path));
            if(!method.equals("GET")) {
                var token=json.readTree(http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
                builder.header(token.get("headerName").asText(),token.get("token").asText());
            }
            builder.header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            return http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        }
        JsonNode ok(String path,String method,Object body) throws Exception {
            var r=request(path,method,body); assertTrue(r.statusCode()>=200 && r.statusCode()<300,r.statusCode()+" "+r.body());
            return r.body().isBlank()?json.nullNode():json.readTree(r.body());
        }
        Client login(String email) throws Exception { ok("/auth/login","POST",Map.of("email",email,"password","convocatorias")); return this; }
        void enroll(UUID service,String position) throws Exception { ok("/callouts/"+service+"/enrollments","POST",Map.of("requirementsAccepted",true,"position",position)); }
    }
    UUID service(LocalDateTime start,LocalDateTime end) {
        UUID id=UUID.randomUUID(); boolean past=end.isBefore(Store.now());
        db.update("""
            INSERT INTO callouts(id,title,start_at,end_at,deadline,minimum_level,capacity,minimum_participants,
            status,created_by,service_code,socorrista_slots,opened_at)
            VALUES (?,'Prueba aislada',?,?,?,'BASICO',2,2,?,?,?,1,?)
            """,id,start,end,start,past?"CERRADA":"ABIERTA",chief,"T-"+id,past?start:null);
        return id;
    }
    LocalDateTime freeSlot() {
        var start=Store.now().plusDays(1000).withHour(10).withMinute(0).withSecond(0).withNano(0);
        while(db.queryForObject("SELECT count(*) FROM callouts WHERE start_at<? AND end_at>?",Integer.class,start.plusHours(5),start)>0) start=start.plusDays(1);
        return start;
    }
    UUID enrollment(UUID c,UUID u) { return db.queryForObject("SELECT id FROM enrollments WHERE callout_id=? AND volunteer_id=?",UUID.class,c,u); }
    UUID fixtureEnrollment(UUID c,UUID u) {
        var id=UUID.randomUUID(); db.update("INSERT INTO enrollments(id,volunteer_id,callout_id,status) VALUES (?,?,?,'CONFIRMADA')",id,u,c); return id;
    }

    @Test void accountApprovalAndSessionRevocationRemainProtected() throws Exception {
        var c=new Client(); String email="test"+UUID.randomUUID()+"@demo.pe";
        String document=String.format("%08d",new Random().nextInt(90000000)+10000000);
        c.ok("/auth/register","POST",Map.of("profile",Map.of("name","Prueba","surnames","Cuenta","email",email,"document",document,"occupation","Estudiante"),"phone","900000000","password","convocatorias"));
        assertEquals(401,c.request("/auth/login","POST",Map.of("email",email,"password","convocatorias")).statusCode());
        var admin=new Client().login("jefatura@demo.pe"); UUID id=db.queryForObject("SELECT id FROM users WHERE email=?",UUID.class,email);
        admin.ok("/users/"+id,"PUT",Map.of("role","VOLUNTARIO","level","BASICO","graduated",false,"status","ACTIVO","comment","Revisado"));
        c.login(email); c.ok("/profile/phone","PUT",Map.of("phone","999111222"));
        assertEquals("999111222",c.ok("/profile","GET",null).get("phone").asText());
        assertEquals(403,c.request("/users","GET",null).statusCode());
        admin.ok("/users/"+id,"PUT",Map.of("role","VOLUNTARIO","level","BASICO","graduated",false,"status","SUSPENDIDO","comment","Decisión manual"));
        assertEquals(401,c.request("/profile","GET",null).statusCode());
    }
    @Test void enrollmentIsAutomaticAndRequiresAllPositionsToClose() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(3));
        var c=new Client().login("voluntario@demo.pe"); var responsible=new Client().login("maria@demo.pe");
        assertEquals(400,c.request("/callouts/"+id+"/enrollments","POST",Map.of("requirementsAccepted",true,"position","RESPONSABLE")).statusCode());
        c.enroll(id,"SOCORRISTA");
        assertEquals("CONFIRMADA",db.queryForObject("SELECT status FROM enrollments WHERE id=?",String.class,enrollment(id,volunteer)));
        assertEquals("ABIERTA",db.queryForObject("SELECT status FROM callouts WHERE id=?",String.class,id));
        responsible.enroll(id,"RESPONSABLE");
        assertEquals("CERRADA",db.queryForObject("SELECT status FROM callouts WHERE id=?",String.class,id));
        assertEquals(maria,db.queryForObject("SELECT responsible_id FROM callouts WHERE id=?",UUID.class,id));
        assertTrue(responsible.ok("/callouts/"+id,"GET",null).get("can_operate").asBoolean());
        assertFalse(responsible.ok("/callouts/"+id,"GET",null).get("can_edit").asBoolean());
        assertEquals(400,c.request("/callouts/"+id+"/enrollments","POST",Map.of("requirementsAccepted",true,"position","SOCORRISTA")).statusCode());
        assertEquals(403,c.request("/enrollments/"+enrollment(id,volunteer)+"/review","POST",Map.of("approved",false,"comment","No autorizado")).statusCode());
    }
    @Test void concurrentSubscriptionsCannotOverbook() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(1));
        var a=new Client().login("voluntario@demo.pe"); var b=new Client().login("lucia@demo.pe");
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var r1=pool.submit(() -> a.request("/callouts/"+id+"/enrollments","POST",Map.of("requirementsAccepted",true,"position","SOCORRISTA")).statusCode());
            var r2=pool.submit(() -> b.request("/callouts/"+id+"/enrollments","POST",Map.of("requirementsAccepted",true,"position","SOCORRISTA")).statusCode());
            var results=new ArrayList<>(List.of(r1.get(),r2.get())); Collections.sort(results); assertEquals(List.of(200,400),results);
        } finally { pool.shutdownNow(); }
        assertEquals(1,db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND position='SOCORRISTA' AND status='CONFIRMADA'",Integer.class,id));
    }
    @Test void overlappingOpportunitiesDisappearAndBoundaryIsAllowed() throws Exception {
        var start=freeSlot(); var first=service(start,start.plusHours(3)); var overlap=service(start.plusHours(1),start.plusHours(4)); var boundary=service(start.plusHours(3),start.plusHours(5));
        var c=new Client().login("voluntario@demo.pe"); c.enroll(first,"SOCORRISTA");
        assertEquals(400,c.request("/callouts/"+overlap+"/enrollments","POST",Map.of("requirementsAccepted",true,"position","SOCORRISTA")).statusCode());
        var list=c.ok("/callouts","GET",null);
        assertFalse(list.toString().contains(overlap.toString())); assertTrue(list.toString().contains(boundary.toString()));
        c.enroll(boundary,"SOCORRISTA");
    }
    @Test void missingPositionsCancelAtDeadlineAndDoNotCreateAbsence() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(3)); var c=new Client().login("voluntario@demo.pe"); c.enroll(id,"SOCORRISTA");
        db.update("UPDATE callouts SET deadline=? WHERE id=?",Store.now().minusSeconds(1),id);
        c.ok("/callouts","GET",null);
        assertEquals("CANCELADA",db.queryForObject("SELECT status FROM callouts WHERE id=?",String.class,id));
        assertEquals("CANCELADA",db.queryForObject("SELECT status FROM enrollments WHERE callout_id=?",String.class,id));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM attendance_records WHERE callout_id=?",Integer.class,id));
    }
    @Test void replacementKeepsSeatUntilApprovalAndCancelledOriginalDoesNotCount() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(3));
        var c=new Client().login("voluntario@demo.pe"); var replacement=new Client().login("lucia@demo.pe"); var boss=new Client().login("maria@demo.pe");
        c.enroll(id,"SOCORRISTA"); boss.enroll(id,"RESPONSABLE"); UUID original=enrollment(id,volunteer);
        c.ok("/enrollments/"+original+"/cancel-request","POST",Map.of("reason","No podré asistir; coordino reemplazo"));
        assertEquals("CONFIRMADA",db.queryForObject("SELECT status FROM enrollments WHERE id=?",String.class,original));
        replacement.ok("/callouts/"+id+"/enrollments","POST",Map.of("requirementsAccepted",true,"position","SOCORRISTA","replacementFor",original));
        var incoming=enrollment(id,lucia);
        assertEquals("PENDIENTE_REEMPLAZO",db.queryForObject("SELECT status FROM enrollments WHERE id=?",String.class,incoming));
        boss.ok("/enrollments/"+incoming+"/review","POST",Map.of("approved",true,"comment","Reemplazo compatible aceptado"));
        assertEquals("CANCELADA",db.queryForObject("SELECT status FROM enrollments WHERE id=?",String.class,original));
        assertEquals(2,db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND status='CONFIRMADA'",Integer.class,id));
        assertEquals("CERRADA",db.queryForObject("SELECT status FROM callouts WHERE id=?",String.class,id));
    }
    @Test void chiefCanRejectAndThePositionReopens() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(2)); var admin=new Client().login("jefatura@demo.pe"); var c=new Client().login("voluntario@demo.pe"); var boss=new Client().login("maria@demo.pe");
        c.enroll(id,"SOCORRISTA"); boss.enroll(id,"RESPONSABLE");
        admin.ok("/enrollments/"+enrollment(id,maria)+"/review","POST",Map.of("approved",false,"comment","No ejercerá la responsabilidad"));
        assertNull(db.queryForObject("SELECT responsible_id FROM callouts WHERE id=?",UUID.class,id));
        assertEquals("ABIERTA",db.queryForObject("SELECT status FROM callouts WHERE id=?",String.class,id));
    }
    @Test void latePenaltyIsReversedWithoutInventingAttendance() throws Exception {
        var end=Store.now().minusHours(1).withSecond(0).withNano(0); var start=end.minusHours(5); var id=service(start,end); var e=fixtureEnrollment(id,volunteer);
        var c=new Client().login("voluntario@demo.pe"); var admin=new Client().login("jefatura@demo.pe");
        c.ok("/attendance","GET",null); UUID a=db.queryForObject("SELECT id FROM attendance_records WHERE enrollment_id=?",UUID.class,e);
        admin.ok("/attendance/"+a+"/confirm","POST",Map.of("checkIn",start.plusMinutes(40).toString(),"checkOut",end.toString(),"observations","Llegó cuarenta minutos después"));
        String month=YearMonth.from(start).toString(); double before=c.ok("/attendance/hours?month="+month,"GET",null).get("hours").asDouble();
        c.ok("/attendance/"+a+"/justifications","POST",Map.of("reason","Incidente de transporte, solicito revisión"));
        UUID j=db.queryForObject("SELECT id FROM justifications WHERE absence_id=?",UUID.class,a);
        assertEquals("TARDANZA",db.queryForObject("SELECT kind FROM justifications WHERE id=?",String.class,j));
        admin.ok("/justifications/"+j+"/resolve","POST",Map.of("approved",true,"comment","Tardanza justificada, sin requisito de reemplazo"));
        double after=c.ok("/attendance/hours?month="+month,"GET",null).get("hours").asDouble();
        assertEquals(before+80.0/60,after,.0002);
        admin.ok("/justifications/"+j+"/resolve","POST",Map.of("approved",true,"comment","Revisión sin duplicar devolución"));
        assertEquals(after,c.ok("/attendance/hours?month="+month,"GET",null).get("hours").asDouble());
        assertEquals(4+20.0/60,db.queryForObject("SELECT approved_hours FROM attendance_records WHERE id=?",Double.class,a),.0001);
    }
    @Test void absenceStaysDeductedDuringReviewAndRequiresReplacement() throws Exception {
        var end=Store.now().minusHours(50); var start=end.minusHours(3); var id=service(start,end); UUID e=fixtureEnrollment(id,volunteer);
        var c=new Client().login("voluntario@demo.pe"); var admin=new Client().login("jefatura@demo.pe"); c.ok("/attendance","GET",null);
        UUID a=db.queryForObject("SELECT id FROM attendance_records WHERE enrollment_id=?",UUID.class,e);
        c.ok("/enrollments/"+e+"/correction","POST",Map.of("reason","Revisar si existe constancia"));
        assertEquals("AUSENTE",db.queryForObject("SELECT status FROM attendance_records WHERE id=?",String.class,a));
        c.ok("/attendance/"+a+"/justifications","POST",Map.of("reason","Solicitud excepcional fuera de plazo"));
        UUID j=db.queryForObject("SELECT id FROM justifications WHERE absence_id=?",UUID.class,a);
        assertTrue(db.queryForObject("SELECT late FROM justifications WHERE id=?",Boolean.class,j));
        assertEquals(400,admin.request("/justifications/"+j+"/resolve","POST",Map.of("approved",true,"comment","Falta verificar reemplazo")).statusCode());
        String month=YearMonth.from(start).toString(); double before=c.ok("/attendance/hours?month="+month,"GET",null).get("hours").asDouble();
        UUID repl=fixtureEnrollment(id,lucia); db.update("UPDATE enrollments SET replacement_for=?,reviewed_at=? WHERE id=?",e,start.minusMinutes(1),repl);
        admin.ok("/justifications/"+j+"/resolve","POST",Map.of("approved",true,"comment","Se verificó reemplazo histórico"));
        assertEquals(before+6,c.ok("/attendance/hours?month="+month,"GET",null).get("hours").asDouble(),.0001);
    }
    @Test void materialsAreRequestsNotStockAndOnlyChiefOrAssignedResponsibleCanRead() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(3)); var admin=new Client().login("jefatura@demo.pe"); var c=new Client().login("voluntario@demo.pe");
        admin.ok("/callouts/"+id+"/materials","POST",Map.of("name","Guantes","quantity",4,"details","Talla M"));
        UUID m=db.queryForObject("SELECT id FROM material_requests WHERE callout_id=?",UUID.class,id);
        assertEquals(403,c.request("/callouts/"+id+"/materials","GET",null).statusCode());
        admin.ok("/materials/"+m+"/handover","POST",Map.of("action","RECEPCION","quantity",4,"observations","Recibido de almacén"));
        admin.ok("/materials/"+m+"/handover","POST",Map.of("action","DEVOLUCION","quantity",3,"observations","Una unidad dañada"));
        assertEquals(3,db.queryForObject("SELECT returned_quantity FROM material_requests WHERE id=?",Integer.class,m));
        assertEquals(chief,db.queryForObject("SELECT received_by FROM material_requests WHERE id=?",UUID.class,m));
        assertEquals(400,admin.request("/materials/"+m+"/handover","POST",Map.of("action","DEVOLUCION","quantity",3,"observations","No duplicar")).statusCode());
    }
    @Test void csrfAndLogoutRemainRequired() throws Exception {
        var c=new Client().login("voluntario@demo.pe");
        var response=c.http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/profile/phone")).header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString("{\"phone\":\"123\"}")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(403,response.statusCode()); c.ok("/auth/logout","POST",Map.of()); assertEquals(401,c.request("/enrollments","GET",null).statusCode());
    }

    @Test void createPublishAndUniqueCodeUseThePublicApi() throws Exception {
        var start=freeSlot(); var admin=new Client().login("jefatura@demo.pe"); String code="API-"+UUID.randomUUID();
        var body=new LinkedHashMap<String,Object>();
        body.put("title","Servicio creado por API"); body.put("description","Guardia con puestos especializados");
        body.put("start",start.toString()); body.put("end",start.plusHours(3).toString()); body.put("deadline",start.minusHours(1).toString());
        body.put("location","Arequipa"); body.put("requirements","Trabajo en equipo"); body.put("level","BASICO");
        body.put("socorristaSlots",2); body.put("radioSlots",1); body.put("pilotSlots",1);
        body.put("kind","SERVICIO"); body.put("area","Salud"); body.put("contact","Encargado de guardia"); body.put("code",code); body.put("serviceType","Guardia");
        UUID id=UUID.fromString(admin.ok("/callouts","POST",body).get("id").asText());
        assertEquals(3,admin.ok("/callouts/"+id,"GET",null).get("planned_hours").asDouble());
        assertEquals(409,admin.request("/callouts","POST",body).statusCode());
        admin.ok("/callouts/"+id+"/status","POST",Map.of("status","ABIERTA"));
        assertTrue(db.queryForObject("SELECT count(*) FROM notifications WHERE message LIKE ?",Integer.class,"%Servicio creado por API%")>0);
        assertNull(db.queryForObject("SELECT responsible_id FROM callouts WHERE id=?",UUID.class,id));
        assertEquals(chief,db.queryForObject("SELECT created_by FROM callouts WHERE id=?",UUID.class,id));
    }
    @Test void pilotNeedsGraduationAndAnExplicitQualification() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(3)); db.update("UPDATE callouts SET pilot_slots=1 WHERE id=?",id);
        UUID sofia=UUID.fromString("00000000-0000-0000-0000-000000000008");
        db.update("UPDATE volunteer_profiles SET pilot_qualified=false WHERE user_id=?",sofia);
        var pilot=new Client().login("reclutador2@demo.pe");
        assertEquals(400,pilot.request("/callouts/"+id+"/enrollments","POST",Map.of("requirementsAccepted",true,"position","PILOTO")).statusCode());
        var admin=new Client().login("jefatura@demo.pe");
        admin.ok("/users/"+sofia,"PUT",Map.of("role","RECLUTADOR","level","AVANZADO","graduated",true,"status","ACTIVO","comment","Habilitación verificada","pilotQualified",true));
        pilot.enroll(id,"PILOTO");
        assertEquals("PILOTO",db.queryForObject("SELECT position FROM enrollments WHERE callout_id=?",String.class,id));
        db.update("UPDATE volunteer_profiles SET pilot_qualified=true WHERE user_id=?",lucia);
        try {
            var trainee=new Client().login("lucia@demo.pe");
            assertEquals(400,trainee.request("/callouts/"+id+"/enrollments","POST",Map.of("requirementsAccepted",true,"position","PILOTO")).statusCode());
        } finally { db.update("UPDATE volunteer_profiles SET pilot_qualified=false WHERE user_id=?",lucia); }
    }
    @Test void cancellationWithin48HoursDoesNotReleaseASeat() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(2)); var c=new Client().login("voluntario@demo.pe"); c.enroll(id,"SOCORRISTA");
        var near=Store.now().plusHours(24); db.update("UPDATE callouts SET start_at=?,end_at=?,deadline=? WHERE id=?",near,near.plusHours(2),near,id);
        UUID e=enrollment(id,volunteer);
        assertEquals(400,c.request("/enrollments/"+e+"/cancel-request","POST",Map.of("reason","Fuera de la anticipación mínima")).statusCode());
        assertEquals("CONFIRMADA",db.queryForObject("SELECT status FROM enrollments WHERE id=?",String.class,e));
    }
    @Test void legacyBalancesAreNotRepricedUntilAnExplicitReview() throws Exception {
        var end=Store.now().minusHours(2).withSecond(0).withNano(0); var start=end.minusHours(3); var id=service(start,end); var e=fixtureEnrollment(id,volunteer);
        UUID a=UUID.randomUUID();
        db.update("INSERT INTO attendance_records(id,enrollment_id,volunteer_id,callout_id,responsible_id,status,check_in,check_out,approved_hours,policy_version) VALUES (?,?,?,?,?,'CONFIRMADA',?,?,2.6667,1) ON CONFLICT(enrollment_id) DO UPDATE SET check_in=EXCLUDED.check_in,check_out=EXCLUDED.check_out,approved_hours=EXCLUDED.approved_hours,status='CONFIRMADA',policy_version=1",a,e,volunteer,id,chief,start.plusMinutes(20),end);
        a=db.queryForObject("SELECT id FROM attendance_records WHERE enrollment_id=?",UUID.class,e);
        var c=new Client().login("voluntario@demo.pe"); var admin=new Client().login("jefatura@demo.pe"); String month=YearMonth.from(start).toString();
        double before=c.ok("/attendance/hours?month="+month,"GET",null).get("hours").asDouble();
        assertEquals(1,db.queryForObject("SELECT policy_version FROM attendance_records WHERE id=?",Integer.class,a));
        admin.ok("/attendance/"+a+"/confirm","POST",Map.of("checkIn",start.plusMinutes(20).toString(),"checkOut",end.toString(),"observations","Revisión expresa con la política actual"));
        assertEquals(2,db.queryForObject("SELECT policy_version FROM attendance_records WHERE id=?",Integer.class,a));
        assertEquals(before-40.0/60,c.ok("/attendance/hours?month="+month,"GET",null).get("hours").asDouble(),.0002);
    }
}
