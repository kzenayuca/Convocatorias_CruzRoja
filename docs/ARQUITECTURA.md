# Arquitectura y persistencia

## Separación general

```text
frontend/                 React y TypeScript; navegación y formularios
backend/                  API Spring MVC, aplicación, dominio y persistencia
docs/                     Requisitos, arquitectura, desarrollo y calidad
.github/workflows/        Integración continua
compose.yaml              PostgreSQL y Adminer locales
```

Backend: monolito modular, no microservicios. Un JAR y una base PostgreSQL compartida.
DDD no exige una base por contexto ni un agregado por tabla.

| Contexto | Paquete | Responsabilidad |
|---|---|---|
| Membresía | membership | Cuentas, perfil, condición, habilitaciones y ciclo anual |
| Operaciones | operations | Convocatoria, equipo, asistencia, incidencias, horas y materiales |
| Administración | administration | Metas institucionales |
| Comunicaciones | communications | Comunicados y avisos personales |

`shared` contiene contratos comunes; `bootstrap` conecta componentes y transacciones. No son áreas del negocio.

## Capas y dependencias

```text
React --HTTP/JSON--> web --> application --> domain
                         infraestructura implementa contratos
                         bootstrap conecta y decora transacciones
```

- `web`: rutas, respuestas y adaptación de entradas; sin consultas a repositorios.
- `application`: casos de uso, autorización, coordinación y notificaciones.
- `domain`: conceptos e invariantes, sin Spring, Hibernate, SQL ni sesión.
- `infrastructure`: entidades JPA, adaptadores, seguridad y servicios técnicos.

Las interfaces de agregados se trasladan al dominio cuando sus contratos no dependen de comandos
de aplicación. Algunos puertos heredados siguen en aplicación y todavía usan proyecciones de mapas.
No se duplican interfaces vacías para simular independencia.

## Modelo rico

| Modelo | Qué protege |
|---|---|
| Callout / TeamCapacity | Total inclusivo, reservas, admisión y operación |
| Enrollment | Identidad del compromiso, reintento y transiciones de función/cancelación |
| ServiceWindow | Planificación y cruces temporales |
| Attendance / ServiceRules | Reconocimiento y deducciones |
| Justification / MonthlyBalance | Decisiones y ajustes sin sobrescribir el cierre |
| MaterialRequest | Recepción/devolución compatibles y no repetidas |
| Notification | Propiedad y conservación de la primera lectura |
| Volunteer | Roles, estados y protección del acceso propio de Jefatura |

Las decisiones que necesitan datos externos se coordinan en aplicación. La capacidad se comprueba
bajo bloqueo de convocatoria; una inscripción no se considera un agregado independiente solo porque tenga tabla.

## Leer el modelo StarUML

El archivo `.mdj` se genera localmente y está excluido de Git. Desde la raíz:

```bash
python3 tools/build_model.py
```

Abrir `backend/target/model/Convocatorias.mdj` en StarUML. Hay vistas numeradas:

1. Dominio: responsabilidades, no tablas JPA.
2. Núcleo: cuenta -> convocatoria -> inscripción; ciclo anual y código son procesos distintos.
3. Ejecución: asistencia -> solicitudes -> decisiones -> cierre/ajustes.
4. Materiales: solicitud -> recepción -> devolución; no se reserva stock.
5. Comunicaciones: un comunicado puede producir muchos avisos personales; no todo aviso nace de un comunicado.

Las clases estereotipadas `tabla existente` representan persistencia, no agregados. Las relaciones
`FK declarada` existen en SQL; `asociación lógica sin FK` no supone una restricción física inexistente.
Multiplicidades concretas y reglas de plazos/cupos están en [REQUISITOS.md](REQUISITOS.md).

El archivo se preparó mediante ingeniería inversa y se validan IDs y referencias. Su apertura y
distribución visual deben verificarse en StarUML. No acredita que el código histórico fuera generado.
Las fuentes `.puml` complementan la vista de arquitectura y casos de uso, sin requerir imágenes.

Para reconstruir un modelo inicial sin sobrescribir el editado:

```bash
python3 tools/build_model.py
python3 tools/build_model.py --check backend/target/model/Convocatorias.mdj
```

La salida por defecto está en `backend/target/model`; copiar el archivo a una carpeta local estable
antes de editarlo, porque `clean` elimina `target`. Comparar y editar; no reemplazar el `.mdj`
manualmente enriquecido sin revisión. El formato se contrastó con el ejemplo UML oficial v7 de
`github.com/staruml/staruml-samples`; no se copiaron sus modelos de negocio.

### Generación hacia Java

El modelo conceptual no es directamente un proyecto Java completo. Para generar, preparar la vista
de implementación con paquetes, firmas y tipos correctos; usar la extensión Java de StarUML y un
directorio vacío. Comparar y compilar el esqueleto. Integrar selectivamente: **nunca generar encima
del código de producción ni sustituir métodos del dominio por cuerpos vacíos**.

## Estado de la persistencia

| Área | Implementación actual |
|---|---|
| Anuncios y notificaciones | Adaptadores JPA independientes; consulta tipada de avisos |
| Materiales y catálogo opcional | JPA sin herencia de adaptadores JDBC; nombres de actores consultados por lote |
| Administración de metas de horas | Lecturas y escrituras JPA independientes; respuesta y auditoría conservadas |
| Ciclo anual e historial de graduación | Adaptador JPA independiente; ciclos leídos con ORM e historial insertado mediante entidad |
| Cuentas, convocatoria, inscripciones y asistencia | Escrituras JPA; algunas operaciones/proyecciones SQL conservadas |
| Auditoría, códigos, mantenimiento y cierres | Operaciones históricas explícitas, pendientes de revisión por capacidad |

Los adaptadores restantes `Jpa* extends Jdbc*` son transitorios. No se elimina un adaptador hasta
migrar y comprobar todos sus consumidores. Las tablas complejas se revisan después de comprender sus reglas.

La consulta de metas usada por graduación (`operations/infrastructure/JpaGraduationSettings`)
también usa JPA, mediante una proyección mínima de lectura propia de Operaciones, sin depender
de las entidades de Administración. No se modifica el contrato numérico del cálculo de graduación.

Hibernate utiliza JDBC internamente: se retira el uso directo de JdbcTemplate por capacidad, no el
driver de PostgreSQL. Flyway mantiene SQL versionado. La entrega única de avisos usa una sentencia
nativa JPA `ON CONFLICT` contra el índice parcial: evita carreras sin añadir tablas ni bloqueos de cuentas.

El registro del ciclo anual también conserva `ON CONFLICT` mediante `EntityManager`, usando la
clave primaria existente `(user_id, year)`. La conservación de compromisos al graduarse usa un
`INSERT ... SELECT` aditivo y atómico: solo participaciones confirmadas con inicio igual o posterior
a la aprobación. No renueva todos los años ni reemplaza el origen, autor o fecha de ciclos existentes.
Estas dos escrituras siguen siendo SQL nativo explícito, no operaciones ORM puras. El historial de
graduación se agrega con Hibernate y participa en la misma transacción que ciclos y notificaciones.

## Consistencia y escalabilidad sencilla

- Transacción de caso de uso con convocatoria antes de participante/inscripción.
- Bloqueo pesimista en operaciones que compiten por cupos o identidad del responsable.
- Flush antes de consultas SQL y refresco bajo bloqueo durante coexistencia ORM/SQL.
- Entidades JPA no expuestas en HTTP; `open-in-view=false`; fotos fuera del mapeo normal de convocatoria.
- Cierres originales inmutables; ajustes anexos y decisiones conservadas.
- Lecturas mínimas por contexto, sin cargar toda una cuenta para mostrar un nombre o destinatario.
- Optimizar listados y consultas por lote según medición; no añadir infraestructura distribuida por anticipación.

ArchUnit comprueba dependencias. Las consultas antiguas que ejecutan mantenimiento todavía necesitan
separación cuidadosa: no se cambia su semántica de expiración sin pruebas de regresión.
