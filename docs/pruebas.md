# Pruebas

Las pruebas traducen los escenarios BDD a JUnit 5 con Arrange–Act–Assert. Dominio no usa Spring ni Mockito; aplicación usa Mockito; persistencia usa PostgreSQL Testcontainers. No se usa H2.

Reglas cubiertas actualmente: usuario y permisos, cupos/horarios, estados de inscripción, las cinco condiciones de elegibilidad, stock y préstamos, puntualidad y horas, plazo de justificación, penalización, comunicados y lectura idempotente.

```powershell
cd backend
mvn clean verify
```

- Ejecución: `target/surefire-reports`
- Cobertura: `target/site/jacoco/index.html`
