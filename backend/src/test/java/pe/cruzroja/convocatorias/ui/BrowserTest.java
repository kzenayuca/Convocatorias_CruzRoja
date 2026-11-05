package pe.cruzroja.convocatorias.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.*;

/** Selenium se ejecuta con JUnit: -De2e.url=http://localhost:5173 */
@EnabledIfSystemProperty(named="e2e.url",matches=".+")
class BrowserTest {
    WebDriver browser;
    WebDriverWait wait;
    @BeforeEach void open() throws Exception {
        var options=new ChromeOptions(); options.addArguments("--headless=new","--no-sandbox","--disable-dev-shm-usage","--window-size=1440,1000");
        String remote=System.getProperty("selenium.url");
        browser=remote==null?new ChromeDriver(options):new RemoteWebDriver(URI.create(remote).toURL(),options);
        wait=new WebDriverWait(browser,Duration.ofSeconds(20)); browser.get(System.getProperty("e2e.url"));
    }
    @AfterEach void close() { if(browser!=null) browser.quit(); }
    void click(String text) {
        var element=wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[self::button or self::a][text()[normalize-space(.)='"+text+"']]")));
        ((JavascriptExecutor)browser).executeScript("arguments[0].scrollIntoView({block:'center'})",element);
        element.click();
    }
    @Test void volunteerCanLoginReadDetailsAndLogout() {
        click("Iniciar sesión");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".welcome")));
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("target"));
            java.nio.file.Files.write(java.nio.file.Path.of("target/home-browser.png"),((TakesScreenshot)browser).getScreenshotAs(OutputType.BYTES));
        } catch(java.io.IOException error) { throw new RuntimeException(error); }
        click("Convocatorias");
        wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Ver detalle"))).click();
        wait.until(d -> d.getCurrentUrl().contains("/callouts/") && d.findElement(By.tagName("main")).getText().toLowerCase().contains("vacantes disponibles"));
        assertFalse(browser.findElements(By.xpath("//h2[text()='Verificación de requisitos']")).isEmpty());
        click("Mi perfil"); wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),"Información personal"));
        screenshot("perfil-browser.png");
        assertTrue(browser.findElements(By.name("phone")).isEmpty()); click("Modificar");
        var phone=browser.findElement(By.name("phone")); phone.clear(); phone.sendKeys("999888777");
        browser.findElement(By.cssSelector("form button[type=submit], form button.button")).click();
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),"Guardado correctamente"));
        browser.navigate().refresh(); wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),"999888777"));
        click("Salir"); wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".login-card")));
        browser.navigate().refresh(); wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".login-card")));
    }
    @Test void registrationIsPendingAndDoesNotAllowImmediateLogin() {
        click("Solicitar una cuenta"); String suffix=String.valueOf(System.nanoTime()); String email="browser"+suffix+"@demo.pe";
        browser.findElement(By.name("name")).sendKeys("Prueba"); browser.findElement(By.name("surnames")).sendKeys("Navegador");
        browser.findElement(By.name("email")).sendKeys(email);
        browser.findElement(By.name("document")).sendKeys(String.format("%08d",Math.abs(suffix.hashCode()%90000000)+10000000));
        browser.findElement(By.name("occupation")).sendKeys("Estudiante"); browser.findElement(By.name("phone")).sendKeys("900000000");
        browser.findElement(By.name("password")).sendKeys("convocatorias"); click("Solicitar cuenta");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.cssSelector(".login-card"),"Solicitud registrada"));
        click("Volver al inicio de sesión"); var input=browser.findElement(By.cssSelector("input[type=email]")); input.clear(); input.sendKeys(email);
        click("Iniciar sesión"); wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[role=alert]")));
        assertTrue(browser.findElements(By.cssSelector(".welcome")).isEmpty());
    }

    void fill(String name,String value) {
        var input=browser.findElement(By.name(name)); input.clear(); input.sendKeys(value);
    }
    void screenshot(String filename) {
        ((JavascriptExecutor)browser).executeScript("window.scrollTo(0,0)");
        try { java.nio.file.Files.write(java.nio.file.Path.of("target",filename),((TakesScreenshot)browser).getScreenshotAs(OutputType.BYTES)); }
        catch(java.io.IOException error) { throw new RuntimeException(error); }
    }
    @SuppressWarnings("unchecked")
    java.util.Map<String,Object> request(String path,String method,Object body) {
        var result=(java.util.Map<String,Object>)((JavascriptExecutor)browser).executeAsyncScript("""
            const [path,method,body,done]=arguments;
            (async()=>{
              const csrf=await fetch('/api/v1/auth/csrf').then(r=>r.json());
              const r=await fetch('/api/v1'+path,{method,headers:{'Content-Type':'application/json',[csrf.headerName]:csrf.token},body:JSON.stringify(body)});
              const text=await r.text(); done({status:r.status,data:text?JSON.parse(text):null});
            })().catch(e=>done({status:0,data:e.message}));
            """,path,method,body);
        assertTrue(((Number)result.get("status")).intValue()/100==2,result.toString());
        return result;
    }
    @Test void chiefSetsConditionTargetsAndDirectlyEnrollsAnEligibleVolunteer() {
        var email=wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("input[type=email]")));
        email.clear(); email.sendKeys("jefatura@demo.pe"); click("Iniciar sesión");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".welcome"))); click("Administración");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("trainingHours")));
        fill("trainingHours","120"); fill("graduateHours","200"); click("Guardar metas generales");
        wait.until(d -> d.findElement(By.tagName("main")).getText().contains("120 h") && d.findElement(By.tagName("main")).getText().contains("200 h"));
        click("Usuarios"); wait.until(d -> !d.findElements(By.cssSelector("select[name=role] option")).isEmpty());
        assertTrue(browser.findElements(By.cssSelector("select[name=role] option")).stream().allMatch(o -> java.util.Set.of("VOLUNTARIO","JEFATURA").contains(o.getAttribute("value"))));
        var start=LocalDateTime.parse((String)((JavascriptExecutor)browser).executeAsyncScript("""
            const done=arguments[arguments.length-1];
            fetch('/api/v1/callouts').then(r=>r.json()).then(rows=>{
              const start=new Date(); start.setUTCFullYear(start.getUTCFullYear()+3); start.setUTCHours(14,0,0,0);
              while(rows.some(c=>Date.parse(c.start_at)<start.getTime()+3*3600000 && Date.parse(c.end_at)>start.getTime())) start.setUTCDate(start.getUTCDate()+1);
              done(new Date(start.getTime()-5*3600000).toISOString().slice(0,16));
            }).catch(e=>done(e.message));
            """));
        String title="Asignación Selenium "+System.nanoTime(); var body=new java.util.LinkedHashMap<String,Object>();
        body.put("title",title); body.put("description","Completar equipo mediante asignación directa");
        body.put("start",start.toString()); body.put("end",start.plusHours(3).toString()); body.put("deadline",start.minusDays(1).toString());
        body.put("location","Arequipa"); body.put("requirements","Uniforme y trabajo en equipo"); body.put("level","BASICO");
        body.put("totalVolunteers",1); body.put("graduateSlots",0); body.put("radioSlots",0); body.put("pilotSlots",0);
        body.put("kind","SERVICIO"); body.put("area","Salud"); body.put("contact","Jefatura"); body.put("code","AS-"+System.nanoTime()); body.put("serviceType","Guardia");
        String id=String.valueOf(((java.util.Map<?,?>)request("/callouts","POST",body).get("data")).get("id"));
        request("/callouts/"+id+"/status","POST",java.util.Map.of("status","ABIERTA"));
        browser.get(System.getProperty("e2e.url")+"/callouts/"+id);
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//summary[text()='Inscribir directamente · jefatura']"))).click();
        wait.until(d -> d.findElements(By.cssSelector("select[name=assignmentUser] option")).size()>1);
        new Select(browser.findElement(By.name("assignmentUser"))).selectByValue("00000000-0000-0000-0000-000000000001");
        var submit=browser.findElement(By.xpath("//button[text()='Confirmar inscripción directa']")); assertFalse(submit.isEnabled());
        var verified=browser.findElement(By.name("assignmentVerified")); ((JavascriptExecutor)browser).executeScript("arguments[0].scrollIntoView({block:'center'})",verified); verified.click();
        click("Confirmar inscripción directa");
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),"Inscripción directa confirmada"));
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),"Cobertura completa"));
        click("Salir"); click("Iniciar sesión"); wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".welcome")));
        click("Mis servicios"); wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),title));
        click("Mi perfil"); wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),"Meta: 120 h"));
        browser.get(System.getProperty("e2e.url")+"/callouts/"+id);
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),"Tu inscripción: ACEPTADA"));
        assertTrue(browser.findElements(By.name("assignmentUser")).isEmpty());
    }
    @Test void chiefPublishesSlotsAndVolunteerIsAcceptedWithoutApproval() {
        var email=wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("input[type=email]")));
        email.clear(); email.sendKeys("jefatura@demo.pe"); click("Iniciar sesión");
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".welcome"))); click("Convocatorias"); click("Nueva convocatoria");
        String code="UI-"+System.nanoTime(), title="Servicio Selenium "+System.nanoTime();
        fill("code",code); fill("title",title); fill("description","Equipo para el servicio comunitario");
        fill("serviceType","Guardia"); fill("location","Arequipa"); fill("contact","Responsable de guardia"); fill("requirements","Trabajo en equipo");
        fill("totalVolunteers","1");
        assertTrue(browser.findElements(By.name("traineeSlots")).isEmpty());
        // Elige un horario realmente libre para poder repetir la prueba sin borrar datos de prueba.
        var start=LocalDateTime.parse((String)((JavascriptExecutor)browser).executeAsyncScript("""
            const done=arguments[arguments.length-1];
            fetch('/api/v1/callouts').then(r=>r.json()).then(rows=>{
              const start=new Date(); start.setUTCFullYear(start.getUTCFullYear()+2); start.setUTCHours(14,0,0,0);
              while(rows.some(c=>Date.parse(c.start_at)<start.getTime()+3*3600000 && Date.parse(c.end_at)>start.getTime()))
                start.setUTCDate(start.getUTCDate()+1);
              done(new Date(start.getTime()-5*3600000).toISOString().slice(0,16));
            }).catch(error=>done('Error: '+error.message));
            """));
        for(var entry:java.util.Map.of("start",start,"end",start.plusHours(3),"deadline",start.minusDays(1)).entrySet())
            ((JavascriptExecutor)browser).executeScript("arguments[0].value=arguments[1]",browser.findElement(By.name(entry.getKey())),entry.getValue().toString());
        click("Crear borrador"); wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("h1"),title));
        wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//summary[text()='Gestionar convocatoria']"))).click(); click("Publicar");
        wait.until(d -> d.findElement(By.tagName("main")).getText().contains("Abierta"));
        assertTrue(browser.findElement(By.tagName("main")).getText().contains("Responsable"));
        assertTrue(browser.findElement(By.tagName("main")).getText().contains("Cada persona cuenta una sola vez"));
        screenshot("puestos-browser.png");
        click("Salir"); click("Iniciar sesión"); wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".welcome")));
        click("Convocatorias"); wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("input[aria-label='Buscar convocatorias']"))).sendKeys(code);
        wait.until(ExpectedConditions.elementToBeClickable(By.linkText(title))).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".requirement input"))).click();
        click("Inscribirme"); wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),"Tu inscripción: ACEPTADA"));
        click("Consultar mi inscripción →");
        wait.until(d -> d.findElement(By.tagName("main")).getText().contains(title) && d.findElement(By.tagName("main")).getText().contains("ACEPTADA"));
        browser.navigate().refresh(); wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),title));
        click("Mi programación");
        var day=start.toLocalDate().toString();
        ((JavascriptExecutor)browser).executeScript("const i=arguments[0]; const setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set; setter.call(i,arguments[1]); i.dispatchEvent(new Event('input',{bubbles:true})); i.dispatchEvent(new Event('change',{bubbles:true}));",browser.findElement(By.cssSelector("input[type=date]")),day);
        wait.until(ExpectedConditions.elementToBeClickable(By.cssSelector(".schedule-link"))); screenshot("programacion-browser.png");
        browser.findElement(By.cssSelector(".schedule-link")).click();
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("h1"),title));
        assertTrue(browser.findElements(By.xpath("//summary[text()='Gestionar convocatoria']")).isEmpty());
    }
}
