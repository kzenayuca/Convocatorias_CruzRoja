# 🩺 Convocatorias
**Cruz Roja · Filial Arequipa**

Un espacio simple para consultar servicios y organizar el voluntariado.

**React + TypeScript · Spring Boot · PostgreSQL**

**Versión 2.2.0:** puestos por servicio, inscripción automática y reglas de tardanza actualizadas, con el mismo estilo visual.

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
| Reclutador | `reclutador@demo.pe` |
| Jefatura | `jefatura@demo.pe` |

**Adminer:** motor PostgreSQL, servidor **`db`**; usuario, contraseña y base: **`convocatorias`**. Desde tu equipo, PostgreSQL está en `127.0.0.1:5433`.

## 📌 Qué puedes probar

Registro con aprobación de jefatura, convocatorias con puestos de responsable/socorrista/radioperador/piloto, inscripción automática sin cruces de horario, reemplazos, asistencia compartida y horas mensuales. Materiales como solicitud al almacén, con recepción/devolución y observaciones, sin inventario propio. Hay **8 convocatorias y 10 perfiles demo**; consulta las cuentas adicionales en [DOCS.md](DOCS.md#datos-demo).

> **Prototipo local:** sin QR, recuperación de contraseña ni suspensiones automáticas. Conserva tus datos: no uses `docker compose down -v`. Reinicia el backend para aplicar las ampliaciones del esquema.

Tolerancia: **10 minutos**. Superado ese límite se descuenta el doble del retraso; una justificación aprobada elimina la penalización, no acredita tiempo no trabajado. El plazo ordinario para justificar es **48 horas**.

Las reglas y las pruebas **JUnit + Selenium** están en **[DOCS.md](DOCS.md)**. La interfaz conserva el estilo original; frontend y backend siguen en un solo repositorio.
