# Convocatorias · Cruz Roja Arequipa

React + Spring Boot + PostgreSQL. Requiere Docker Desktop, Java 17+ con Maven y Node 22.12+.

## Iniciar

Desde la raíz:

```powershell
docker compose up -d
```

En otra terminal:

```powershell
cd backend
mvn spring-boot:run
```

En otra terminal:

```powershell
cd frontend
npm install # Solo la primera vez o cuando cambien las dependencias.
npm run dev
```

Web: http://localhost:5173 (o el puerto que indique Vite si está ocupado) · API: http://localhost:8080/api/v1/health · Adminer: http://localhost:8081.

## Credenciales locales

- PostgreSQL: base, usuario y contraseña **`convocatorias`**. Conexión local: `127.0.0.1:5433`, para no interferir con PostgreSQL de Windows. En Adminer, servidor `db` (puerto interno `5432`).
- Login: `voluntario@demo.pe`, `reclutador@demo.pe` o `jefatura@demo.pe`; contraseña **`convocatorias`**.
- No hace falta `.env`; los valores por defecto están listos para desarrollo. Estas credenciales no son para producción.
- Si hay un volumen PostgreSQL antiguo, cambiar `.env` no cambia sus cuentas. Hay que migrar las credenciales sin borrar datos; no uses `down -v` si necesitas conservarlos.
- Si PowerShell tiene `DB_USER`, `DB_PASSWORD` o `DB_URL` antiguos, corrígelos o elimínalos de esa terminal antes de ejecutar Maven. Spring no carga el `.env` de Compose.

Opcional: toda la aplicación en Docker con `docker compose --profile app up -d --build`, en http://localhost:8088.

## Alcance actual

El login, la cookie de sesión y el cierre de sesión son reales. Las demás consultas y estadísticas siguen siendo demostrativas; las inscripciones de la interfaz se guardan en el navegador. Esta limpieza no completa su integración con PostgreSQL.

Solo se mantiene `npm run dev`. Sin lint, contraste, Sonar ni cobertura automática. Quedan dos comprobaciones pequeñas de reglas, sin ejecutarse al arrancar. Requisitos acordados y pendientes: [REQUISITOS.md](REQUISITOS.md).

GitHub Actions solo valida configuración Maven/Compose e instalación del frontend. No compila ni ejecuta pruebas todavía. Guarda y sube los cambios de configuración junto con los de la aplicación; crea un tag nuevo únicamente después de que ese commit pase el CI. Reejecutar un workflow antiguo no incorpora estas correcciones.
