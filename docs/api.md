# API REST

| Método | Ruta | Rol | Descripción |
|---|---|---|---|
| GET | `/api/v1/health` | Público | Salud del backend |
| POST | `/api/v1/auth/login` | Público | Iniciar sesión |
| POST | `/api/v1/auth/logout` | Autenticado | Cerrar sesión |
| GET | `/api/v1/auth/me` | Autenticado | Sesión actual |
| GET | `/api/v1/callouts` | Todos | Convocatorias |
| GET | `/api/v1/enrollments` | Todos | Inscripciones |
| GET | `/api/v1/inventory` | Reclutador/Jefatura | Materiales |
| GET | `/api/v1/attendance/hours` | Todos | Horas calculadas |
| GET | `/api/v1/justifications` | Todos | Justificaciones |
| GET | `/api/v1/notifications` | Todos | Notificaciones |
