# Demostración

1. `docker compose up -d --build`.
2. Abrir `http://localhost:8088` y revisar Inicio.
3. Navegar por Convocatorias, Inscripciones, Inventario, Asistencia, Justificaciones y Avisos.
4. Abrir Adminer en `http://localhost:8081` y revisar las tablas PostgreSQL.
5. Verificar `http://localhost:8088/api/v1/health`.
6. Mostrar pruebas con `cd backend; mvn clean verify`.
7. Mostrar contraste y compilación con `cd frontend; npm run check:contrast; npm run build`.
