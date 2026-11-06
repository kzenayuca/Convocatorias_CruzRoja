# Arquitectura: Web + DDD + Clean Architecture

## Estilo y alcance

La aplicación sigue siendo un **monolito modular**: frontend React/TypeScript, backend Spring Boot y una base PostgreSQL. El backend se publica como un único JAR; no se introducen microservicios, un bus distribuido, event sourcing ni CQRS con bases separadas.

Se aplica **DDD táctico y separación de contextos**, con un núcleo Java independiente de frameworks y adaptadores externos siguiendo Clean Architecture. No basta con poner nombres a carpetas: hay modelos de negocio ejecutados por los casos de uso, puertos de salida, implementaciones JDBC y pruebas que impiden invertir las dependencias.

## Contextos y propiedad

| Módulo | Responsabilidad y datos propios |
|---|---|
| `membership` · Voluntarios | Cuentas, perfil, estado, graduación y habilitaciones. Agregado `Volunteer`. |
| `operations` · Operaciones | Convocatorias, inscripciones, responsable, programación, asistencia, justificaciones, cierres y solicitudes de materiales. Agregados/modelos `Callout`, `Attendance`, `Justification`, `MaterialRequest`; valores `TeamCapacity`, `ServiceWindow`, `MonthlyBalance`. |
| `administration` · Administración | Política institucional de metas por condición. Valor `HourTargets`; no hay meta administrativa para jefatura. |
| `communications` · Comunicaciones | Notificaciones persistentes, lectura y comunicados oficiales. Modelo `Announcement`. |
| `shared` | Contratos comunes, errores de aplicación, identidad de ejecución, notificación y utilidades de proyecciones. No contiene reglas específicas de un contexto. |
| `bootstrap` | Composition root: construye casos de uso, configura reloj e introduce los límites transaccionales de Spring. |

Asistencia, cierres y materiales pertenecen al contexto Operaciones porque colaboran en el mismo servicio y requieren consistencia con sus inscripciones. No son microservicios separados por tabla.

### Integración entre contextos

- Voluntarios usa el contrato `ServiceParticipation`, no una implementación de Operaciones. Cuando jefatura cambia una cuenta se bloquean sus compromisos, se guarda la cuenta y se reevalúan las inscripciones futuras dentro de la misma transacción.
- Los casos de uso envían avisos por `NotificationPublisher`; no conocen la tabla ni la implementación de Comunicaciones.
- Operaciones consume `GraduationSettings` para consultar la política de metas mediante un adaptador JDBC.
- Para elegibilidad, el adaptador construye un `Participant` con los datos de identidad requeridos por Operaciones. No se importa el agregado ni la infraestructura de Voluntarios.
- La base sigue compartida. Hay consultas de integración y claves foráneas en adaptadores; no se afirma aislamiento físico de bases. Los módulos de negocio no dependen de clases internas de otros módulos.

## Capas y dirección de dependencias

```text
frontend React ── HTTP/JSON ──► web ──► application ──► domain
                                             ▲             ▲
                                             └── infrastructure
                                                   (implementa puertos)

bootstrap construye y conecta los componentes externos e internos
```

Cada contexto usa `domain/`, `application/`, `infrastructure/` y `web/` cuando corresponde:

- **Dominio:** Java puro. Invariantes y decisiones de negocio. Sin Spring, Jakarta, HTTP, SQL, JDBC, PDFBox ni acceso a la sesión.
- **Aplicación:** comandos, casos de uso y puertos. Coordina permisos, carga/persistencia y notificaciones. Sin anotaciones de framework, transacciones de Spring ni consultas SQL.
- **Infraestructura:** implementa repositorios, notificaciones, contraseña BCrypt, reloj/identidad de seguridad y decodificación de imágenes. Solo aquí se usa JDBC. Las fechas JDBC se convierten a tipos Java antes de entrar al núcleo.
- **Web:** mapea las rutas existentes a casos de uso y sus salidas a HTTP. El contenido de un PDF se representa en el núcleo con `Document`; PDFBox y `ResponseEntity` quedan en el adaptador web compartido. Archivos multipart no atraviesan el núcleo.
- **Seguridad HTTP:** el adaptador de sesiones/CSRF de Spring vive en `membership/infrastructure/security`, incluyendo el controlador del protocolo de autenticación. No se mezcla con los casos de uso de cuentas ni con las reglas del dominio.

La validación de comandos está en constructores Java: también aplica si se ejecuta un caso de uso sin HTTP. La validación de reglas del negocio está en los modelos de dominio. El adaptador web traduce errores de entrada, autorización, inexistencia y conflicto a sus códigos HTTP.

## Ejemplos de reglas en el dominio

- `Callout` protege el total inclusivo, mínimos, funciones reservadas, aceptación/plazo de inscripción, operación y cambio explícito del responsable.
- `Participant` decide elegibilidad por condición/habilitaciones, sin niveles y sin convertir jefatura en un voluntario inscribible.
- `Attendance` calcula crédito y deducciones, y valida confirmaciones y ajustes excepcionales.
- `Justification.resolve` produce una decisión inmutable con autor, fecha, motivo y efecto firmado sobre la deducción. El caso de uso la persiste y, si el mes está cerrado, añade el ajuste asociado.
- `MonthlyBalance` calcula saldo vigente conservando el cierre original.
- `MaterialRequest` impide repetir recepción/devolución o superar las cantidades recibidas.
- `Volunteer` protege los roles admitidos y el acceso propio de jefatura.

Las decisiones auditadas no constituyen event sourcing: el estado actual sigue guardado en las tablas existentes y el historial es anexo.

## Consistencia y transacciones

`UseCaseConfiguration` decora las operaciones de escritura con transacciones REQUIRED mediante proxies de Spring. El núcleo no importa ni necesita `@Transactional`. La validación de permisos continúa ejecutándose en los casos de uso.

Los repositorios mantienen los bloqueos `FOR UPDATE` y el orden de adquisición: convocatoria antes de perfil/inscripción; al cerrar un mes se bloquean sus convocatorias ordenadas. Así, inscripción directa y autoinscripción compiten por el mismo cupo y los fallos revierten cambios parciales. Los avisos persistentes participan en la transacción local; no hay entrega externa asíncrona.

## Proyecciones y compatibilidad

La API mantiene las rutas, nombres JSON y tipos de respuesta usados por React. Los listados, reportes y vistas conservan **proyecciones de lectura** `Map<String,Object>` para no cambiar el contrato existente. No son entidades ni agregados de dominio y no contienen SQL. Los agregados y valores del dominio son tipados.

El adaptador JSON expone los `LocalDateTime` con offset de Perú (`-05:00`), igual que los timestamps anteriores, para que la web no cambie horas según la zona del navegador. Esta conversión y su prueba pertenecen al adaptador, no al dominio.

Los comandos son objetos de entrada del núcleo; los adaptadores pueden deserializarlos sin acoplarlos a anotaciones JSON o de validación. La refactorización no necesita alterar tablas, IDs, contraseñas, inscripciones, fotos, cierres ni historial.

## Comprobaciones automáticas

Desde `backend`:

```bash
# Necesario una vez al actualizar: elimina clases compiladas de los paquetes anteriores.
mvn clean test

# Solo arquitectura, dominio y casos de uso sin PostgreSQL
mvn -Dtest=ArchitectureTest,AggregatesTest,UseCasesTest,TransactionBoundaryTest test
```

- `ArchitectureTest` (ArchUnit): dominio independiente, aplicación con dependencias hacia dentro, JDBC solo en infraestructura, controladores sin JDBC, módulos sin ciclos y shared kernel sin dependencias de negocio.
- Dominio y aplicación también se pueden compilar directamente con `javac --release 17` sin añadir librerías al classpath; no necesitan Spring para compilar.
- `AggregatesTest` y pruebas de reglas: invariantes ejecutadas sin Spring.
- `UseCasesTest`: puertos falsos/Mockito, sin servidor ni PostgreSQL.
- `TransactionBoundaryTest`: el decorador de infraestructura revierte una escritura fallida.
- `WorkflowTest`: regresión HTTP contra una base exclusiva indicada por `TEST_DB_URL`; verifica permisos, cupos concurrentes, bloqueos, cierres, historial y rollback real.
- `BrowserTest`: regresión web Selenium existente. Continúa en este repositorio; **Lab 6 no forma parte de esta migración**.

Los comandos y la configuración de pruebas completas están en [DOCS.md](DOCS.md#ejecución-pruebas-y-ci). No ejecutar pruebas de integración o navegador sobre datos reales.

## Evolución permitida

Agregar una funcionalidad implica definir primero sus reglas de dominio, su caso de uso y los puertos necesarios; después los adaptadores. No se debe poner SQL en controladores ni importar implementaciones JDBC desde el núcleo. No es obligatorio crear un agregado, evento o repositorio por cada tabla: los límites se eligen por reglas y consistencia de negocio.
