# 🩺 Convocatorias
**Cruz Roja · Filial Arequipa**

Un espacio simple para consultar servicios y organizar el voluntariado.

**React + TypeScript · Spring Boot · PostgreSQL**

**Versión 2.6.0:** aplicación web con backend de **monolito modular + DDD + Clean Architecture**, conservando las reglas, pantallas, API y datos de la versión anterior. [Ver arquitectura y contextos](ARCHITECTURE.md).

## 🚀 Arranque local

Requiere Docker Desktop iniciado, Java 17+ con Maven y Node 22.12+.

**1. Base de datos** — desde la raíz:
```powershell
docker compose up -d
```

**2. Backend** — en otra terminal, desde la raíz:
```powershell
cd backend
mvn clean test
mvn spring-boot:run
```

**3. Frontend** — en otra terminal, desde la raíz:
```powershell
cd frontend
npm run dev
```
La primera vez, ejecuta `npm ci` dentro de `frontend` antes de iniciarlo. El `clean` inicial del backend elimina clases compiladas de paquetes anteriores, no datos de PostgreSQL.

| Servicio | Dirección |
|---|---|
| Aplicación | http://localhost:5173 — o el puerto que indique Vite |
| Salud de la API | http://localhost:8080/api/v1/health |
| Adminer | http://localhost:8081 |

## 🔑 Acceso demo

Contraseña para todas las cuentas: **`convocatorias`**.

| Rol | Correo |
|---|---|
| Voluntario | `voluntario@demo.pe` |
| Voluntario graduado (correo histórico) | `reclutador@demo.pe` |
| Jefatura | `jefatura@demo.pe` |

**Adminer:** motor PostgreSQL, servidor **`db`**; usuario, contraseña y base: **`convocatorias`**. Desde tu equipo, PostgreSQL está en `127.0.0.1:5433`.

## 📌 Qué puedes probar

Registro con aprobación de jefatura; perfil con botón **Modificar**; inscripción automática y **un total de voluntarios que incluye responsable, piloto y radioperador**. Voluntario admite graduados y personas en formación; el mínimo de graduados se exige dentro del total, no se suma. Responsable opcional asignado por jefatura. **Mi programación** solo muestra participaciones aceptadas; jefatura tiene la programación general. Cada servicio abre su página de detalle.

Jefatura puede **buscar por nombre e inscribir directamente** a personas elegibles desde una convocatoria publicada: verifica conocimientos, cursos y demás requisitos propios del servicio; el sistema valida graduación, habilitaciones, cupos, plazo y cruces. **No hay niveles visibles ni restricciones por nivel.** La persona recibe una notificación y la inscripción aparece en su programación.

El **responsable** es un voluntario graduado activo ya inscrito y confirmado como voluntario general. Solo en su convocatoria tiene permisos para gestionar, subir fotos y confirmar asistencias. Cambiarlo o retirarlo requiere una acción explícita y auditada; conserva la inscripción, pero pierde los permisos. El contacto se actualiza al teléfono del responsable, visible solo a jefatura y participantes confirmados; de otro modo dice “Consultar a jefatura”. El antiguo rol RECLUTADOR sigue migrándose a VOLUNTARIO conservando cuentas e historial.

**Solo jefatura descarga el PDF de programación.** Se conservan vista digital y PDF real de reportes y solicitudes de almacén según sus permisos. Fotos opcionales JPG/PNG hasta **10 MB**, botón **Adjuntar foto**, ofrecido al cerrar o después; solo jefatura o el responsable confirmado pueden subirlas. Administración configura dos metas generales de horas acumuladas para voluntarios en formación y graduados; **jefatura no tiene meta ni penalizaciones ni se justifica**. Hay **8 convocatorias y 10 perfiles demo**; consulta [DOCS.md](DOCS.md#datos-demo).

La bandeja de justificaciones de jefatura permite consultar todos los usuarios y estados, filtrar, aprobar o rechazar con motivo y revisar el historial. Cada decisión registra autor, fecha y efecto en la penalización. Se pueden resolver después del cierre mensual: se añaden ajustes separados sin sobrescribir el cierre original ni otorgar horas no trabajadas.

> **Prototipo local:** sin QR, recuperación de contraseña ni suspensiones automáticas. Conserva tus datos: no uses `docker compose down -v`. Reinicia el backend para aplicar las ampliaciones del esquema.

Tolerancia: **10 minutos**. Superado ese límite se descuenta el doble del retraso; una justificación aprobada elimina la penalización, no acredita tiempo no trabajado. El plazo ordinario para justificar es **48 horas**.

Las reglas y las pruebas **JUnit + Selenium** están en **[DOCS.md](DOCS.md)**. JUnit, Mockito y JaCoCo preparan la base técnica para el Lab 5. **El Lab 6 y su separación multirepo quedan para después**; no se han creado repositorios ni tareas en GitHub.

## Comprobar esta versión

```powershell
# Frontend
cd frontend
npm ci
npm run build
# Backend (pruebas unitarias; para integración ver DOCS.md)
cd ../backend
mvn clean test
```

Cobertura: `backend/target/site/jacoco/index.html`. Ejecución JUnit: `backend/target/surefire-reports/`.
