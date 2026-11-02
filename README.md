# Sistema de Convocatorias — Cruz Roja Arequipa

Aplicación web académica para gestionar voluntarios, convocatorias, inscripciones, inventario, asistencia, justificaciones y comunicados. Es un monolito modular con reglas de negocio DDD y escenarios BDD documentados.

## Modelo mental

| Pregunta | Módulo |
|---|---|
| ¿Quién participa? | `users` |
| ¿En qué servicio? | `callouts` |
| ¿Quién se inscribió? | `enrollments` |
| ¿Qué materiales se usan? | `inventory` |
| ¿Quién asistió y cuántas horas hizo? | `attendance` |
| ¿La ausencia fue justificada? | `justifications` |
| ¿Qué debe comunicarse? | `notifications` |

## Ejecutar todo

Requiere Docker Desktop iniciado.

```powershell
Copy-Item .env.example .env -Force
docker compose down -v
docker compose up -d --build
```

> `down -v` solo es necesario la primera vez o cuando cambió la contraseña local de PostgreSQL.

| Servicio | Dirección |
|---|---|
| Aplicación | http://localhost:8088 |
| Adminer | http://localhost:8081 |
| PostgreSQL | `localhost:5432` |
| Salud API | http://localhost:8088/api/v1/health |

Adminer: sistema `PostgreSQL`, servidor `db`, usuario/contraseña/base `cruzroja` / `cruzroja` / `convocatorias`.

## Desarrollo

Base de datos:

```powershell
docker compose up -d db adminer
```

Backend, en otra terminal:

```powershell
cd backend
$env:DB_USER="cruzroja"
$env:DB_PASSWORD="cruzroja"
mvn spring-boot:run
```

Frontend, en otra terminal:

```powershell
cd frontend
npm install
npm run dev
```

Frontend: http://localhost:5173 · API: http://localhost:8080/api/v1/health

## Pruebas

```powershell
cd backend
mvn clean verify
```

Los reportes quedan en `backend/target/surefire-reports` y `backend/target/site/jacoco`. No se incluyen pruebas frontend por decisión del equipo; sí se validan lint, contraste y build:

```powershell
cd frontend
npm run lint
npm run check:contrast
npm run build
```

## Documentación esencial

- [Requisitos y escenarios BDD](docs/requisitos-bdd.md)
- [Arquitectura](docs/architecture.md)
- [API](docs/api.md)
- [Pruebas](docs/pruebas.md)
- [Demostración](docs/demo.md)
