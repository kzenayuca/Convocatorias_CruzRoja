# Arquitectura

Monolito modular con DDD simple:

```text
HTTP (presentation)
        ↓
Casos de uso (application)
        ↓
Reglas y puertos (domain)
        ↑
PostgreSQL y eventos (infrastructure)
```

Módulos: `users ← callouts ← enrollments ← attendance ← justifications`; `inventory` usa el contrato público de `callouts`; `notifications` escucha eventos. Cada módulo solo comparte records e interfaces de su paquete `api` y es dueño de sus tablas.

Principios: reglas dentro del dominio, autorización en casos de uso, DTO de entrada validado, `JdbcTemplate` explícito, sesión con cookie y errores JSON en español.
