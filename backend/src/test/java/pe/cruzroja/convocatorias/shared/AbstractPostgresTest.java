package pe.cruzroja.convocatorias.shared;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
@Testcontainers
public abstract class AbstractPostgresTest {
 @Container @ServiceConnection protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.4-alpine").withDatabaseName("test").withUsername("test").withPassword("test").withReuse(true);
}
