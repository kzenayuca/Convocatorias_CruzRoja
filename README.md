# Convocatorias · Cruz Roja Filial Arequipa

Aplicación web para organizar convocatorias, inscripciones, equipos y resultados del voluntariado.

**React + TypeScript · Spring Boot · Java 17 · PostgreSQL · JPA/Hibernate**

## Funcionalidades

- Solicitud y activación de cuentas; perfil y revisión de cambios.
- Convocatorias con código automático, requisitos, horarios y composición del equipo.
- Inscripción automática y asignación directa con control de elegibilidad, cupos y cruces.
- Responsable por servicio, programación y operación.
- Asistencia, horas, revisiones, justificaciones y reemplazos.
- Reportes mensuales/anuales y metas de graduación.
- Solicitudes de materiales, recepción y devolución.
- Comunicados y notificaciones personales.

## Entender el sistema

1. [Requisitos, recorrido y restricciones](docs/REQUISITOS.md).
2. [Arquitectura, tablas y modelo StarUML](docs/ARQUITECTURA.md).
3. [Desarrollo, migraciones y pruebas](docs/DESARROLLO.md).
4. [Convenciones, estilos y Clean Code](docs/CALIDAD.md).

## Ejecutar localmente

Requisitos: Java 17+, Node 22.12+ y PostgreSQL. Docker Desktop es una opción para la base.
Cada componente se inicia en una terminal independiente.

```powershell
# 1. Desde la raíz
docker compose up -d

# 2. Backend (otra terminal)
cd backend
.\mvnw.cmd spring-boot:run

# 3. Frontend (otra terminal)
cd frontend
npm ci
npm run dev
```

En Bash utilizar `./mvnw`. **Si la base ya existía antes de Flyway, hacer copia y seguir
[la adopción explícita](docs/DESARROLLO.md#base-existente-antes-del-primer-arranque).** No borrar volúmenes.

| Componente | Dirección habitual |
|---|---|
| Frontend | http://localhost:5173 |
| Backend / salud | http://localhost:8080/api/v1/health |
| PostgreSQL | 127.0.0.1:5433 |
| Adminer | http://localhost:8081 |

## Probar con datos demo

Contraseña demo: `convocatorias`.

| Perfil | Correo |
|---|---|
| Voluntario | `voluntario@demo.pe` |
| Graduada | `maria@demo.pe` |
| Jefatura | `jefatura@demo.pe` |

Recorrido sugerido: Jefatura crea y publica; voluntario revisa condiciones y se inscribe;
Jefatura consulta cobertura y equipo. El total incluye todas las funciones; **Completa** no significa
**Finalizada**. Los graduados necesitan ciclo anual para el año del servicio.

Es un prototipo local. Antes de utilizar datos reales se necesitan HTTPS, copias/restauración,
consentimiento y retención de datos, revisión de seguridad y pruebas de capacidad.
