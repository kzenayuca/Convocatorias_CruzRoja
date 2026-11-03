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

/** Solo se habilita contra una base aislada explícita; jamás usa la base local por defecto. */
@EnabledIfEnvironmentVariable(named="TEST_DB_URL",matches=".+")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "spring.datasource.url=${TEST_DB_URL}","spring.datasource.username=${TEST_DB_USER:convocatorias}",
    "spring.datasource.password=${TEST_DB_PASSWORD:convocatorias}"})
class WorkflowTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    private final UUID volunteer=UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID chief=UUID.fromString("00000000-0000-0000-0000-000000000003");

    class Client {
        HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        String token,header;
        HttpResponse<String> request(String path,String method,Object body) throws Exception {
            if(!method.equals("GET")) {
                var csrf=json.readTree(http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body());
                token=csrf.get("token").asText(); header=csrf.get("headerName").asText();
            }
            var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path));
            if(!method.equals("GET")) builder.header(header,token);
            builder.header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            return http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        }
        JsonNode ok(String path,String method,Object body) throws Exception {
            var r=request(path,method,body); assertTrue(r.statusCode()>=200 && r.statusCode()<300,r.statusCode()+" "+r.body());
            return r.body().isBlank()?json.nullNode():json.readTree(r.body());
        }
        Client login(String email) throws Exception { ok("/auth/login","POST",Map.of("email",email,"password","convocatorias")); return this; }
    }
    UUID service(LocalDateTime start,LocalDateTime end) {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO callouts(id,title,start_at,end_at,minimum_level,capacity,minimum_participants,status,responsible_id) VALUES (?,'Prueba aislada',?,?,'BASICO',1,1,'PUBLICADA',?)",id,start,end,chief);
        return id;
    }
    LocalDateTime freeFutureSlot() {
        var start=Store.now().plusDays(1000).withHour(10).withMinute(0).withSecond(0).withNano(0);
        while(db.queryForObject("SELECT count(*) FROM callouts WHERE start_at<? AND end_at>?",Integer.class,start.plusHours(3),start)>0) start=start.plusDays(1);
        return start;
    }
    UUID enroll(UUID service, UUID user,String status) {
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO enrollments(id,volunteer_id,callout_id,status) VALUES (?,?,?,?)",id,user,service,status); return id;
    }

    @Test void accountApprovalPhoneAndSessionRevocation() throws Exception {
        var c=new Client(); String suffix=String.valueOf(System.nanoTime()); String email="test"+suffix+"@demo.pe";
        String document=String.format("%08d",Math.abs(new Random().nextInt(90000000))+10000000);
        c.ok("/auth/register","POST",Map.of("profile",Map.of("name","Prueba","surnames","Cuenta","email",email,"document",document,"occupation","Estudiante"),"phone","900000000","password","convocatorias"));
        assertEquals(401,c.request("/auth/login","POST",Map.of("email",email,"password","convocatorias")).statusCode());
        var admin=new Client().login("jefatura@demo.pe");
        UUID id=db.queryForObject("SELECT id FROM users WHERE email=?",UUID.class,email);
        var review=Map.of("role","VOLUNTARIO","level","BASICO","graduated",false,"status","ACTIVO","comment","Cuenta revisada");
        admin.ok("/users/"+id,"PUT",review); c.login(email);
        c.ok("/profile/phone","PUT",Map.of("phone","999111222"));
        assertEquals("999111222",c.ok("/profile","GET",null).get("phone").asText());
        assertEquals(403,c.request("/users","GET",null).statusCode());
        admin.ok("/users/"+id,"PUT",Map.of("role","VOLUNTARIO","level","BASICO","graduated",false,"status","SUSPENDIDO","comment","Decisión manual"));
        assertEquals(401,c.request("/profile","GET",null).statusCode());
    }
    @Test void capacityMinimumAndPermissions() throws Exception {
        var admin=new Client().login("jefatura@demo.pe"); var c=new Client().login("voluntario@demo.pe");
        var start=freeFutureSlot(); UUID id=service(start,start.plusHours(3));
        c.ok("/callouts/"+id+"/enrollments","POST",Map.of("requirementsAccepted",true));
        assertEquals(400,c.request("/callouts/"+id+"/enrollments","POST",Map.of("requirementsAccepted",true)).statusCode());
        UUID enrollment=db.queryForObject("SELECT id FROM enrollments WHERE callout_id=?",UUID.class,id);
        assertEquals(403,c.request("/enrollments/"+enrollment+"/review","POST",Map.of("approved",true,"comment","No autorizado")).statusCode());
        admin.ok("/enrollments/"+enrollment+"/review","POST",Map.of("approved",true,"comment","Perfil compatible"));
        var other=new Client().login("lucia@demo.pe");
        assertEquals(400,other.request("/callouts/"+id+"/enrollments","POST",Map.of("requirementsAccepted",true)).statusCode());
        admin.ok("/callouts/"+id+"/status","POST",Map.of("status","CONFIRMADA"));
        c.ok("/auth/logout","POST",Map.of()); assertEquals(401,c.request("/enrollments","GET",null).statusCode());
    }
    @Test void deductionAndLateJustificationAreIdempotent() throws Exception {
        var end=Store.now().minusHours(50); var start=end.minusHours(3); UUID id=service(start,end);
        UUID enrollment=enroll(id,volunteer,"REEMPLAZADA");
        UUID replacement=enroll(id,UUID.fromString("00000000-0000-0000-0000-000000000006"),"CONFIRMADA");
        db.update("UPDATE enrollments SET replacement_for=?,reviewed_at=? WHERE id=?",enrollment,start.minusMinutes(1),replacement);
        var c=new Client().login("voluntario@demo.pe"); var admin=new Client().login("jefatura@demo.pe");
        String month=YearMonth.from(start).toString();
        double before=c.ok("/attendance/hours?month="+month,"GET",null).get("hours").asDouble();
        assertEquals(before,c.ok("/attendance/hours?month="+month,"GET",null).get("hours").asDouble());
        UUID attendance=db.queryForObject("SELECT id FROM attendance_records WHERE enrollment_id=?",UUID.class,enrollment);
        c.ok("/attendance/"+attendance+"/justifications","POST",Map.of("reason","Reemplazo confirmado; presentación excepcional"));
        UUID justification=db.queryForObject("SELECT id FROM justifications WHERE absence_id=?",UUID.class,attendance);
        assertTrue(db.queryForObject("SELECT late FROM justifications WHERE id=?",Boolean.class,justification));
        admin.ok("/justifications/"+justification+"/resolve","POST",Map.of("approved",true,"comment","Excepción aceptada"));
        assertEquals(before+6,c.ok("/attendance/hours?month="+month,"GET",null).get("hours").asDouble(),.0001);
        admin.ok("/justifications/"+justification+"/resolve","POST",Map.of("approved",true,"comment","Revisión sin duplicar abono"));
        assertEquals(before+6,c.ok("/attendance/hours?month="+month,"GET",null).get("hours").asDouble(),.0001);
    }
    @Test void pendingAtServiceStartIsRejectedAndCsrfIsRequired() throws Exception {
        var end=Store.now().minusHours(1); UUID id=service(end.minusHours(1),end); UUID e=enroll(id,volunteer,"PENDIENTE");
        var c=new Client().login("voluntario@demo.pe"); c.ok("/enrollments","GET",null);
        assertEquals("RECHAZADA",db.queryForObject("SELECT status FROM enrollments WHERE id=?",String.class,e));
        var response=c.http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/profile/phone")).header("Content-Type","application/json").PUT(HttpRequest.BodyPublishers.ofString("{\"phone\":\"123\"}")).build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(403,response.statusCode());
    }

    @Test void confirmationClipsHoursAndCorrectionRequestDoesNotEraseAbsence() throws Exception {
        var end=Store.now().minusHours(1); var start=end.minusHours(1); UUID id=service(start,end);
        UUID e=enroll(id,volunteer,"CONFIRMADA");
        var c=new Client().login("voluntario@demo.pe"); var admin=new Client().login("jefatura@demo.pe");
        c.ok("/attendance","GET",null);
        UUID a=db.queryForObject("SELECT id FROM attendance_records WHERE enrollment_id=?",UUID.class,e);
        c.ok("/enrollments/"+e+"/correction","POST",Map.of("reason","Solicito revisar mi entrada"));
        assertEquals("AUSENTE",db.queryForObject("SELECT status FROM attendance_records WHERE id=?",String.class,a));
        admin.ok("/attendance/"+a+"/confirm","POST",Map.of("checkIn",start.minusMinutes(10).toString(),"checkOut",end.plusMinutes(10).toString(),"observations","Marcas verificadas"));
        assertEquals(1,db.queryForObject("SELECT approved_hours FROM attendance_records WHERE id=?",Double.class,a));
        assertEquals(400,c.request("/attendance/"+a+"/justifications","POST",Map.of("reason","No hay ausencia")).statusCode());
    }

    @Test void simultaneousApprovalsCannotOverbook() throws Exception {
        var start=freeFutureSlot(); UUID id=service(start,start.plusHours(1));
        UUID first=enroll(id,volunteer,"PENDIENTE"), second=enroll(id,UUID.fromString("00000000-0000-0000-0000-000000000006"),"PENDIENTE");
        var a=new Client().login("jefatura@demo.pe"); var b=new Client().login("jefatura@demo.pe");
        try(var executor=new AutoCloseExecutor()) {
            var r1=executor.pool.submit(() -> a.request("/enrollments/"+first+"/review","POST",Map.of("approved",true,"comment","Primera revisión")).statusCode());
            var r2=executor.pool.submit(() -> b.request("/enrollments/"+second+"/review","POST",Map.of("approved",true,"comment","Segunda revisión")).statusCode());
            assertEquals(Set.of(200,400),Set.of(r1.get(),r2.get()));
        }
        assertEquals(1,db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND status='CONFIRMADA'",Integer.class,id));
    }
    static class AutoCloseExecutor implements AutoCloseable {
        final java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        public void close() { pool.shutdownNow(); }
    }
}
