# Convocatorias

## Responsabilidad

El módulo `callouts` es dueño de su dominio, aplicación, persistencia, API REST, SQL y feature React. Otros módulos solo consumen su paquete `api`.

## Capas

- `domain`: entidades, invariantes y puertos.
- `application`: casos de uso y autorización de negocio.
- `infrastructure`: persistencia con `JdbcTemplate` y eventos.
- `presentation`: endpoints `/api/v1/callouts` y DTOs de entrada.
- `api`: contratos públicos de lectura y eventos.

## Pruebas

Las reglas deben cubrirse con JUnit 5; aplicación con Mockito y repositorios con PostgreSQL Testcontainers. No se usa H2.
