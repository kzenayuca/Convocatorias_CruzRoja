# Registro de validación de integración — issue #96

## Procedencia

Rama: `refactor/lizeth-practica07-entrega`.
Base: `3e5ad43` (Jenkins) y recuperación de `origin/refactor/lizzz7-75-attendance`.
Consultar el commit de integración en Git para el SHA final.
Entorno: WSL, repositorio sobre NTFS, Node 22.16.0 y npm de esa distribución.

## Resultados observados

| Comando | Resultado observado |
|---|---|
| `npm ci --no-audit --no-fund` en frontend | Instalación correcta |
| `npm run build`, antes de conciliar contratos | Falló TS18046 en respuesta de creación de convocatoria |
| `npm test`, primer intento paralelo | 42 aprobadas, 1 fallida y 4 errores de arranque de workers; ejecución no aprobada |
| `npm run build`, después de conciliar | Correcto: TypeScript y bundle Vite |
| `python3 -m unittest discover -s tools -p 'test_*.py'` | 42 pruebas aprobadas |

La respuesta de creación se tipó con `CreatedResource`, manteniendo la API
`unknown` por defecto. La prueba fallida de foto esperaba una alerta y el componente
recuperado mostraba un estado. Se integró `useRequest` y `ErrorNotice` en ese
formulario. Se conservó la nueva composición de DetailPage y el guard de carga de metas.
La suite se vuelve a ejecutar con `--maxWorkers=1`; el resultado final debe
consultarse en el comentario de validación de #96/PR y en CI del commit final.

Este fallo/corrección pertenece a integración; no prueba que los refactorings
anteriores se desarrollaran originalmente mediante TDD.

## Bloqueos que no deben presentarse como aprobados

- `docker version`: integración Docker Desktop/WSL no disponible.
- Java y Scanner CLI no disponibles en el PATH de esta terminal.
- Jenkins y SonarQube: no se ejecutaron aquí.
- Sin reporte Sonar antes/después generado en esta integración.
- GitHub Projects: token sin `read:project`; no se pudo inspeccionar/actualizar el tablero.

No se han cambiado permisos, instalado servicios con privilegios ni relajado
reglas para superar estos bloqueos. La guía Windows indica cómo completar la
ejecución con el usuario del agente Jenkins y conservar sus evidencias reales.
