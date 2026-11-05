# 🩺 Convocatorias
**Cruz Roja · Filial Arequipa**

Un espacio simple para consultar servicios y organizar el voluntariado.

**React + TypeScript · Spring Boot · PostgreSQL**

**Versión 2.4.0:** metas de horas para formación y graduados, inscripción directa por jefatura y responsable como función por servicio, conservando programación, PDF y el estilo visual.

## 🚀 Arranque local

Requiere Docker Desktop iniciado, Java 17+ con Maven y Node 22.12+.

**1. Base de datos** — desde la raíz:
```powershell
docker compose up -d
```

**2. Backend** — en otra terminal, desde la raíz:
```powershell
cd backend
mvn spring-boot:run
```

**3. Frontend** — en otra terminal, desde la raíz:
```powershell
cd frontend
npm run dev
```
La primera vez, ejecuta `npm install` dentro de `frontend` antes de iniciarlo.

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

Jefatura puede **inscribir directamente** a personas elegibles desde el detalle de una convocatoria publicada: exige verificación de requisitos y no permite saltarse cupos, nivel, habilitaciones, plazo ni cruces. La persona recibe una notificación y la inscripción aparece en su programación. Jefatura crea las convocatorias; un **responsable** es un voluntario graduado asignado a un servicio, no un rol global. El antiguo rol RECLUTADOR se migra a VOLUNTARIO conservando cuentas y asignaciones.

Solicitudes de almacén, programación y reportes tienen vista digital y descarga **PDF real, sin impresión**. Fotos opcionales JPG/PNG hasta 50 MB, subidas por jefatura o el responsable asignado y visibles para cuentas activas. Administración configura dos **metas generales de horas acumuladas**, para formación y graduados, sin alterar saldos ni graduar automáticamente; se muestran en perfil y Mis servicios. No son metas mensuales ni individuales. Hay **8 convocatorias y 10 perfiles demo**; consulta [DOCS.md](DOCS.md#datos-demo).

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
mvn test
```

Cobertura: `backend/target/site/jacoco/index.html`. Ejecución JUnit: `backend/target/surefire-reports/`.
