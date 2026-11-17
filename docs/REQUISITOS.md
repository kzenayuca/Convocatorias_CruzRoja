# Requisitos, flujo y restricciones

## 1. Qué resuelve el sistema

Organiza convocatorias y participación de voluntarios de Cruz Roja Filial Arequipa: cuentas,
equipos, programación, asistencia, horas y solicitudes. Es una aplicación local con datos demo,
no una plataforma de despacho de emergencias ni una certificadora de cursos.

### Actores

| Actor | Qué puede hacer |
|---|---|
| Visitante | Solicitar cuenta e iniciar sesión si fue activada |
| Voluntario en formación | Consultar oportunidades compatibles, participar y consultar sus resultados |
| Voluntario graduado | Lo anterior, con ciclo anual y habilitaciones cuando el puesto las exige |
| Responsable asignado | Gestionar exclusivamente la convocatoria a la que está asignado |
| Jefatura | Administrar cuentas, convocatorias, equipos, solicitudes, metas y reportes |

Responsable no es un rol global. Graduación, rol, estado y habilitaciones son conceptos distintos.

## 2. Recorrido principal

1. La persona solicita cuenta. Queda `PENDIENTE`, `VOLUNTARIO` y sin graduación.
2. Jefatura la revisa. Solo `ACTIVO` permite acceder; suspender/inactivar invalida el acceso.
3. Jefatura crea una convocatoria `BORRADOR`; el sistema reserva un código único.
4. Publica un borrador válido. Queda `ABIERTA` mientras falten condiciones del equipo.
5. El voluntario consulta horario, lugar, condiciones y puesto. Acepta los requisitos declarados.
6. El sistema comprueba elegibilidad, ciclo anual si corresponde, plazo, cruces y reservas de cupo.
7. La inscripción válida queda `CONFIRMADA`, presentada como **ACEPTADA**. No hay lista de espera.
8. Si el equipo cumple todos los requisitos, la convocatoria queda `CERRADA`, presentada como **Completa**.
9. Jefatura puede asignar un responsable graduado previamente inscrito. No añade una plaza.
10. El gestor abre el servicio, los participantes registran marcas y el gestor confirma horas.
11. Se cierra la ejecución, se resuelven incidencias y Jefatura aprueba el reporte mensual.

**Completa no significa finalizada.** La cobertura y la ejecución se muestran por separado.
No es obligatorio tener responsable: sin él opera Jefatura.

### Caminos alternativos

- Editar: antes de apertura, conservando equipo, tipo con participantes y compatibilidad horaria.
- Cancelar: conserva registros; no es un borrado físico.
- Falta de cobertura al vencer el plazo: cancelación automática, sin inasistencia.
- Imposibilidad anticipada: solicitud de reemplazo; conserva compromiso hasta aprobar un sustituto.
- Incidencia posterior: revisión de marcas o justificación, según el problema.

## 3. Reglas verificables

| ID | Área | Restricción |
|---|---|---|
| RN01 | Cuentas | Roles VOLUNTARIO/JEFATURA; estados PENDIENTE/ACTIVO/RECHAZADO/INACTIVO/SUSPENDIDO |
| RN02 | Identidad | DNI de 8 dígitos y único; correo único sin distinguir mayúsculas; contraseña 8–72 caracteres |
| RN03 | Perfil | Teléfono directo; otros cambios requieren aprobación; una solicitud de perfil pendiente por persona |
| RN04 | Convocatoria | Inicio futuro, fin posterior e intervalo máximo de 48 h; plazo futuro y <=inicio |
| RN05 | Cupos | Total 1–10000; incluye todas las funciones; mínimo de graduados 0–total; piloto/radio 0 o 1 |
| RN06 | Composición | Una persona cuenta una vez; su graduación puede satisfacer el mínimo; no se consumen reservas faltantes |
| RN07 | Puestos | General admite formación/graduados; piloto/radio exigen graduación y habilitación; no hay nueva plaza FORMACION exclusiva |
| RN08 | Ciclo anual | Graduado activo requiere ciclo del año de inicio; enero–diciembre, sin renovación automática ni creación retroactiva |
| RN09 | Disponibilidad | No se superponen compromisos de una persona; fin a las 13:00 e inicio a las 13:00 es válido; sin margen de traslado |
| RN10 | Inscripción | Aceptación automática, requisitos aceptados, sin duplicados ni lista de espera; asignación directa solo Jefatura con verificación |
| RN11 | Responsable | Opcional, único, graduado activo ya confirmado como general; antes del inicio/apertura; cambio o retiro con motivo e historial |
| RN12 | Código | E/I/G + mes/año de creación + correlativo por categoría/año; único e inmutable; no reinicia cada mes |
| RN13 | Apertura | Tipo SERVICIO, equipo completo; desde una hora antes del inicio y antes del fin |
| RN14 | Cierre | Servicio abierto, sin cancelación; no se cierra antes del fin; foto opcional |
| RN15 | Marcación | Propia y con inscripción confirmada; servicio abierto; ventana inicio−1 h a fin+1 h; entrada antes del fin y salida tras entrada |
| RN16 | Horas | Confirmar después del fin; presencia dentro del horario; sin extras; ajuste excepcional solo Jefatura, 0–48 h |
| RN17 | Penalización | Ausencia: 2×duración; tardanza >10 min: 2×retraso completo, limitado a duración; hasta 10 min hay tolerancia |
| RN18 | Justificar | Desde el fin hasta fin+72 h inclusive; solo autor; motivo; evidencia opcional; solo Jefatura resuelve |
| RN19 | Efecto | Justificar elimina deducción, no acredita presencia inexistente; Jefatura y servicios cancelados están exentos |
| RN20 | Reemplazar | Solicitar >=48 h antes; propuesta de nombre opcional; Jefatura verifica y asigna otra persona elegible antes de inicio/apertura |
| RN21 | Relevo | Aprobar cancela al titular sin inasistencia y confirma al sustituto, conservando puesto y cupos; rechazar mantiene al titular |
| RN22 | Archivos | JPG/PNG reales, <=10 MB y <=40 MP; evidencia privada para autor y Jefatura |
| RN23 | Materiales | Solicitud de almacén, no stock reservado; cantidad 1–10000; recepción única <=solicitado; devolución única <=recibido, incluso 0 |
| RN24 | Metas | Configurables por Jefatura, 0.01–100000 y hasta 2 decimales; formación acumula años, graduados seguimiento anual |
| RN25 | Graduación | Saldo neto suficiente y validación de Jefatura; no automática; mantiene historial y compromisos |
| RN26 | Reporte | Solo mes terminado y sin servicios del periodo pendientes de finalizar; cierre único e inmutable; ajustes posteriores separados |
| RN27 | Plazo reporte | Publicar entre 1 y 3 del mes siguiente; se señala retraso, pero se permite aprobar después |
| RN28 | Comunicaciones | Avisos personales internos; comunicados solo Jefatura a TODOS/VOLUNTARIO/JEFATURA; no correo ni WhatsApp |
| RN29 | Privacidad | Equipo solo gestores; datos generales/reportes solo Jefatura; teléfono responsable solo Jefatura y confirmados; PDF general solo Jefatura |
| RN30 | Tiempo | America/Lima; el mes del servicio se determina por su inicio |

Los registros de asistencia anteriores a la política 2 conservan su tratamiento de tardanza.
El mantenimiento resuelve registros pendientes a los 7 días; un cierre mensual anterior los congela.
No se inventan horas por falta de marcas. Cursos/experiencia escritos se declaran y verifican
personalmente: el sistema no certifica esos antecedentes.

## 4. Requisitos funcionales y trazabilidad

| ID | Capacidad | Componente principal | Reglas |
|---|---|---|---|
| RF01 | Acceder y administrar cuentas/perfil | AccountsUseCases | RN01–03 |
| RF02 | Crear/publicar/editar/cancelar convocatoria | ServicesUseCases | RN04–06, RN12 |
| RF03 | Consultar e inscribirse | EnrollmentUseCases / CalloutWorkflow | RN07–10 |
| RF04 | Componer equipo y asignar responsable | ServicesUseCases / Callout | RN05–06, RN11 |
| RF05 | Consultar programación y operar | ServicesUseCases | RN13–15, RN29 |
| RF06 | Registrar/confirmar/revisar asistencia | AttendanceUseCases | RN15–17 |
| RF07 | Justificar o solicitar reemplazo | AttendanceUseCases / ReplacementUseCases | RN18–22 |
| RF08 | Consultar horas, graduación y aprobar reportes | AttendanceUseCases / AccountsUseCases | RN24–27 |
| RF09 | Gestionar solicitudes de materiales | MaterialsUseCases | RN23 |
| RF10 | Publicar y consultar avisos | NoticesUseCases | RN28–29 |
| RF11 | Registrar ciclo anual | AnnualEnrollmentUseCases | RN08 |

### Criterios principales

- RF02: entrada válida de Jefatura crea borrador con código y detalle persistente; fechas inválidas o
  permisos insuficientes no crean registros parciales.
- RF03: una inscripción válida aparece aceptada; dos personas no ocupan la última plaza; un rechazo
  no genera lista de espera ni duplica participantes.
- RF04: cambiar responsable conserva plazas e historial; al retirarlo pierde permisos y sigue inscrito.
- RF07: se respetan límites inclusivos de 48/72 h y privacidad; una decisión no duplica devoluciones.
- RF09: no se recibe más de lo solicitado ni se devuelve más de lo recibido; no hay reserva de stock.
- RF10: cada persona ve sus avisos; leer conserva la primera fecha; un evento único no duplica avisos.

### Evidencia de implementación

Esta matriz vincula una exigencia con su implementación y una comprobación concreta. No sustituye
la validación del usuario ni demuestra el cumplimiento completo de un laboratorio.
Las rutas Java son relativas a `backend/src/main/java/pe/cruzroja/convocatorias`; las pruebas,
a `backend/src/test/java/pe/cruzroja/convocatorias`.

| Requisito u objetivo | Archivo que lo demuestra | Prueba/evidencia | Pendiente |
|---|---|---|---|
| RN24: administrar metas decimales y actualizarlas conjuntamente | `administration/infrastructure/JpaAdministrationRepository.java`, `administration/domain/HourTargets.java`, `operations/infrastructure/JpaGraduationSettings.java` | `api/AdministrationPersistenceTest`: persistencia, JSON, auditoría, rollback y lectura de metas para graduación; `api/JpaGraduationSettingsTest`: selección de condición; `api/WorkflowTest`: contrato HTTP | Validación con el usuario |
| RF10: avisos personales y primera lectura | `communications/domain/Notification.java`, `communications/infrastructure/JpaNoticeRepository.java` | `communications/domain/NotificationTest`, `api/CommunicationPersistenceTest` | Validación con el usuario |
| RF11 / RN08 y RN25: ciclo único por persona/año, compromisos e historial de graduación | `membership/infrastructure/JpaAnnualEnrollmentRepository.java`, `membership/infrastructure/persistence/AnnualEnrollmentEntity.java`, `membership/infrastructure/persistence/GraduationHistoryEntity.java` | `api/AnnualEnrollmentPersistenceTest`: orden/JSON, metadatos heredados, conservación selectiva, historial, rollback y concurrencia; `api/WorkflowTest`: permisos y reglas por HTTP | Las inserciones idempotentes de ciclos conservan SQL nativo JPA; validación con el usuario |
| Integridad: entrega única de avisos bajo concurrencia | `communications/infrastructure/JpaNotificationPublisher.java` | `api/CommunicationPersistenceTest.concurrentUniqueDeliveryProducesOneNoticeWithoutPoisoningTransactions` | Medición con carga representativa |
| Independencia del dominio | Clases de `domain` y contratos de repositorio | `architecture/ArchitectureTest` | Revisar responsabilidades y contratos heredados; ArchUnit no demuestra por sí solo buen diseño |

## 5. Requisitos no funcionales

- Seguridad: autorización backend, sesión, CSRF, BCrypt y controles de evidencia.
- Integridad: unicidad, transacciones y bloqueos; errores no dejan operaciones parciales.
- Mantenibilidad: dominio sin frameworks, contratos y adaptadores comprobados por ArchUnit.
- Rendimiento: evitar N+1 y cargas binarias; fijar carga/entorno antes de acordar un SLA.
- El objetivo inicial de respuesta <5 minutos no se considera medido ni garantía de producción.

## 6. Cómo se relacionan las tablas

### Núcleo

`users` + `volunteer_profiles` describen la cuenta. `callouts` describe la actividad.
`enrollments` vincula una persona con una convocatoria y un puesto. `annual_enrollments` es un
registro administrativo del año: **no es otra inscripción al servicio**.

### Ejecución e incidencias

`attendance_records` pertenece a la participación. `attendance_review_requests` pide corregir marcas.
`justifications` registra ausencia, tardanza o reemplazo preventivo; este último puede no tener asistencia.
`justification_resolutions` conserva decisiones. `monthly_report_adjustments` registra su efecto después
del cierre sin reescribir `monthly_report_rows`.

### Apoyo

- `material_requests`: solicitudes, recepción y devolución vinculadas a convocatoria.
- `materials`: catálogo opcional histórico; `material_assignments`/`loans` no gobiernan el flujo actual.
- `announcements`: comunicado institucional; `notifications`: avisos individuales. Un aviso no es una
  copia relacional obligatoria de un comunicado: también puede originarse en cuentas, asistencia, etc.
- `responsible_history`, `service_code_history`, `graduation_history`: auditoría del negocio.
- `service_code_counters`: evita códigos duplicados bajo concurrencia.
- `flyway_schema_history`: control técnico de migraciones, no función de negocio.

**Pasar a JPA no obliga a crear nuevas tablas de negocio.** Se mapean las existentes.
El modelo distingue asociaciones lógicas de claves foráneas realmente declaradas: el esquema heredado
no declara todas las relaciones del núcleo como FK. No se añadirán restricciones sin revisar datos huérfanos.

## 7. Modelo editable

Generar localmente con `python3 tools/build_model.py` y abrir
`backend/target/model/Convocatorias.mdj` en StarUML. El `.mdj` no se incluye en Git.
Comenzar por los diagramas numerados:
dominio, núcleo de datos, ejecución/incidencias, materiales y comunicaciones.
Es un modelo de ingeniería inversa; no prueba generación histórica de Java desde StarUML.
La revisión visual y la validación con el experto del negocio siguen siendo necesarias.
