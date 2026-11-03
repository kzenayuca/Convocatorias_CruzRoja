# 🩺 Convocatorias
**Cruz Roja · Filial Arequipa**

Un espacio simple para consultar servicios y organizar el voluntariado.

**React + TypeScript · Spring Boot · PostgreSQL**

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

Sesiones reales, búsqueda con sugerencias y **8 convocatorias demo** con distintos estados y cupos. Hay **10 perfiles**; consulta las cuentas adicionales en [DOCS.md](DOCS.md#datos-demo).

> **En desarrollo:** las nuevas inscripciones se guardan solo en el navegador. Las horas, avisos y reportes siguen siendo demostrativos.

Las reglas acordadas, los pendientes y las notas de ejecución están en **[DOCS.md](DOCS.md)**. CI básico, sin pruebas ni build por ahora.
