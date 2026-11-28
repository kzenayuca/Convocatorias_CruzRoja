# Registro de validación de integración — issue #96

## Actualización verificada el 28 de noviembre de 2026

Commit de código probado: `b5e80b5`.
Se utilizó un checkout limpio independiente, con finales LF, sin modificar ni
reformatear la carpeta original. PostgreSQL 16.4 se creó en Docker con puerto
aleatorio publicado en 127.0.0.1 y se retiró al terminar. No se utilizó la base personal.

| Ejecución local | Resultado |
|---|---|
| Windows JDK 21.0.11, Maven Wrapper, `-Pquality verify` | BUILD SUCCESS; Spotless aprobado y JAR generado |
| Backend Surefire | 200 casos contabilizados: 192 aprobados, 8 omitidos, 0 fallos, 0 errores |
| Selenium BrowserTest | 8 omitidos porque no se configuró su entorno E2E |
| MaterialsUseCasesTest | 7 aprobados con Mockito |
| Frontend Windows Node 24.21.0, `npm test -- --maxWorkers=1` | 74 pruebas aprobadas en 8 archivos, sin errores de workers |
| Python `unittest discover -s tools -p 'test_*.py'` | 42 aprobadas |

JaCoCo XML de la misma suite backend: líneas 2830 cubiertas y 333 no cubiertas
(89,47 %), ramas 913 cubiertas y 351 no cubiertas (72,23 %).
Son métricas **locales de JaCoCo**, no resultados nuevos de SonarQube ni cobertura
frontend. No sumar estas cantidades con los porcentajes de otros reportes.

Jenkins respondió en 8090 y SonarQube en 9000 (versión 25.12.0.117093, UP).
Sus API requieren autenticación (403/401). No se ejecutó ni modificó un job
autenticado ni se generó un nuevo reporte Sonar en esta validación.
La PR conserva este pendiente hasta que el usuario ejecute el job y adjunte evidencias.

Los resultados históricos siguientes explican los fallos previos de entorno;
la ejecución Windows/checkout limpio anterior es la validación local posterior.

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

Este apartado corresponde al primer entorno WSL. Para la actualización actual
se localizaron y usaron las herramientas Windows; los bloqueos de autenticación
Jenkins/Sonar y permisos Projects siguen pendientes.

- `docker version`: integración Docker Desktop/WSL no disponible.
- Java y Scanner CLI no disponibles en el PATH de esta terminal.
- Jenkins y SonarQube: no se ejecutaron aquí.
- Sin reporte Sonar antes/después generado en esta integración.
- GitHub Projects: token sin `read:project`; no se pudo inspeccionar/actualizar el tablero.

No se han cambiado permisos, instalado servicios con privilegios ni relajado
reglas para superar estos bloqueos. La guía Windows indica cómo completar la
ejecución con el usuario del agente Jenkins y conservar sus evidencias reales.
