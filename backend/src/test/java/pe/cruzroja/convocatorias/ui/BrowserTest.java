package pe.cruzroja.convocatorias.ui;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.time.Duration;
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
    void click(String text) { wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//*[self::button or self::a][text()[normalize-space(.)='"+text+"']]"))).click(); }
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
        click("Mi perfil"); wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),"Teléfono · cambio directo"));
        var phone=browser.findElement(By.name("phone")); phone.clear(); phone.sendKeys("999888777");
        browser.findElement(By.cssSelector("form button[type=submit], form button.button")).click();
        wait.until(ExpectedConditions.textToBePresentInElementLocated(By.tagName("main"),"Guardado correctamente"));
        browser.navigate().refresh(); wait.until(ExpectedConditions.attributeToBe(By.name("phone"),"value","999888777"));
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
}
