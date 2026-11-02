# Sistema de Convocatorias — Cruz Roja Peruana, Filial Arequipa v2

[![CI](https://github.com/kzenayuca/Convocatorias_CruzRoja/actions/workflows/ci.yml/badge.svg)](https://github.com/kzenayuca/Convocatorias_CruzRoja/actions/workflows/ci.yml)

Aplicación modular para convocatorias, inscripciones, inventario, asistencia, justificaciones y comunicados de voluntarios.

## Inicio rápido

```bash
cp .env.example .env
docker compose up -d --build
# abrir http://localhost:8088
```

Para levantar solo PostgreSQL: `docker compose up -d db`. Para desarrollo del frontend: `npm install && npm --prefix frontend install && npm run dev`.

## Stack

Java 17, Spring Boot 3.3.5, JdbcTemplate, Spring Security, PostgreSQL 16, React, Vite, TypeScript, CSS plano, Docker Compose, JUnit 5, Mockito, Testcontainers, JaCoCo y SonarQube Community.

## Módulos

`users`, `callouts`, `inventory`, `enrollments`, `attendance`, `justifications` y `notifications`.

## Pruebas y calidad

Docker debe estar activo para Testcontainers.

```bash
cd backend && ./mvnw verify
cd ../frontend && npm run lint && npm run check:contrast && npm run build
```

Por decisión del propietario, esta entrega no incluye pruebas automatizadas frontend. Los reportes backend se generan en `backend/target/surefire-reports` y `backend/target/site/jacoco`.

Consulte [`docs/README.md`](docs/README.md) para arquitectura, API, UX, CI y migración.
