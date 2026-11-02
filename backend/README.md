# Backend

Cada módulo contiene `api`, `domain`, `application`, `infrastructure` y `presentation`. El dominio no depende de Spring; aplicación depende de puertos; infraestructura implementa persistencia con `JdbcTemplate`.

```bash
./mvnw spring-boot:run
./mvnw verify
```

Variables: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `SPRING_PROFILES_ACTIVE=demo`. Los esquemas se cargan desde `classpath*:db/*.sql`; datos demo desde `db/data/*.sql`. `AbstractPostgresTest` mantiene el contenedor PostgreSQL compartido. Para agregar un módulo, cree las cinco capas, SQL propio, pruebas y contrato `api`; no consulte tablas ajenas.
