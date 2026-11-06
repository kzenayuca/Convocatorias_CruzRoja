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
        void assign(UUID service,UUID user,String position) throws Exception { ok("/callouts/"+service+"/assignments","POST",Map.of("userId",user,"position",position,"requirementsVerified",true)); }
        int photo(UUID service,byte[] bytes) throws Exception {
            var token=ok("/auth/csrf","GET",null); String boundary="cr-test-boundary";
            var payload=new java.io.ByteArrayOutputStream();
            payload.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"equipo.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes());
            payload.write(bytes); payload.write(("\r\n--"+boundary+"--\r\n").getBytes());
            var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/callouts/"+service+"/photo"))
                .header(token.get("headerName").asText(),token.get("token").asText()).header("Content-Type","multipart/form-data; boundary="+boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload.toByteArray())).build();
            return http.send(request,HttpResponse.BodyHandlers.ofString()).statusCode();
        }
        byte[] pdf(String path) throws Exception {
            var r=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200,r.statusCode()); assertEquals("application/pdf",r.headers().firstValue("Content-Type").orElse(""));
            assertTrue(r.headers().firstValue("Content-Disposition").orElse("").contains("attachment")); return r.body();
        }
    }
    UUID service(LocalDateTime start,LocalDateTime end) {
        UUID id=UUID.randomUUID(); boolean past=end.isBefore(Store.now());
        db.update("""
            INSERT INTO callouts(id,title,start_at,end_at,deadline,minimum_level,capacity,minimum_participants,
            status,created_by,service_code,socorrista_slots,total_volunteers,opened_at)
            VALUES (?,'Prueba aislada',?,?,?,'BASICO',1,1,?,?,?,1,1,?)
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
    Map<String,Object> callBody(LocalDateTime start,String code) {
        var body=new LinkedHashMap<String,Object>();
        body.put("title","Servicio público de prueba"); body.put("description","Actividad comunitaria");
        body.put("start",start.toString()); body.put("end",start.plusHours(3).toString()); body.put("deadline",start.minusHours(1).toString());
        body.put("location","Arequipa"); body.put("requirements","Trabajo en equipo"); body.put("level","BASICO");
        body.put("totalVolunteers",2); body.put("radioSlots",0); body.put("pilotSlots",0);
        body.put("kind","SERVICIO"); body.put("area","Salud"); body.put("contact","Coordinación"); body.put("code",code); body.put("serviceType","Guardia");
        return body;
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
        db.update("UPDATE callouts SET total_volunteers=2 WHERE id=?",id);
        var c=new Client().login("voluntario@demo.pe"); var responsible=new Client().login("maria@demo.pe");
        assertEquals(400,c.request("/callouts/"+id+"/enrollments","POST",Map.of("requirementsAccepted",true,"position","RESPONSABLE")).statusCode());
        c.enroll(id,"SOCORRISTA");
        assertEquals("CONFIRMADA",db.queryForObject("SELECT status FROM enrollments WHERE id=?",String.class,enrollment(id,volunteer)));
        assertEquals("ABIERTA",db.queryForObject("SELECT status FROM callouts WHERE id=?",String.class,id));
        responsible.enroll(id,"SOCORRISTA");
        new Client().login("jefatura@demo.pe").ok("/callouts/"+id+"/responsible","PUT",Map.of("userId",maria));
        assertEquals("CERRADA",db.queryForObject("SELECT status FROM callouts WHERE id=?",String.class,id));
        assertEquals(maria,db.queryForObject("SELECT responsible_id FROM callouts WHERE id=?",UUID.class,id));
        assertTrue(responsible.ok("/callouts/"+id,"GET",null).get("can_operate").asBoolean());
        assertTrue(responsible.ok("/callouts/"+id,"GET",null).get("can_edit").asBoolean());
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
        db.update("UPDATE callouts SET total_volunteers=2 WHERE id=?",id);
        db.update("UPDATE callouts SET deadline=? WHERE id=?",Store.now().minusSeconds(1),id);
        c.ok("/callouts","GET",null);
        assertEquals("CANCELADA",db.queryForObject("SELECT status FROM callouts WHERE id=?",String.class,id));
        assertEquals("CANCELADA",db.queryForObject("SELECT status FROM enrollments WHERE callout_id=?",String.class,id));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM attendance_records WHERE callout_id=?",Integer.class,id));
    }
    @Test void replacementKeepsSeatUntilApprovalAndCancelledOriginalDoesNotCount() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(3));
        db.update("UPDATE callouts SET total_volunteers=2 WHERE id=?",id);
        var c=new Client().login("voluntario@demo.pe"); var replacement=new Client().login("lucia@demo.pe"); var boss=new Client().login("maria@demo.pe");
        c.enroll(id,"SOCORRISTA"); boss.enroll(id,"SOCORRISTA"); new Client().login("jefatura@demo.pe").ok("/callouts/"+id+"/responsible","PUT",Map.of("userId",maria)); UUID original=enrollment(id,volunteer);
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
        c.enroll(id,"SOCORRISTA");
        admin.ok("/enrollments/"+enrollment(id,volunteer)+"/review","POST",Map.of("approved",false,"comment","No participará"));
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
    @Test void chiefCanResolveAnAbsenceAfterReviewingItsEvidence() throws Exception {
        var end=Store.now().minusHours(50); var start=end.minusHours(3); var id=service(start,end); UUID e=fixtureEnrollment(id,volunteer);
        var c=new Client().login("voluntario@demo.pe"); var admin=new Client().login("jefatura@demo.pe"); c.ok("/attendance","GET",null);
        UUID a=db.queryForObject("SELECT id FROM attendance_records WHERE enrollment_id=?",UUID.class,e);
        c.ok("/enrollments/"+e+"/correction","POST",Map.of("reason","Revisar si existe constancia"));
        assertEquals("AUSENTE",db.queryForObject("SELECT status FROM attendance_records WHERE id=?",String.class,a));
        c.ok("/attendance/"+a+"/justifications","POST",Map.of("reason","Solicitud excepcional fuera de plazo"));
        UUID j=db.queryForObject("SELECT id FROM justifications WHERE absence_id=?",UUID.class,a);
        assertTrue(db.queryForObject("SELECT late FROM justifications WHERE id=?",Boolean.class,j));
        admin.ok("/justifications/"+j+"/resolve","POST",Map.of("approved",false,"comment","Falta verificar evidencia"));
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
        body.put("totalVolunteers",4); body.put("radioSlots",1); body.put("pilotSlots",1);
        body.put("kind","SERVICIO"); body.put("area","Salud"); body.put("contact","Encargado de guardia"); body.put("code",code); body.put("serviceType","Guardia");
        UUID id=UUID.fromString(admin.ok("/callouts","POST",body).get("id").asText());
        assertEquals(3,admin.ok("/callouts/"+id,"GET",null).get("planned_hours").asDouble());
        var exposed=OffsetDateTime.parse(admin.ok("/callouts/"+id,"GET",null).get("start_at").asText());
        assertEquals(start,exposed.toLocalDateTime()); assertEquals(ZoneOffset.ofHours(-5),exposed.getOffset());
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
        admin.ok("/users/"+sofia,"PUT",Map.of("role","VOLUNTARIO","level","AVANZADO","graduated",true,"status","ACTIVO","comment","Habilitación verificada","pilotQualified",true));
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

    @Test void volunteerAcceptsEitherConditionButLastGraduateSeatIsProtected() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(3));
        db.update("UPDATE callouts SET total_volunteers=2,graduate_slots=1 WHERE id=?",id);
        var trainee=new Client().login("voluntario@demo.pe"); var graduate=new Client().login("maria@demo.pe");
        assertEquals(400,trainee.request("/callouts/"+id+"/enrollments","POST",Map.of("requirementsAccepted",true,"position","GRADUADO")).statusCode());
        trainee.enroll(id,"SOCORRISTA"); assertEquals("ABIERTA",db.queryForObject("SELECT status FROM callouts WHERE id=?",String.class,id));
        var another=new Client().login("lucia@demo.pe");
        assertEquals(400,another.request("/callouts/"+id+"/enrollments","POST",Map.of("requirementsAccepted",true,"position","SOCORRISTA")).statusCode());
        graduate.enroll(id,"SOCORRISTA"); assertEquals("CERRADA",db.queryForObject("SELECT status FROM callouts WHERE id=?",String.class,id));
        assertNull(db.queryForObject("SELECT responsible_id FROM callouts WHERE id=?",UUID.class,id));
        var admin=new Client().login("jefatura@demo.pe");
        admin.ok("/callouts/"+id+"/responsible","PUT",Map.of("userId",maria));
        assertEquals(2,db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND status='CONFIRMADA'",Integer.class,id));
    }
    @Test void scheduleIsPersonalEvenForRecruitersAndPdfDoesNotExposeOtherTeams() throws Exception {
        var start=freeSlot(); var mine=service(start,start.plusHours(3)); var other=service(start,start.plusHours(3));
        var c=new Client().login("voluntario@demo.pe"); var recruiter=new Client().login("reclutador@demo.pe"); var admin=new Client().login("jefatura@demo.pe");
        c.enroll(mine,"SOCORRISTA"); recruiter.enroll(other,"SOCORRISTA");
        String query="?date="+start.toLocalDate();
        var personal=c.ok("/schedule"+query,"GET",null); assertEquals(1,personal.size()); assertEquals(mine.toString(),personal.get(0).get("id").asText());
        var recruit= recruiter.ok("/schedule"+query,"GET",null); assertEquals(1,recruit.size()); assertEquals(other.toString(),recruit.get(0).get("id").asText());
        assertEquals(2,admin.ok("/schedule"+query,"GET",null).size());
        assertEquals(403,c.request("/callouts/"+other+"/enrollments","GET",null).statusCode());
        assertEquals(403,c.request("/schedule/pdf"+query,"GET",null).statusCode());
        assertEquals(403,recruiter.request("/schedule/pdf"+query,"GET",null).statusCode());
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(admin.pdf("/schedule/pdf"+query))) {
            String text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
            assertTrue(text.contains("T-"+mine)); assertTrue(text.contains("T-"+other));
        }
        assertEquals(403,recruiter.request("/reports?month="+YearMonth.from(start),"GET",null).statusCode());
        c.ok("/callouts/"+mine,"GET",null);
    }
    @Test void photoUploadIsRestrictedButAllActiveAccountsCanViewAndParticipantFindsItInProfile() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(3));
        var user=new Client().login("voluntario@demo.pe"); var admin=new Client().login("jefatura@demo.pe");
        var recruiter=new Client().login("reclutador@demo.pe");
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB); var output=new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(image,"png",output);
        assertEquals(403,user.photo(id,output.toByteArray()));
        assertEquals(200,admin.photo(id,output.toByteArray()));
        assertEquals(200,user.request("/callouts/"+id+"/photo","GET",null).statusCode());
        assertEquals(403,recruiter.photo(id,output.toByteArray()));
        recruiter.enroll(id,"SOCORRISTA"); assertEquals(403,recruiter.photo(id,output.toByteArray()));
        admin.ok("/callouts/"+id+"/responsible","PUT",Map.of("userId",UUID.fromString("00000000-0000-0000-0000-000000000002")));
        assertEquals(200,recruiter.photo(id,output.toByteArray()));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM callout_photo_archive WHERE callout_id=?",Integer.class,id));
        assertFalse(recruiter.ok("/profile/photos","GET",null).toString().contains(id.toString()));
        assertFalse(user.ok("/profile/photos","GET",null).toString().contains(id.toString()));
        var end=Store.now().minusHours(2); var previous=service(end.minusHours(3),end);
        UUID recruiterId=UUID.fromString("00000000-0000-0000-0000-000000000002"); UUID e=fixtureEnrollment(previous,recruiterId);
        recruiter.ok("/attendance","GET",null);
        db.update("UPDATE attendance_records SET check_in=?,check_out=? WHERE enrollment_id=?",end.minusHours(3),end,e);
        assertEquals(200,admin.photo(previous,output.toByteArray()));
        assertTrue(recruiter.ok("/profile/photos","GET",null).toString().contains(previous.toString()));
        assertEquals(400,admin.photo(id,"invalid image".getBytes()));
        byte[] large=Arrays.copyOf(output.toByteArray(),3*1024*1024);
        assertEquals(200,admin.photo(id,large));
        assertEquals(large.length,db.queryForObject("SELECT octet_length(team_photo) FROM callouts WHERE id=?",Integer.class,id));
        assertEquals(413,admin.photo(id,Arrays.copyOf(output.toByteArray(),10*1024*1024+1)));
    }
    @Test void marksAutoCloseAfterSevenDaysWithoutInventingMissingTimes() throws Exception {
        var end=Store.now().minusDays(8); var start=end.minusHours(3); var id=service(start,end); var e=fixtureEnrollment(id,volunteer); var missing=fixtureEnrollment(id,lucia);
        var user=new Client().login("voluntario@demo.pe"); user.ok("/attendance","GET",null);
        db.update("UPDATE attendance_records SET check_in=?,check_out=?,status='PENDIENTE_REVISION' WHERE enrollment_id=?",start.plusMinutes(20),end,e);
        db.update("UPDATE attendance_records SET check_in=?,status='PENDIENTE_REVISION' WHERE enrollment_id=?",start,missing);
        user.ok("/attendance","GET",null);
        assertEquals("CONFIRMADA",db.queryForObject("SELECT status FROM attendance_records WHERE enrollment_id=?",String.class,e));
        assertEquals(2+40.0/60,db.queryForObject("SELECT approved_hours FROM attendance_records WHERE enrollment_id=?",Double.class,e),.0001);
        assertEquals("INCOMPLETA",db.queryForObject("SELECT status FROM attendance_records WHERE enrollment_id=?",String.class,missing));
        assertNull(db.queryForObject("SELECT check_out FROM attendance_records WHERE enrollment_id=?",java.sql.Timestamp.class,missing));
        assertEquals(0,db.queryForObject("SELECT approved_hours FROM attendance_records WHERE enrollment_id=?",Double.class,missing));
        long notices=db.queryForObject("SELECT count(*) FROM notifications WHERE type='ASISTENCIA' AND recipient_id=?",Long.class,volunteer);
        user.ok("/attendance","GET",null); assertEquals(notices,db.queryForObject("SELECT count(*) FROM notifications WHERE type='ASISTENCIA' AND recipient_id=?",Long.class,volunteer));
    }
    @Test void monthlyApprovalFreezesBalancesAndRejectsLateChanges() throws Exception {
        var month=YearMonth.of(2000,1); while(db.queryForObject("SELECT count(*) FROM monthly_reports WHERE month=?",Integer.class,month.atDay(1))>0) month=month.plusMonths(1);
        var start=month.atDay(10).atTime(8,0); var end=start.plusHours(3); var id=service(start,end); var e=fixtureEnrollment(id,volunteer);
        var admin=new Client().login("jefatura@demo.pe"); var user=new Client().login("voluntario@demo.pe"); user.ok("/attendance","GET",null);
        UUID a=db.queryForObject("SELECT id FROM attendance_records WHERE enrollment_id=?",UUID.class,e);
        admin.ok("/attendance/"+a+"/confirm","POST",Map.of("checkIn",start.toString(),"checkOut",end.toString(),"observations","Confirmación antes del cierre"));
        String query="?month="+month; assertEquals(403,user.request("/reports/approve"+query,"POST",Map.of()).statusCode());
        admin.ok("/reports/approve"+query,"POST",Map.of());
        assertTrue(user.ok("/attendance/hours"+query,"GET",null).get("closed").asBoolean());
        assertEquals(3,user.ok("/attendance/hours"+query,"GET",null).get("hours").asDouble());
        assertEquals(400,admin.request("/attendance/"+a+"/confirm","POST",Map.of("checkIn",start.toString(),"checkOut",end.toString(),"hours",8,"observations","No cambiar mes cerrado")).statusCode());
        assertEquals(400,user.request("/enrollments/"+e+"/correction","POST",Map.of("reason","No se puede cambiar")).statusCode());
        assertEquals(400,user.request("/attendance/"+a+"/justifications","POST",Map.of("reason","No se puede cambiar")).statusCode());
        assertEquals(400,admin.request("/reports/approve"+query,"POST",Map.of()).statusCode());
        assertTrue(user.ok("/attendance/progress","GET",null).get("months").toString().contains(month.toString()));
    }
    @Test void conditionTargetsAreConfigurableOnlyByChiefAndDoNotAlterBalances() throws Exception {
        var user=new Client().login("voluntario@demo.pe"); var admin=new Client().login("jefatura@demo.pe");
        var graduate=new Client().login("maria@demo.pe");
        double before=user.ok("/attendance/progress","GET",null).get("progress").get("balance").asDouble();
        assertEquals(403,user.request("/administration","PUT",Map.of("trainingHours",120,"graduateHours",200)).statusCode());
        assertEquals(400,admin.request("/administration","PUT",Map.of("trainingHours",0,"graduateHours",200)).statusCode());
        assertEquals(400,admin.request("/administration","PUT",Map.of("trainingHours",150)).statusCode());
        admin.ok("/administration","PUT",Map.of("trainingHours",150,"graduateHours",200));
        var training=user.ok("/attendance/progress","GET",null); var graduated=graduate.ok("/attendance/progress","GET",null);
        assertEquals(150,training.get("progress").get("required").asDouble()); assertEquals("FORMACION",training.get("condition").asText());
        assertEquals(200,graduated.get("progress").get("required").asDouble()); assertEquals("GRADUADO",graduated.get("condition").asText());
        admin.ok("/administration","PUT",Map.of("trainingHours",160,"graduateHours",210));
        assertEquals(160,user.ok("/attendance/progress","GET",null).get("progress").get("required").asDouble());
        assertEquals(before,user.ok("/attendance/progress","GET",null).get("progress").get("balance").asDouble());
        assertFalse(user.ok("/auth/me","GET",null).get("graduated").asBoolean());
        assertEquals(210,admin.ok("/administration","GET",null).get("graduate_hours").asDouble());
    }
    @Test void onlyChiefAssignsCodesAndOldCodesStayReserved() throws Exception {
        var start=freeSlot(); var admin=new Client().login("jefatura@demo.pe"); var recruiter=new Client().login("reclutador@demo.pe");
        String code="COD-"+UUID.randomUUID(); var body=callBody(start,code);
        assertEquals(403,recruiter.request("/callouts","POST",body).statusCode());
        body.put("code",null); assertEquals(403,recruiter.request("/callouts","POST",body).statusCode());
        UUID id=UUID.fromString(admin.ok("/callouts","POST",body).get("id").asText());
        assertEquals(400,admin.request("/callouts/"+id+"/status","POST",Map.of("status","ABIERTA")).statusCode());
        body.put("code",code); admin.ok("/callouts/"+id,"PUT",body);
        body.put("code","NEW-"+UUID.randomUUID()); admin.ok("/callouts/"+id,"PUT",body);
        body.put("code",code.toLowerCase()); assertEquals(409,admin.request("/callouts","POST",body).statusCode());
        assertEquals(0,db.queryForObject("SELECT count(*) FROM callouts WHERE lower(service_code)=lower(?)",Integer.class,code),"El código histórico no deja un borrador parcial al fallar su reserva.");
        assertEquals(2,db.queryForObject("SELECT count(*) FROM service_code_history WHERE callout_id=?",Integer.class,id));
        assertTrue(admin.ok("/service-codes","GET",null).toString().contains(code.toUpperCase(Locale.ROOT)));
        assertEquals(403,recruiter.request("/service-codes","GET",null).statusCode());
    }
    @Test void publishedChangesNotifyParticipantsAndScheduleConflictsAreRejected() throws Exception {
        var start=freeSlot(); var admin=new Client().login("jefatura@demo.pe"); var user=new Client().login("voluntario@demo.pe");
        var body=callBody(start,"UPD-"+UUID.randomUUID()); UUID id=UUID.fromString(admin.ok("/callouts","POST",body).get("id").asText());
        admin.ok("/callouts/"+id+"/status","POST",Map.of("status","ABIERTA")); user.enroll(id,"SOCORRISTA");
        var next=service(start.plusHours(3),start.plusHours(5)); user.enroll(next,"SOCORRISTA");
        body.put("end",start.plusHours(4).toString()); assertEquals(400,admin.request("/callouts/"+id,"PUT",body).statusCode());
        body.put("end",start.plusHours(3).toString()); body.put("location","Nueva sede"); body.put("requirements","Documento y trabajo en equipo");
        admin.ok("/callouts/"+id,"PUT",body);
        assertTrue(user.ok("/notifications","GET",null).toString().contains("Nueva sede"));
        assertTrue(user.ok("/callouts/"+id,"GET",null).get("requirements").asText().contains("Documento"));
    }
    @Test void responsibleIsOptionalButConsumesOnePlaceAndCanBeReassignedByChief() throws Exception {
        var start=freeSlot(); var admin=new Client().login("jefatura@demo.pe"); var user=new Client().login("voluntario@demo.pe");
        var body=callBody(start,"BOS-"+UUID.randomUUID()); body.put("totalVolunteers",2);
        UUID id=UUID.fromString(admin.ok("/callouts","POST",body).get("id").asText()); admin.ok("/callouts/"+id+"/status","POST",Map.of("status","ABIERTA"));
        user.enroll(id,"SOCORRISTA"); assertEquals(2,db.queryForObject("SELECT capacity FROM callouts WHERE id=?",Integer.class,id));
        assertEquals(403,user.request("/callouts/"+id+"/responsible","PUT",Map.of("userId",maria)).statusCode());
        admin.assign(id,maria,"SOCORRISTA");
        admin.ok("/callouts/"+id+"/responsible","PUT",Map.of("userId",maria));
        var remove=new HashMap<String,Object>(); remove.put("userId",null); remove.put("expectedResponsibleId",maria); remove.put("reason","Retiro explícito; conserva su plaza"); admin.ok("/callouts/"+id+"/responsible/change","POST",remove);
        assertEquals("CERRADA",db.queryForObject("SELECT status FROM callouts WHERE id=?",String.class,id));
        admin.ok("/callouts/"+id+"/responsible","PUT",Map.of("userId",maria));
        assertEquals(2,db.queryForObject("SELECT capacity FROM callouts WHERE id=?",Integer.class,id));
        assertEquals("CERRADA",db.queryForObject("SELECT status FROM callouts WHERE id=?",String.class,id));
        assertEquals(maria,db.queryForObject("SELECT responsible_id FROM callouts WHERE id=?",UUID.class,id));
    }
    @Test void materialsPdfIsReadableAndUsesTheSamePermissions() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(3)); var admin=new Client().login("jefatura@demo.pe"); var user=new Client().login("voluntario@demo.pe");
        admin.ok("/callouts/"+id+"/materials","POST",Map.of("name","Botiquín","quantity",2,"details","Verificar insumos"));
        try(var pdf=org.apache.pdfbox.Loader.loadPDF(admin.pdf("/callouts/"+id+"/materials/pdf"))) {
            assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf).contains("Botiquín"));
        }
        assertEquals(403,user.request("/callouts/"+id+"/materials/pdf","GET",null).statusCode());
    }
    @Test void fourVolunteersIncludeResponsiblePilotRadioAndGraduateMinimum() throws Exception {
        var start=freeSlot(); var admin=new Client().login("jefatura@demo.pe");
        var body=callBody(start,"T4-"+UUID.randomUUID()); body.put("totalVolunteers",4); body.put("graduateSlots",1);
        body.put("radioSlots",1); body.put("pilotSlots",1);
        UUID id=UUID.fromString(admin.ok("/callouts","POST",body).get("id").asText());
        admin.ok("/callouts/"+id+"/status","POST",Map.of("status","ABIERTA"));
        UUID sofia=UUID.fromString("00000000-0000-0000-0000-000000000008");
        UUID recruiter=UUID.fromString("00000000-0000-0000-0000-000000000002");
        boolean oldRadio=db.queryForObject("SELECT radio_qualified FROM volunteer_profiles WHERE user_id=?",Boolean.class,maria);
        boolean oldPilot=db.queryForObject("SELECT pilot_qualified FROM volunteer_profiles WHERE user_id=?",Boolean.class,sofia);
        try {
            db.update("UPDATE volunteer_profiles SET radio_qualified=true WHERE user_id=?",maria);
            db.update("UPDATE volunteer_profiles SET pilot_qualified=true WHERE user_id=?",sofia);
            admin.assign(id,recruiter,"SOCORRISTA");
            admin.ok("/callouts/"+id+"/responsible","PUT",Map.of("userId",recruiter));
            new Client().login("voluntario@demo.pe").enroll(id,"SOCORRISTA");
            var extra=new Client().login("lucia@demo.pe");
            assertEquals(400,extra.request("/callouts/"+id+"/enrollments","POST",Map.of("position","SOCORRISTA","requirementsAccepted",true)).statusCode());
            new Client().login("maria@demo.pe").enroll(id,"RADIOPERADOR");
            assertEquals("ABIERTA",db.queryForObject("SELECT status FROM callouts WHERE id=?",String.class,id));
            new Client().login("reclutador2@demo.pe").enroll(id,"PILOTO");
            var detail=admin.ok("/callouts/"+id,"GET",null);
            assertEquals("COMPLETA",detail.get("coverage").asText());
            assertEquals(4,detail.get("total_volunteers").asInt());
            assertEquals(4,detail.get("positions").get(0).get("filled").asInt());
            assertEquals(3,detail.get("positions").get(1).get("filled").asInt());
            assertEquals(4,db.queryForObject("SELECT count(DISTINCT volunteer_id) FROM enrollments WHERE callout_id=? AND status='CONFIRMADA'",Integer.class,id));
            assertEquals(400,admin.request("/callouts/"+id+"/responsible","PUT",Map.of("userId",maria)).statusCode());
            assertEquals(recruiter,db.queryForObject("SELECT responsible_id FROM callouts WHERE id=?",UUID.class,id));
            assertEquals(400,extra.request("/callouts/"+id+"/enrollments","POST",Map.of("position","SOCORRISTA","requirementsAccepted",true)).statusCode());
            body.put("totalVolunteers",3); assertEquals(400,admin.request("/callouts/"+id,"PUT",body).statusCode());
            try(var pdf=org.apache.pdfbox.Loader.loadPDF(admin.pdf("/schedule/pdf?date="+start.toLocalDate()))) {
                assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf).contains("Total de voluntarios: 4"));
            }
        } finally {
            db.update("UPDATE volunteer_profiles SET radio_qualified=? WHERE user_id=?",oldRadio,maria);
            db.update("UPDATE volunteer_profiles SET pilot_qualified=? WHERE user_id=?",oldPilot,sofia);
        }
    }
    @Test void oneGraduateIsOnePersonAndCannotBeReplacedByATrainee() throws Exception {
        var start=freeSlot(); var admin=new Client().login("jefatura@demo.pe"); var body=callBody(start,"G-"+UUID.randomUUID());
        body.put("totalVolunteers",1); body.put("graduateSlots",1);
        UUID id=UUID.fromString(admin.ok("/callouts","POST",body).get("id").asText());
        admin.ok("/callouts/"+id+"/status","POST",Map.of("status","ABIERTA"));
        var trainee=new Client().login("voluntario@demo.pe"); var graduate=new Client().login("maria@demo.pe");
        assertEquals(400,trainee.request("/callouts/"+id+"/enrollments","POST",Map.of("position","SOCORRISTA","requirementsAccepted",true)).statusCode());
        graduate.enroll(id,"SOCORRISTA");
        assertEquals("CERRADA",db.queryForObject("SELECT status FROM callouts WHERE id=?",String.class,id));
        UUID original=enrollment(id,maria);
        graduate.ok("/enrollments/"+original+"/cancel-request","POST",Map.of("reason","Coordinar reemplazo graduado"));
        assertEquals(400,trainee.request("/callouts/"+id+"/enrollments","POST",Map.of("position","SOCORRISTA","requirementsAccepted",true,"replacementFor",original)).statusCode());
        var replacement=new Client().login("reclutador@demo.pe");
        replacement.ok("/callouts/"+id+"/enrollments","POST",Map.of("position","SOCORRISTA","requirementsAccepted",true,"replacementFor",original));
        admin.ok("/enrollments/"+enrollment(id,UUID.fromString("00000000-0000-0000-0000-000000000002"))+"/review","POST",Map.of("approved",true,"comment","Graduado compatible"));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND status='CONFIRMADA'",Integer.class,id));
    }
    @Test void invalidTotalsAndRequirementsCannotBeSaved() throws Exception {
        var admin=new Client().login("jefatura@demo.pe"); var body=callBody(freeSlot(),"I-"+UUID.randomUUID());
        body.put("totalVolunteers",0); assertEquals(400,admin.request("/callouts","POST",body).statusCode());
        body.put("totalVolunteers",1); body.put("graduateSlots",2); assertEquals(400,admin.request("/callouts","POST",body).statusCode());
        body.put("graduateSlots",0); body.put("radioSlots",1); body.put("pilotSlots",1); assertEquals(400,admin.request("/callouts","POST",body).statusCode());
        body.remove("totalVolunteers"); assertEquals(400,admin.request("/callouts","POST",body).statusCode());
    }
    @Test void aFullTeamCannotGainAnAdditionalResponsibleButCanPromoteAnAcceptedGraduate() throws Exception {
        var start=freeSlot(); var admin=new Client().login("jefatura@demo.pe"); var body=callBody(start,"F-"+UUID.randomUUID());
        body.put("totalVolunteers",1);
        UUID id=UUID.fromString(admin.ok("/callouts","POST",body).get("id").asText());
        admin.ok("/callouts/"+id+"/status","POST",Map.of("status","ABIERTA"));
        new Client().login("maria@demo.pe").enroll(id,"SOCORRISTA"); UUID original=enrollment(id,maria);
        assertEquals(400,admin.request("/callouts/"+id+"/responsible","PUT",Map.of("userId",UUID.fromString("00000000-0000-0000-0000-000000000002"))).statusCode());
        admin.ok("/callouts/"+id+"/responsible","PUT",Map.of("userId",maria));
        assertEquals(original,enrollment(id,maria));
        assertEquals("RESPONSABLE",db.queryForObject("SELECT position FROM enrollments WHERE id=?",String.class,original));
        assertEquals("CERRADA",db.queryForObject("SELECT status FROM callouts WHERE id=?",String.class,id));
    }
    @Test void legacyTrainingEnrollmentsKeepTheirReplacementFlowWithoutNewTrainingOnlySeats() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(3)); UUID original=fixtureEnrollment(id,lucia);
        db.update("UPDATE enrollments SET position='FORMACION' WHERE id=?",original);
        var trainee=new Client().login("voluntario@demo.pe"); var source=new Client().login("lucia@demo.pe"); var admin=new Client().login("jefatura@demo.pe");
        assertFalse(trainee.ok("/callouts/"+id,"GET",null).get("eligible_positions").toString().contains("FORMACION"));
        source.ok("/enrollments/"+original+"/cancel-request","POST",Map.of("reason","Coordinar reemplazo del registro anterior"));
        assertTrue(trainee.ok("/callouts/"+id,"GET",null).get("eligible_positions").toString().contains("FORMACION"));
        assertEquals(400,trainee.request("/callouts/"+id+"/enrollments","POST",Map.of("position","FORMACION","requirementsAccepted",true)).statusCode());
        trainee.ok("/callouts/"+id+"/enrollments","POST",Map.of("position","FORMACION","requirementsAccepted",true,"replacementFor",original));
        admin.ok("/enrollments/"+enrollment(id,volunteer)+"/review","POST",Map.of("approved",true,"comment","Reemplazo histórico compatible"));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND status='CONFIRMADA'",Integer.class,id));
    }
    @Test void chiefCanDirectlyCompleteACalloutAndNotifyTheSelectedVolunteer() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(3)); var admin=new Client().login("jefatura@demo.pe");
        var user=new Client().login("voluntario@demo.pe");
        assertTrue(admin.ok("/callouts/"+id+"/assignment-candidates?position=SOCORRISTA","GET",null).toString().contains(volunteer.toString()));
        assertEquals(403,user.request("/callouts/"+id+"/assignment-candidates?position=SOCORRISTA","GET",null).statusCode());
        assertEquals(403,user.request("/callouts/"+id+"/assignments","POST",Map.of("userId",lucia,"position","SOCORRISTA","requirementsVerified",true)).statusCode());
        assertEquals(400,admin.request("/callouts/"+id+"/assignments","POST",Map.of("userId",volunteer,"position","SOCORRISTA","requirementsVerified",false)).statusCode());
        admin.assign(id,volunteer,"SOCORRISTA");
        var e=db.queryForMap("SELECT * FROM enrollments WHERE callout_id=? AND volunteer_id=?",id,volunteer);
        assertEquals("CONFIRMADA",e.get("status")); assertEquals(chief,e.get("assigned_by")); assertNotNull(e.get("assigned_at")); assertEquals(true,e.get("requirements_accepted"));
        assertEquals("CERRADA",db.queryForObject("SELECT status FROM callouts WHERE id=?",String.class,id));
        assertEquals(1,user.ok("/schedule?date="+start.toLocalDate(),"GET",null).size());
        assertTrue(user.ok("/notifications","GET",null).toString().contains("Jefatura te inscribió directamente"));
        assertEquals(400,admin.request("/callouts/"+id+"/assignments","POST",Map.of("userId",volunteer,"position","SOCORRISTA","requirementsVerified",true)).statusCode());
        assertEquals(400,admin.request("/callouts/"+id+"/assignments","POST",Map.of("userId",lucia,"position","SOCORRISTA","requirementsVerified",true)).statusCode());
        assertEquals(0,admin.ok("/callouts/"+id+"/assignment-candidates?position=SOCORRISTA","GET",null).size());
    }
    @Test void directAssignmentIgnoresLegacyLevelsButChecksStatusGraduationAndQualifications() throws Exception {
        var start=freeSlot(); var admin=new Client().login("jefatura@demo.pe"); var id=service(start,start.plusHours(3));
        db.update("UPDATE callouts SET minimum_level='AVANZADO' WHERE id=?",id);
        assertTrue(admin.ok("/callouts/"+id+"/assignment-candidates?position=SOCORRISTA","GET",null).toString().contains(volunteer.toString()));
        UUID inactive=UUID.fromString("00000000-0000-0000-0000-000000000010");
        assertEquals(400,admin.request("/callouts/"+id+"/assignments","POST",Map.of("userId",inactive,"position","SOCORRISTA","requirementsVerified",true)).statusCode());
        db.update("UPDATE callouts SET minimum_level='BASICO',graduate_slots=1 WHERE id=?",id);
        assertFalse(admin.ok("/callouts/"+id+"/assignment-candidates?position=SOCORRISTA","GET",null).toString().contains(volunteer.toString()));
        assertEquals(400,admin.request("/callouts/"+id+"/assignments","POST",Map.of("userId",volunteer,"position","SOCORRISTA","requirementsVerified",true)).statusCode());
        assertEquals(400,admin.request("/callouts/"+id+"/assignments","POST",Map.of("userId",volunteer,"position","GRADUADO","requirementsVerified",true)).statusCode());
        db.update("UPDATE callouts SET graduate_slots=0,pilot_slots=1 WHERE id=?",id);
        boolean old=db.queryForObject("SELECT pilot_qualified FROM volunteer_profiles WHERE user_id=?",Boolean.class,maria);
        try {
            db.update("UPDATE volunteer_profiles SET pilot_qualified=false WHERE user_id=?",maria);
            assertEquals(400,admin.request("/callouts/"+id+"/assignments","POST",Map.of("userId",maria,"position","PILOTO","requirementsVerified",true)).statusCode());
            assertEquals(400,admin.request("/callouts/"+id+"/assignments","POST",Map.of("userId",maria,"position","SOCORRISTA","requirementsVerified",true)).statusCode());
            db.update("UPDATE volunteer_profiles SET pilot_qualified=true WHERE user_id=?",maria);
            admin.assign(id,maria,"PILOTO");
        } finally { db.update("UPDATE volunteer_profiles SET pilot_qualified=? WHERE user_id=?",old,maria); }
    }
    @Test void directAssignmentsRespectOverlapsBoundariesAndClosedEnrollmentPeriods() throws Exception {
        var start=freeSlot(); var admin=new Client().login("jefatura@demo.pe");
        var first=service(start,start.plusHours(3)); var clash=service(start.plusHours(1),start.plusHours(4)); var boundary=service(start.plusHours(3),start.plusHours(5));
        admin.assign(first,volunteer,"SOCORRISTA");
        assertFalse(admin.ok("/callouts/"+clash+"/assignment-candidates?position=SOCORRISTA","GET",null).toString().contains(volunteer.toString()));
        assertEquals(400,admin.request("/callouts/"+clash+"/assignments","POST",Map.of("userId",volunteer,"position","SOCORRISTA","requirementsVerified",true)).statusCode());
        admin.assign(boundary,volunteer,"SOCORRISTA");
        var expired=service(start.plusDays(1),start.plusDays(1).plusHours(3)); db.update("UPDATE callouts SET deadline=? WHERE id=?",Store.now().minusSeconds(1),expired);
        assertEquals(400,admin.request("/callouts/"+expired+"/assignments","POST",Map.of("userId",lucia,"position","SOCORRISTA","requirementsVerified",true)).statusCode());
        var draft=service(start.plusDays(2),start.plusDays(2).plusHours(3)); db.update("UPDATE callouts SET status='BORRADOR' WHERE id=?",draft);
        assertEquals(400,admin.request("/callouts/"+draft+"/assignments","POST",Map.of("userId",lucia,"position","SOCORRISTA","requirementsVerified",true)).statusCode());
        db.update("UPDATE callouts SET status='CANCELADA' WHERE id=?",draft);
        assertEquals(400,admin.request("/callouts/"+draft+"/assignments","POST",Map.of("userId",lucia,"position","SOCORRISTA","requirementsVerified",true)).statusCode());
    }
    @Test void directAndSelfEnrollmentRaceForTheSameTotalWithoutOverbooking() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(3)); var admin=new Client().login("jefatura@demo.pe"); var user=new Client().login("voluntario@demo.pe");
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(() -> admin.request("/callouts/"+id+"/assignments","POST",Map.of("userId",lucia,"position","SOCORRISTA","requirementsVerified",true)).statusCode());
            var b=pool.submit(() -> user.request("/callouts/"+id+"/enrollments","POST",Map.of("position","SOCORRISTA","requirementsAccepted",true)).statusCode());
            var results=new ArrayList<>(List.of(a.get(),b.get())); Collections.sort(results); assertEquals(List.of(200,400),results);
        } finally { pool.shutdownNow(); }
        assertEquals(1,db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND status='CONFIRMADA'",Integer.class,id));
    }
    @Test void responsiblePermissionsAreLocalAndFormerRecruitersHaveNoGlobalPrivileges() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(3)); var other=service(start,start.plusHours(3));
        UUID formerRecruiter=UUID.fromString("00000000-0000-0000-0000-000000000002");
        db.update("UPDATE callouts SET created_by=? WHERE id=?",formerRecruiter,id);
        var account=new Client().login("reclutador@demo.pe"); var admin=new Client().login("jefatura@demo.pe");
        assertEquals("VOLUNTARIO",account.ok("/auth/me","GET",null).get("role").asText());
        assertFalse(account.ok("/callouts/"+id,"GET",null).get("can_edit").asBoolean());
        assertEquals(403,account.request("/callouts","POST",callBody(start,"NO-"+UUID.randomUUID())).statusCode());
        assertEquals(403,account.request("/callouts/"+id+"/enrollments","GET",null).statusCode());
        admin.assign(id,formerRecruiter,"SOCORRISTA");
        admin.ok("/callouts/"+id+"/responsible","PUT",Map.of("userId",formerRecruiter));
        assertTrue(account.ok("/callouts/"+id,"GET",null).get("can_edit").asBoolean());
        assertTrue(account.ok("/callouts/"+id,"GET",null).get("can_upload_photo").asBoolean());
        assertEquals(403,account.request("/callouts/"+other+"/enrollments","GET",null).statusCode());
        assertEquals(403,account.request("/callouts/"+other+"/assignments","POST",Map.of("userId",lucia,"position","SOCORRISTA","requirementsVerified",true)).statusCode());
        var clear=new HashMap<String,Object>(); clear.put("userId",null); clear.put("expectedResponsibleId",formerRecruiter); clear.put("reason","Retiro explícito de permisos"); admin.ok("/callouts/"+id+"/responsible/change","POST",clear);
        assertFalse(account.ok("/callouts/"+id,"GET",null).get("can_operate").asBoolean());
    }
    @Test void responsibleRequiresAVolunteerGraduateAndRetiredRolesCannotBeAssigned() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(3)); var admin=new Client().login("jefatura@demo.pe");
        assertEquals(400,admin.request("/callouts/"+id+"/responsible","PUT",Map.of("userId",volunteer)).statusCode());
        for(String role:List.of("RECLUTADOR","RESPONSABLE")) assertEquals(400,admin.request("/users/"+maria,"PUT",Map.of("role",role,"level","AVANZADO","graduated",true,"status","ACTIVO","comment","Rol retirado")).statusCode());
        assertEquals(0,db.queryForObject("SELECT count(*) FROM users WHERE role='RECLUTADOR'",Integer.class));
        boolean graduated=db.queryForObject("SELECT graduated FROM users WHERE id=?",Boolean.class,chief);
        try {
            db.update("UPDATE users SET graduated=true WHERE id=?",chief);
            assertFalse(admin.ok("/callouts/"+id+"/responsible-candidates","GET",null).toString().contains(chief.toString()));
            assertEquals(400,admin.request("/callouts/"+id+"/responsible","PUT",Map.of("userId",chief)).statusCode());
        } finally { db.update("UPDATE users SET graduated=? WHERE id=?",graduated,chief); }
    }
    @Test void changingVolunteerConditionSelectsTheOtherGeneralTargetWithoutResettingHours() throws Exception {
        UUID userId=UUID.randomUUID(); String email="condition-"+userId+"@demo.pe";
        db.update("INSERT INTO users(id,name,email,password_hash,role,graduated,status) SELECT ?,'Meta por condición',?,password_hash,'VOLUNTARIO',false,'ACTIVO' FROM users WHERE id=?",userId,email,volunteer);
        db.update("INSERT INTO volunteer_profiles(user_id,document,training_level) VALUES (?,?,'BASICO')",userId,"T-"+userId.toString().substring(0,12));
        var admin=new Client().login("jefatura@demo.pe"); var user=new Client().login(email);
        admin.ok("/administration","PUT",Map.of("trainingHours",120,"graduateHours",200));
        var before=user.ok("/attendance/progress","GET",null);
        assertEquals(120,before.get("progress").get("required").asDouble());
        admin.ok("/users/"+userId,"PUT",Map.of("role","VOLUNTARIO","level","BASICO","graduated",true,"status","ACTIVO","comment","Graduación validada por jefatura"));
        var after=user.ok("/attendance/progress","GET",null);
        assertEquals("GRADUADO",after.get("condition").asText()); assertEquals(200,after.get("progress").get("required").asDouble());
        assertEquals(before.get("progress").get("balance").asDouble(),after.get("progress").get("balance").asDouble());
        assertEquals("VOLUNTARIO",user.ok("/auth/me","GET",null).get("role").asText());
    }
    @Test void retiredLevelsAreNeitherRequiredNorExposedAndCannotBlockParticipation() throws Exception {
        var admin=new Client().login("jefatura@demo.pe"); var user=new Client().login("voluntario@demo.pe");
        var body=callBody(freeSlot(),"LV-"+UUID.randomUUID()); body.remove("level"); body.remove("contact");
        UUID id=UUID.fromString(admin.ok("/callouts","POST",body).get("id").asText());
        db.update("UPDATE callouts SET minimum_level='AVANZADO' WHERE id=?",id);
        admin.ok("/callouts/"+id+"/status","POST",Map.of("status","ABIERTA"));
        assertFalse(user.ok("/callouts/"+id,"GET",null).has("minimum_level"));
        user.enroll(id,"SOCORRISTA");
        assertFalse(user.ok("/profile","GET",null).has("training_level"));
        admin.ok("/users/"+volunteer,"PUT",Map.of("role","VOLUNTARIO","graduated",false,"status","ACTIVO","comment","Sin niveles"));
        assertEquals("CONFIRMADA",db.queryForObject("SELECT status FROM enrollments WHERE callout_id=? AND volunteer_id=?",String.class,id,volunteer));
    }
    @Test void responsibilityChangesAreExplicitAuditedAndKeepTheOldVolunteerEnrolled() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(3)); db.update("UPDATE callouts SET total_volunteers=3 WHERE id=?",id);
        UUID luis=UUID.fromString("00000000-0000-0000-0000-000000000002");
        var admin=new Client().login("jefatura@demo.pe"); var mariaClient=new Client().login("maria@demo.pe");
        var outsider=new Client().login("lucia@demo.pe"); var participant=new Client().login("voluntario@demo.pe");
        assertEquals(400,admin.request("/callouts/"+id+"/responsible","PUT",Map.of("userId",maria)).statusCode());
        admin.assign(id,maria,"SOCORRISTA"); admin.assign(id,luis,"SOCORRISTA");
        assertFalse(mariaClient.ok("/callouts/"+id,"GET",null).get("can_operate").asBoolean());
        admin.ok("/callouts/"+id+"/responsible","PUT",Map.of("userId",maria));
        assertEquals("Consultar a jefatura",outsider.ok("/callouts/"+id,"GET",null).get("contact").asText());
        assertEquals("Consultar a jefatura",participant.ok("/callouts/"+id,"GET",null).get("contact").asText());
        participant.enroll(id,"SOCORRISTA");
        String phone=db.queryForObject("SELECT phone FROM volunteer_profiles WHERE user_id=?",String.class,maria);
        assertEquals(phone,participant.ok("/callouts/"+id,"GET",null).get("contact").asText());
        assertEquals(400,admin.request("/callouts/"+id+"/responsible","PUT",Map.of("userId",luis)).statusCode());
        assertEquals(400,admin.request("/callouts/"+id+"/responsible/change","POST",Map.of("expectedResponsibleId",maria,"userId",luis,"reason","")).statusCode());
        admin.ok("/callouts/"+id+"/responsible/change","POST",Map.of("expectedResponsibleId",maria,"userId",luis,"reason","Cambio de coordinador verificado"));
        assertFalse(mariaClient.ok("/callouts/"+id,"GET",null).get("can_operate").asBoolean());
        assertEquals("SOCORRISTA",db.queryForObject("SELECT position FROM enrollments WHERE callout_id=? AND volunteer_id=?",String.class,id,maria));
        assertEquals(3,db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND status='CONFIRMADA'",Integer.class,id));
        assertEquals(2,admin.ok("/callouts/"+id+"/responsible/history","GET",null).size());
        assertEquals(400,admin.request("/callouts/"+id+"/responsible/change","POST",Map.of("expectedResponsibleId",maria,"userId",luis,"reason","Pantalla obsoleta")).statusCode());
        var clear=new HashMap<String,Object>(); clear.put("expectedResponsibleId",luis); clear.put("userId",null); clear.put("reason","Sin encargado designado");
        admin.ok("/callouts/"+id+"/responsible/change","POST",clear);
        assertEquals("Consultar a jefatura",participant.ok("/callouts/"+id,"GET",null).get("contact").asText());
    }
    @Test void closedMonthJustificationDecisionsAppendReversibleAdjustmentsWithoutRewritingTheSnapshot() throws Exception {
        var month=YearMonth.of(1990,1); while(db.queryForObject("SELECT count(*) FROM monthly_reports WHERE month=?",Integer.class,month.atDay(1))>0) month=month.plusMonths(1);
        var start=month.atDay(10).atTime(8,0); var end=start.plusHours(3); var id=service(start,end); var e=fixtureEnrollment(id,volunteer);
        var admin=new Client().login("jefatura@demo.pe"); var user=new Client().login("voluntario@demo.pe"); user.ok("/attendance","GET",null);
        UUID a=db.queryForObject("SELECT id FROM attendance_records WHERE enrollment_id=?",UUID.class,e);
        admin.ok("/attendance/"+a+"/confirm","POST",Map.of("checkIn",start.plusMinutes(20).toString(),"checkOut",end.toString(),"observations","Marca tardía revisada"));
        admin.ok("/reports/approve?month="+month,"POST",Map.of());
        var snapshot=db.queryForMap("SELECT * FROM monthly_report_rows WHERE month=? AND user_id=?",month.atDay(1),volunteer);
        user.ok("/attendance/"+a+"/justifications","POST",Map.of("reason","Evidencia presentada después del cierre"));
        UUID j=db.queryForObject("SELECT id FROM justifications WHERE absence_id=?",UUID.class,a);
        assertEquals(403,user.request("/justifications/"+j+"/resolve","POST",Map.of("approved",true,"comment","Sin permiso")).statusCode());
        admin.ok("/justifications/"+j+"/resolve","POST",Map.of("approved",true,"comment","Evidencia verificada por jefatura"));
        var after=user.ok("/attendance/hours?month="+month,"GET",null);
        double original=((Number)snapshot.get("hours")).doubleValue();
        assertEquals(original+40.0/60,after.get("hours").asDouble(),.0002); assertEquals(original,after.get("original_hours").asDouble());
        admin.ok("/justifications/"+j+"/resolve","POST",Map.of("approved",true,"comment","Revisión repetida sin duplicar ajuste"));
        assertEquals(1,db.queryForObject("SELECT count(*) FROM monthly_report_adjustments WHERE month=? AND user_id=?",Integer.class,month.atDay(1),volunteer));
        admin.ok("/justifications/"+j+"/resolve","POST",Map.of("approved",false,"comment","Nueva evidencia: se revoca la aprobación"));
        assertEquals(original,user.ok("/attendance/hours?month="+month,"GET",null).get("hours").asDouble(),.0002);
        assertEquals(snapshot,db.queryForMap("SELECT * FROM monthly_report_rows WHERE month=? AND user_id=?",month.atDay(1),volunteer));
        assertEquals(3,db.queryForObject("SELECT count(*) FROM justification_resolutions WHERE justification_id=?",Integer.class,j));
        assertEquals(chief,db.queryForObject("SELECT resolved_by FROM justifications WHERE id=?",UUID.class,j));
        assertTrue(admin.ok("/justifications","GET",null).toString().contains("Nueva evidencia"));
        var report=admin.ok("/reports?month="+month,"GET",null);
        for(var row:report) if(row.get("id").asText().equals(volunteer.toString())) assertEquals(original,row.get("hours").asDouble(),.0002);
    }
    @Test void chiefsHaveNoTargetPenaltyJustificationOrNewVolunteerEnrollment() throws Exception {
        var admin=new Client().login("jefatura@demo.pe"); assertFalse(admin.ok("/attendance/progress","GET",null).get("applicable").asBoolean());
        var start=freeSlot(); var future=service(start,start.plusHours(3));
        assertEquals(400,admin.request("/callouts/"+future+"/enrollments","POST",Map.of("position","SOCORRISTA","requirementsAccepted",true)).statusCode());
        var end=Store.now().minusHours(2); var past=service(end.minusHours(3),end); var e=fixtureEnrollment(past,chief);
        admin.ok("/attendance","GET",null); UUID a=db.queryForObject("SELECT id FROM attendance_records WHERE enrollment_id=?",UUID.class,e);
        assertEquals(403,admin.request("/attendance/"+a+"/justifications","POST",Map.of("reason","No corresponde")).statusCode());
        var records=admin.ok("/attendance","GET",null);
        for(var record:records) assertEquals(0,record.get("deduction").asDouble());
        assertEquals(0,admin.ok("/attendance/hours?month="+YearMonth.from(end),"GET",null).get("deductions").asDouble());
    }
    @Test void onlyTheConfirmedResponsibleCanReviewThisTeamOrUploadItsPhoto() throws Exception {
        var start=freeSlot(); var id=service(start,start.plusHours(3)); var other=service(start,start.plusHours(3));
        db.update("UPDATE callouts SET total_volunteers=2 WHERE id=?",id);
        var admin=new Client().login("jefatura@demo.pe"); var manager=new Client().login("maria@demo.pe");
        admin.assign(id,maria,"SOCORRISTA"); admin.assign(id,volunteer,"SOCORRISTA");
        admin.ok("/callouts/"+id+"/responsible","PUT",Map.of("userId",maria));
        var image=new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB); var bytes=new java.io.ByteArrayOutputStream(); javax.imageio.ImageIO.write(image,"png",bytes);
        assertEquals(200,manager.photo(id,bytes.toByteArray())); assertEquals(403,manager.photo(other,bytes.toByteArray()));
        var end=Store.now().minusHours(2).withSecond(0).withNano(0); var pastStart=end.minusHours(3);
        db.update("UPDATE callouts SET start_at=?,end_at=?,opened_at=?,status='CERRADA' WHERE id=?",pastStart,end,pastStart,id);
        var attendance=manager.ok("/callouts/"+id+"/attendance","GET",null); UUID record=null;
        for(var a:attendance) if(a.get("volunteer_id").asText().equals(volunteer.toString())) record=UUID.fromString(a.get("id").asText());
        assertNotNull(record);
        manager.ok("/attendance/"+record+"/confirm","POST",Map.of("checkIn",pastStart.toString(),"checkOut",end.toString(),"observations","Equipo del responsable confirmado"));
        manager.ok("/callouts/"+id+"/operation","POST",Map.of("action","CERRAR","observations","Cierre sin foto obligatoria"));
        assertEquals(200,manager.photo(id,bytes.toByteArray()));
        db.update("UPDATE enrollments SET position='SOCORRISTA' WHERE callout_id=? AND volunteer_id=?",id,maria);
        assertEquals(403,manager.photo(id,bytes.toByteArray()));
        assertEquals(403,manager.request("/attendance/"+record+"/confirm","POST",Map.of("checkIn",pastStart.toString(),"checkOut",end.toString(),"observations","Sin permisos vigentes")).statusCode());
    }
}
