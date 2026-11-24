package pe.cruzroja.convocatorias.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import pe.cruzroja.convocatorias.ConvocatoriasApplication;

@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class DemoProfileTest {
    private static final UUID CHIEF = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @ParameterizedTest
    @ValueSource(strings = {"default", "production", "local-demo"})
    void demoAccountsAreCreatedOnlyInTheLocalProfile(String profile) {
        String testDbUrl = System.getenv("TEST_DB_URL");
        String user = System.getenv().getOrDefault("TEST_DB_USER", "convocatorias");
        String password = System.getenv().getOrDefault("TEST_DB_PASSWORD", "convocatorias");
        String schema = "demo_profile_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin =
                new JdbcTemplate(new DriverManagerDataSource(testDbUrl, user, password));
        String schemaUrl =
                testDbUrl + (testDbUrl.contains("?") ? "&" : "?") + "currentSchema=" + schema;
        List<String> arguments =
                List.of(
                        "--spring.datasource.url=" + schemaUrl,
                        "--spring.datasource.username=" + user,
                        "--spring.datasource.password=" + password,
                        "--spring.flyway.default-schema=" + schema,
                        "--spring.profiles.default=default",
                        "--spring.profiles.active=" + (profile.equals("default") ? "" : profile),
                        "--server.port=0");
        try {
            try (var context = start(arguments)) {
                JdbcTemplate db = context.getBean(JdbcTemplate.class);
                if (profile.equals("local-demo")) {
                    assertEquals(10L, demoAccounts(db));
                    return;
                }
                assertEquals(0L, demoAccounts(db));
                db.update(
                        """
                        INSERT INTO users(id,name,email,password_hash,role,status)
                        VALUES (?, 'Jefatura existente', 'jefatura@demo.pe', 'existing-hash', 'JEFATURA', 'ACTIVO')
                        """,
                        CHIEF);
            }
            try (var context = start(arguments)) {
                JdbcTemplate db = context.getBean(JdbcTemplate.class);
                assertEquals(1L, demoAccounts(db));
                assertEquals(
                        "existing-hash",
                        db.queryForObject(
                                "SELECT password_hash FROM users WHERE id=?", String.class, CHIEF));
            }
        } finally {
            admin.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private ConfigurableApplicationContext start(List<String> arguments) {
        return SpringApplication.run(
                ConvocatoriasApplication.class, arguments.toArray(String[]::new));
    }

    private long demoAccounts(JdbcTemplate db) {
        return db.queryForObject(
                "SELECT count(*) FROM users WHERE email LIKE '%@demo.pe'", Long.class);
    }
}
