# Migración de la versión 1 a la versión 2

## Objetivo

Este documento registra el inventario real de la versión 1 y las decisiones de migración hacia el **Sistema de Convocatorias de la Cruz Roja Peruana – Filial Arequipa, versión 2**.

La versión 2 se desarrollará en este repositorio sobre `main`, por decisión posterior del propietario. La etiqueta `v2.0.0` se creará únicamente después de completar y verificar la versión. El frontend no tendrá pruebas automatizadas por solicitud del propietario; sí conservará lint de accesibilidad, comprobación de contraste y compilación TypeScript.

## Comandos de inspección ejecutados

```bash
git fetch --all --prune
git status --short --branch
git branch --all --no-color
git log --all --oneline --decorate --graph -30
git ls-tree -r --name-only <rama>
git log -1 --format='%h %s' <rama>
```

Todos los comandos se ejecutaron correctamente. El árbol estaba limpio y `main` estaba sincronizada con `origin/main` al iniciar el inventario.

## Inventario de ramas

Las ramas con sufijo `-debunked` son los nombres que existen realmente en el remoto; no se asumieron los nombres históricos sin sufijo mencionados en el README.

| Rama remota | Contenido de valor encontrado | Uso previsto en v2 |
|---|---|---|
| `origin/main` | Estructura Maven estándar; shared kernel; dominios de usuarios, convocatorias e inscripciones; 10 plantillas HTML; CSS; datos TSV; pruebas de dominio; README y capturas. | Fuente consolidada para reglas, lenguaje del negocio y flujos. No se copiará su arquitectura ni CSS literalmente. |
| `origin/desarrollo-debunked` | Integración histórica principal de BC1, BC2 y BC3; specifications de inscripción; vistas y recursos. | Fuente principal histórica solicitada para contrastar reglas y casos de uso. |
| `origin/10-feature03-bc2-convocatorias` | Refactor DDD más reciente de convocatorias, eventos, repositorios, seguridad separada y pruebas. | Fuente preferente para comprender BC2 y sus invariantes. |
| `origin/11-feature04-bc3-inscripciones` | Apunta al estado integrado previo; dominio de inscripción, consulta y vistas. | Referencia de BC3; se complementa con las ramas específicas de inscripción. |
| `origin/8-feature01-maven-shared-kernel` | Apunta al estado integrado previo; estructura anterior y shared concepts. | Referencia histórica; la v2 usará su propio paquete base y límites por módulo. |
| `origin/9-feature02-bc1-usuarios` | Apunta al estado integrado previo; usuarios, perfiles y postulaciones. | Referencia de BC1, complementada con el refactor de usuarios. |
| `origin/kzenayuca-shared-kernel-maven` | Migración a layout Maven estándar y tipos `AggregateRoot`, `DomainEvent`, `DomainException`, `ValueObject`. | Se reutilizan ideas, no necesariamente las clases; la v2 evitará acoplamiento compartido innecesario. |
| `origin/kzenayuca-user-management` | Refactor de usuarios con entidades, value object, evento y puertos de repositorio. | Fuente preferente para perfil y postulaciones; se adapta al módulo `users`. |
| `origin/feature/solid-bc3-inscripciones-debunked` | Assembler, specifications y puerto de consulta para aplicar SRP/OCP/ISP/DIP. | Referencia para separar consultas y políticas en `enrollments`. |
| `origin/feature/hf3.2.1-estado-inscripcion-debunked` | Historia de estado de inscripción, entidad, estados y vista correspondiente. | Se conserva el flujo como “Mis inscripciones” con línea de tiempo. |
| `origin/feature/mi_parte-debunked` | Integración ejecutable, vistas de gestión, CSS y un login único. | Referencia de navegación y gestión; el login se sustituye por el shell React accesible. |
| `origin/kzenayuca-debunked` | Mismo punto que `feature/mi_parte-debunked`, incluido `login.html`. | Referencia duplicada; no requiere migración separada. |
| `origin/parte_l-debunked` | Registro de postulantes, fábrica, repositorio y demo Spring independiente. | Reglas y flujo de gestión de participantes; en v2 se ubican en `enrollments`. |
| `origin/Mikaela-debunked` | Modelo más amplio: usuarios, jefatura, voluntario, requisitos, servicios, convocatoria, inscripción, fábricas y seis vistas estáticas; capturas. | Fuente para ampliar el vocabulario y contrastar el modelo extendido. |
| `origin/David-Progreso-debunked` | Demo Spring de perfil/postulantes/convocatorias/inscripciones y plantillas. | Referencia del perfil y flujo de postulantes. Se descartan artefactos `target/`. |
| `origin/David_LL-debunked` | Vistas funcionales y recursos compilados, con poco código de dominio. | Referencia visual secundaria. Se descartan artefactos `target/`. |
| `origin/feature/readme-final-debunked` | README final, documentación y estado integrado de vistas/dominio. | Fuente documental para trazabilidad académica. |

## Vistas y recursos verificados

En `origin/main` existen realmente estas vistas:

- `main.html`
- `convocatorias.html`
- `inscripcion.html`
- `estado_inscripcion.html`
- `gestion_inscripciones.html`
- `perfil_voluntario.html`
- `gestion_convocatorias.html`
- `equipo_asignado.html`
- `postulantes.html`
- `verificacion_minimos.html`

También existe `login.html` en `origin/feature/mi_parte-debunked` y `origin/kzenayuca-debunked`. Los tres logins por rol mencionados en el README no son tres archivos HTML independientes: se verificaron como capturas de interfaces por rol.

Se visualizaron las capturas reales:

- `docs/Homepage.png`
- `docs/convocatoriasDisponibles.png`
- `docs/Incripcion.png`
- `docs/estadoInscripcion.png`
- `docs/perfil.png`
- `docs/MinimoParticipantes.png`
- `docs/loginVoluntario.png`
- `docs/loginJefatura.png`
- `docs/loginGraduado.png`
- `docs/Modelo_Extendido.png`

La v1 usa rojo institucional, tarjetas blancas y flujos reconocibles. La v2 conservará el vocabulario, la jerarquía funcional y los accesos por rol. Se corregirán la navegación duplicada, falta de ayudas y validación visible, dependencia excesiva del color para estados, ausencia de foco claramente documentado y baja adaptación de tablas/formularios.

## Matriz inicial de migración

| Elemento de v1 | Rama/archivo de origen | Decisión | Destino previsto en v2 |
|---|---|---|---|
| Inicio y accesos por rol | `origin/main:src/main/resources/templates/main.html`; capturas `Homepage.png` y logins | Mejorar como shell React con sesión y rol visibles, navegación contextual y widgets. | `frontend/src/app/` y widgets descubiertos desde `frontend/src/features/*/index.ts` |
| Login único y perfiles visuales por rol | `origin/feature/mi_parte-debunked:resources/login.html`; capturas de login | Unificar en una pantalla; autenticación de sesión con BCrypt. | Shell frontend y `/api/v1/auth/*` del módulo `users` |
| Convocatorias disponibles | `origin/main:src/main/resources/templates/convocatorias.html` | Rehacer con filtros, búsqueda, badges, barras de cupo/mínimo y agenda. | `frontend/src/features/callouts/` |
| Gestión de convocatorias | `origin/main:src/main/resources/templates/gestion_convocatorias.html`; dominio de `origin/10-feature03-bc2-convocatorias` | Reutilizar reglas e intención; ampliar estados y permisos. | Módulo `callouts` |
| Inscripción | `origin/main:src/main/resources/templates/inscripcion.html` | Rehacer como Stepper de tres pasos con resumen y prevención de errores. | `frontend/src/features/enrollments/` |
| Estado de inscripción | `origin/feature/hf3.2.1-estado-inscripcion-debunked`; `estado_inscripcion.html` | Conservar flujo y mejorar con línea de tiempo y acciones permitidas. | Módulo `enrollments` |
| Gestión de inscripciones y participantes | `gestion_inscripciones.html`, `postulantes.html`, `verificacion_minimos.html`; `origin/parte_l-debunked` | Consolidar filtros, mínimo de participantes, confirmación y rechazo. | Módulo `enrollments` |
| Perfil del voluntario | `perfil_voluntario.html`; `origin/kzenayuca-user-management` | Conservar campos útiles; añadir disponibilidad y progresión controlada de nivel. | Módulo `users` |
| Equipo asignado | `equipo_asignado.html` y dominio BC2 | Integrar la información útil en participantes/asistencia; no mantener un agregado aislado sin requisito v2. | `enrollments` y `attendance` |
| Entidad y estados de inscripción | `bc3_inscripciones/dominio` | Reescribir en inglés, preservar transiciones y ampliar cancelación como estado final. | `pe.cruzroja.convocatorias.enrollments.domain` |
| Convocatoria, horario y ubicación | `bc2_convocatorias/dominio`; refactor de rama 10 | Reutilizar conceptos e invariantes, ampliando cupos, niveles y ciclo de vida. | `pe.cruzroja.convocatorias.callouts.domain` |
| Perfil, voluntario y postulación | `usuarios/dominio`; ramas de usuarios y participantes | Reorganizar como `User` y `VolunteerProfile`; mover gestión de participantes a inscripciones. | Módulos `users` y `enrollments` |
| Repositorios en memoria/TSV | `infraestructura/persistence`, `resources/database/*.tsv` | Descartar implementación; conservar datos conceptuales como semilla. | Repositorios `JdbcTemplate` y SQL por módulo |
| CSS de v1 | `src/main/resources/static/sistema.css` | No copiar. Sustituir por tokens y componentes Soft UI accesibles. | `frontend/src/shared/ui/` |
| Paquetes `bc1_usuarios`, `bc2_convocatorias`, `bc3_inscripciones` | Varias ramas | Descartar nombres con dígitos y español técnico; corrige el hallazgo Sonar `java:S120`. | `pe.cruzroja.convocatorias.users`, `.callouts`, `.enrollments` |
| Modelo extendido | `docs/Modelo_Extendido.png`, `docs/ModeloDominioExtendido.puml` | Usar como referencia de vocabulario, no como diseño vinculante. | `docs/architecture.md` y documentación por módulo |
| Artefactos compilados | `target/` en ramas David | Descartar. | Ninguno; cubiertos por `.gitignore` |

## Hallazgos que debe corregir la v2

1. Paquetes con dígitos y nombres inconsistentes (`bc3_inscripciones`, `bc2_convocatorias`) se reemplazan por módulos ingleses sin prefijos numéricos.
2. La persistencia TSV o en memoria se reemplaza por PostgreSQL 16 y `JdbcTemplate`.
3. Los límites entre módulos se formalizan mediante paquetes `api` y eventos internos.
4. Las reglas de autorización salen de controladores/sesión HTTP y se aplican en servicios de aplicación mediante `CurrentUser`.
5. Las plantillas y CSS global de v1 se rehacen como React + TypeScript y sistema Soft UI basado en tokens.
6. Los estados no dependerán solo del color: incluirán icono y texto.
7. Inventario, asistencia, justificaciones y notificaciones son módulos nuevos sin antecedente funcional completo en v1.
8. La v2 conservará pruebas backend de dominio, aplicación y repositorio. Por decisión del propietario, no se implementarán pruebas frontend automatizadas.

## Estado de la migración

Este documento es inicial y se actualizará durante las fases siguientes con los destinos definitivos y cualquier decisión de descarte adicional.
