# Decisiones

- `JdbcTemplate` mantiene SQL explícito y evita JPA.
- SQL por módulo evita Flyway para simplificación académica.
- Testcontainers prueba PostgreSQL real; H2 está prohibido.
- Spring Security autentica; los servicios aplican permisos del negocio.
- CSRF está desactivado como simplificación académica para la SPA de sesión.
- Sonar efímero hace reproducible CI; se admite servidor persistente con secretos.
- Soft UI incluye bordes, contraste, foco e icono+texto para mitigar sus riesgos.
