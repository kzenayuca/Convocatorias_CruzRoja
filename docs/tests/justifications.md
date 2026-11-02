# Casos de prueba — Justificaciones y penalizaciones

| Clase/Método | Caso de prueba | Valores de prueba | Resultado esperado |
|---|---|---|---|
| Dominio/constructor | Datos obligatorios inválidos | nulo o vacío | Lanza `IllegalArgumentException` |
| Dominio/transición | Estado no permitido | estado final | Rechaza la transición |
| Aplicación/caso de uso | Puerto responde correctamente | UUID válido | Devuelve DTO esperado |
| Repositorio/guardar | Persistencia PostgreSQL | entidad válida | Recupera los mismos datos |
