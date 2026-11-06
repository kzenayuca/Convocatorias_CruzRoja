# Documentación esencial · versión 2.7.0

## Estado real

Aplicación local persistente: cuentas, perfiles, convocatorias, inscripciones, programación, asistencia, horas, justificaciones, materiales y notificaciones. No se usa `localStorage` como fuente de inscripciones.

Se conserva el diseño: colores, tarjetas y barra lateral; se añaden espacios, páginas de detalle y secciones de gestión plegables. No se ha separado Selenium en otro repositorio ni implementado el Lab 6.

El backend ahora es un **monolito modular con DDD y Clean Architecture**: contextos Voluntarios, Operaciones, Administración y Comunicaciones, con dominio/aplicación Java puros y adaptadores web/JDBC. Los controladores no contienen SQL ni reglas de negocio. Los límites de dependencia se comprueban con ArchUnit. Diseño, contextos, puertos, agregados y decisiones: [ARCHITECTURE.md](ARCHITECTURE.md).

## Reglas y permisos

| Área | Comportamiento |
|---|---|
| Cuentas | Solo roles VOLUNTARIO y JEFATURA. Jefatura activa, valida graduación y habilitaciones. Los permisos se recargan en cada petición. Ser graduado no otorga privilegios globales; RESPONSABLE es una función por servicio. RECLUTADOR ya no es asignable. Jefatura administra, no ocupa nuevas plazas de voluntario. |
| Perfil | Ficha de lectura; **Modificar** abre los formularios. Teléfono de cambio directo, otros datos requieren aprobación. Correo/DNI únicos. No se muestran niveles ni la etiqueta BÁSICO. Jefatura ve su perfil administrativo sin meta personal. |
| Requisitos | No hay niveles de elegibilidad. Cada convocatoria declara conocimientos, cursos afines, manejo de herramientas, experiencia u otras condiciones. La persona acepta cada requisito al inscribirse; jefatura verifica las declaraciones, también al inscribir directamente. El sistema no certifica cursos ni documentos. Graduación y habilitaciones de piloto/radio siguen siendo condiciones independientes. |
| Convocatorias | Una página por convocatoria. Jefatura crea; el responsable graduado asignado puede gestionar únicamente su servicio, asistencia, materiales y fotos. Haber creado un servicio o ser un antiguo reclutador no otorga permisos. Jefatura gestiona todos los servicios. |
| Código | **Automático al crear**, correlativo por categoría/año y mes de creación como etiqueta: `I-FEB-2027-024`, luego `I-MAR-2027-025`. Reinicia por categoría en enero del siguiente año, no cada mes. Cuenta borradores y cancelados; no se reutilizan números ni códigos. Asignación y alta en una transacción con contador bloqueado en PostgreSQL. Categoría/código/año de creación son inmutables al editar fechas; se conservan sin renumerar los códigos históricos. El campo de código de clientes antiguos se ignora al crear; el código del servidor es el válido. |
| Tipo de servicio | Categorías **E · Externo** (conciertos/pasacalles/eventos externos), **I · Interno** (en o para la filial: ambulancia, farmacia, dirección) y **G · Guardia** (equipo/ambulancia disponibles en la filial para llamadas). La descripción de la actividad sigue siendo texto libre. Para clientes antiguos sin categoría se usa I; la web siempre permite elegirla al crear. |
| Cupos | Un único **total de voluntarios**, incluidos responsable, piloto y radioperador. “Voluntario” admite graduados y personas en formación; “voluntario graduado” solo graduados. Se configura un **mínimo de graduados dentro del total**, no plazas adicionales. Total 4, mínimo 1 graduado = 4 personas, no 5. Total 1, mínimo 1 = una única persona graduada. No hay una nueva categoría exclusiva para formación. |
| Funciones | Piloto/radio son funciones obligatorias cuando se solicitan, dentro del total, con graduación y habilitación verificada por jefatura. Una persona ocupa un solo puesto y cuenta una sola vez; su graduación puede satisfacer simultáneamente el mínimo de graduados. Se reserva espacio para funciones aún vacantes y graduados faltantes; ni una inscripción ni un reemplazo pueden consumir ese espacio. |
| Responsable | Opcional, único, voluntario graduado activo **ya inscrito y confirmado como voluntario general**, sin cruce. Jefatura lo asigna antes del servicio. Convierte la inscripción existente sin duplicarla; no al piloto/radioperador ni a personas con reemplazo pendiente. Solo mientras esté asignado y tenga inscripción confirmada puede gestionar esa convocatoria, revisar asistencia y subir fotos; ninguna otra. Sin responsable opera jefatura. |
| Cambio de responsable | La asignación inicial se bloquea cuando ya hay uno. **Cambiar o retirar responsable** es una acción separada de jefatura, antes del inicio, con motivo obligatorio y comprobación de que el responsable actual no cambió mientras se editaba. Se guarda quién, cuándo, anterior, nuevo y motivo. El anterior conserva su plaza como voluntario general, pero pierde inmediatamente permisos de responsable. El cambio no inscribe personas nuevas ni excede cupos. |
| Contacto | El contacto se deriva del teléfono actual del responsable con inscripción confirmada, no de un texto manual. Solo jefatura y participantes con inscripción CONFIRMADA ven el número. Los demás ven **Consultar a jefatura**, igual que cuando no hay responsable o no tiene teléfono. No se publica el número en el PDF de programación. |
| Inscripción | Aceptación automática de un voluntario activo que cumple puesto, requisitos declarados, horario y cupo. Estado interno CONFIRMADA, mostrado como ACEPTADA. Los niveles históricos no bloquean ni cancelan inscripciones. |
| Ciclo anual | Enero-diciembre para todos los reportes. Los graduados deben **registrar su inscripción administrativa para el año de inicio del servicio**, distinta de inscribirse automáticamente en cada convocatoria. La registra el propio graduado activo o Jefatura; es idempotente, con autor/fecha y aviso único. Se permite registrar un año futuro para actividades ya programadas; no se crean ciclos retroactivos ni se renueva automáticamente cada enero. Sin ciclo del año, no se ofrecen nuevas actividades de ese año ni se permite asignación directa/reemplazo. La formación no tiene esta barrera anual. |
| Inscripción directa | Solo jefatura, convocatoria publicada, antes del inicio, apertura operativa y plazo. Permite **buscar por nombres o apellidos**, sin distinguir tildes, y elegir una persona elegible. Jefatura confirma que verificó los requisitos declarados. El servidor vuelve a comprobar cuenta, graduación, habilitaciones, cupo inclusivo, reservas, duplicados y horario en la misma transacción que la autoinscripción. Queda CONFIRMADA, notifica y aparece en la agenda. Se guardan `assigned_by`, `assigned_at` y `requirements_accepted`; no se inventa un reemplazo ni se revierte una resolución previa. |
| Horarios | Se controla el cruce de la **misma persona**, no entre actividades. Fin 13:00/inicio 13:00 permitido. Sin margen de traslado. Servicios solapados con equipos diferentes permitidos. |
| Cobertura | ABIERTA si faltan personas, graduados mínimos o funciones requeridas; CERRADA solo cuando se cumplen todos y el total es exacto. Un responsable asignado cuenta como persona; no es obligatorio asignarlo para cerrar. Sin cobertura al vencer el plazo: CANCELADA, avisos, sin inasistencia. Sin lista de espera. |
| Ejecución | PROGRAMADO, EN CURSO, FINALIZADO o CANCELADO; separado de cobertura. Abre con puestos completos, desde una hora antes; cierra después del fin programado. Foto no obligatoria. INGRESO no registra horas de servicio. |
| Programación | Voluntarios, incluidos responsables, solo ven servicios con inscripción CONFIRMADA propia. Jefatura ve todo. Selección diaria, servicios que atraviesan medianoche y enlaces. **Solo jefatura descarga el PDF**, con validación en backend; el voluntario no ve el botón. No incluye DNI ni teléfonos. |
| Equipos | Nómina por puesto para jefatura y gestores operativos autorizados del servicio, no para participantes ordinarios. Solo jefatura consulta todos los usuarios y reportes generales. |
| Reemplazo | Titular solicita desde Mis servicios con **al menos 48 h antes del inicio**, motivo/sustento y, a elección, **nombre completo de quien consiguió** o solicitud de **búsqueda/asignación de Jefatura**. Evidencia opcional. En ambos casos **solo Jefatura** confirma una persona elegible y requisitos, en una transacción antes del inicio. La propuesta no libera plaza. Conserva puesto, mínimos, funciones, ciclo anual si es graduado y no admite cruces ni exceso de cupo. Original CANCELADA sin inasistencia; el reemplazo queda ACEPTADO. La vacante sigue cubierta por el reemplazo, **sin contar dos personas** ni contar cancelaciones sin sustituto. Si se releva al responsable, pasa la función y permisos con historial. |
| Cambios | Antes del inicio se puede cambiar horario/lugar/requisitos y notificar a inscritos. Un cambio de horario no crea cruces; con participantes no se cambia tipo de convocatoria. No se eliminan plazas ocupadas. |
| Fotos | JPG/PNG, máximo **10 MB** y 40 megapíxeles. Botón **Adjuntar foto**, opcional. Al cerrar se ofrece adjuntarla entonces o más adelante; cerrar nunca exige foto. Solo jefatura o el responsable confirmado del servicio pueden subir/reemplazar, también después del cierre. Ver la foto requiere acceso a la convocatoria. Perfil muestra fotos con entrada registrada. Se conserva la foto anterior en archivo histórico; imágenes antiguas mayores de 10 MB no se eliminan. |
| Materiales | Solicitud de almacén externo, no inventario. En la web **solo se escribe** material/referencia, cantidades y especificaciones: no hay selector de catálogo. Se conserva la API opcional de catálogo para clientes previos y las pruebas del Issue #27. Recepción/devolución, observaciones y PDF no cambian; no reserva stock ni bloquea el servicio. |
| Notificaciones | Personales y persistentes: publicaciones, cambios, inscripciones, asistencia, revisiones, cuentas, perfiles, graduación y reportes. Contador y lectura; comunicados oficiales solo por jefatura. No se envían correos ni WhatsApp. |
| Avisos de solicitudes | Jefatura recibe un aviso por cada validación pendiente: activación de cuenta, cambio de perfil, revisión de asistencia, justificación, reemplazo, reporte mensual y graduación elegible. El solicitante recibe el acuse y la decisión. **Ver detalle** abre y resalta la solicitud concreta; el historial de perfiles y revisiones conserva el enlace después de resolver. Los enlaces no amplían permisos: cada consulta vuelve a validar rol, autoría o encargo operativo. El responsable recibe solo el resumen operativo del reemplazo; no ve el sustento ni adjuntos de otros. |
| Formularios | Al guardar correctamente, el formulario se contrae y muestra un aviso de guardado; no se vuelve a abrir al refrescar datos. Un error mantiene el formulario abierto para corregir. La solicitud abierta desde una notificación se expande una vez; después queda bajo control de la persona. |

## Asistencia y horas

- Voluntario marca; responsable asignado o jefatura confirma/corrige después del servicio. Sin QR ni firma digital.
- Entrada dentro de la ventana y antes del fin; salida hasta una hora después. Sin marcas, se puede solicitar revisión.
- Tras **7 días desde el fin**, marcas completas pendientes se reconocen usando el horario y tolerancia. No se inventan horas: entrada sin salida queda INCOMPLETA con cero horas hasta revisión; sin entrada queda AUSENTE. Proceso idempotente cada 30 s y al consultar.
- Base programada; llegada anticipada/salida tardía no añaden horas. Salida anticipada reduce el crédito. Solo jefatura autoriza ajustes excepcionales con motivo.
- Hasta **10:00 minutos**, tolerancia sin penalización y base completa si se permanece hasta el final. Desde **10:01**, tiempo presente más deducción adicional de **2 × retraso**.
- Inasistencia: deducción de **2 × duración programada**. Una petición de revisión no la elimina.
- Ausencias y tardanzas: pueden justificarse desde el fin del servicio **hasta 72 horas después, inclusive**; pasado ese plazo el servidor rechaza nuevas solicitudes. El plazo no depende de cuándo se revisaron las marcas. No se exige un reemplazo retroactivo: el reemplazo obligatorio corresponde al trámite preventivo de cancelación, no a la ausencia ya ocurrida.
- Solo Jefatura resuelve justificaciones y consulta las de todos los usuarios; por defecto se muestran **PENDIENTES**, con accesos a APROBADAS y RECHAZADAS y filtros de estado, persona/servicio y mes. Los voluntarios, incluidos los responsables, ven únicamente sus propias solicitudes y sus estados; solo tienen el filtro de estado. Abrir un aviso operativo no concede acceso al listado de terceros.
- Aprobar una justificación de ausencia/tardanza elimina solo su deducción, sin acreditar tiempo no trabajado. Rechazar o revocar mantiene/restaura la penalización. Se conserva cada decisión con autor, fecha, motivo y deducción anterior/posterior; repetir aprobación no duplica devoluciones. Una solicitud preventiva de reemplazo no se aprueba sin asignación ni puede deshacerse por una segunda resolución: se evita cancelar/duplicar plazas silenciosamente.
- Evidencia opcional en ambos trámites: **foto del documento JPG/PNG de hasta 10 MB y 40 megapíxeles**. Se comprueba contenido real, no la extensión declarada. Guardada privadamente en PostgreSQL; solo solicitante y Jefatura pueden abrirla, con respuesta sin caché. El listado incluye únicamente si hay evidencia, nunca sus bytes. No se admiten PDF, documentos ejecutables ni otros tipos.
- **Jefatura no tiene meta de horas, penalizaciones ni obligación de justificar.** La exención se valida en backend; en el inicio ve un panel administrativo de horas e incidencias, no metas personales. Los registros históricos se conservan.

Ejemplo: servicio de 5 h, llegada 20 min tarde → **4 h 40 min reconocidas − 40 min de penalización = 4 h de saldo**. Justificación aprobada → **4 h 40 min**, no 5 h.

### Meses y graduación

- El servicio se atribuye al mes de su inicio. Saldo mensual admite negativos y domingos/feriados.
- Al comenzar el siguiente mes se prepara un aviso para jefatura: publicar entre los días **1 y 3**. Si se retrasa, se muestra aviso y se permite aprobar registrando la fecha real.
- Aprobar guarda una instantánea por usuario y notifica. No hay reapertura ni corrección de asistencias después. Se pueden presentar justificaciones de meses cerrados **si aún no pasaron las 72 horas**, y resolver solicitudes presentadas a tiempo aunque se revisen después: el cambio de deducción se añade en `monthly_report_adjustments`, ligado a la decisión, sin reescribir `monthly_report_rows`. Las solicitudes históricas no se borran. Saldo vigente = saldo original + ajustes. Se muestra el original y los ajustes en horas, reportes y PDF; el progreso acumulado usa el saldo ajustado del mes de origen, sin contarlo otra vez en el mes actual.
- **Revisar incidencias antes de aprobar:** los pendientes/incompletos se conservan como tales, sin inventar crédito. El cierre mensual tiene prioridad sobre el plazo de 7 días: una aprobación temprana congela también esos resultados. La interfaz lo advierte y pide confirmación.
- **Formación:** meta configurable de saldo neto, inicialmente **200 h solo si no había meta configurada**. Suma horas reconocidas menos penalizaciones de todos los años; **no se reinicia en enero**. Alcanzarla habilita la evaluación, no cambia automáticamente la condición. Jefatura ve el saldo neto histórico en Usuarios y aprueba indicando motivo; el servidor impide graduar sin saldo suficiente y guarda autor, fecha, saldo y meta usados. No basta haber trabajado 200 horas si las penalizaciones dejan el saldo por debajo.
- **Graduados:** seguimiento de saldo neto del año enero-diciembre, con meta anual configurable e inscripción anual independiente. No se borra el historial: se conserva por año en reportes. Al aprobar la graduación se registra el ciclo actual y se conservan los años de compromisos futuros ya aceptados, sin cancelar plazas únicamente por ese cambio. Ninguna meta aplica a Jefatura.
- **Reportes y PDF:** Jefatura elige mensual o anual para todos los voluntarios; cada voluntario descarga solo sus propios reportes mensual/anual desde Mis servicios. El anual muestra el total neto y los 12 meses, incluso meses en cero, con ajustes del mes original y pendientes. Un reporte anual es una agregación, no un nuevo cierre: respeta las instantáneas mensuales y sus ajustes sin duplicarlos. Los permisos del PDF de programación general no cambian.

## Migración sin borrar datos

`schema.sql` añade campos/tablas e índices. Mantiene cuentas, inscripciones, fotos, asistencias y códigos anteriores. No reclasifica inscripciones históricas ni reasigna responsables silenciosamente.

El ajuste de cupos añade `total_volunteers`: para servicios anteriores, suma una sola vez los cupos independientes previos y el responsable aceptado, sin bajar del número de personas ya aceptadas. Así no elimina compromisos ni inventa cuál era el total que se quería pedir. **Jefatura debe revisar el total de las convocatorias anteriores** si deseaba uno diferente. `graduate_slots` pasa a representar el mínimo de graduados dentro de ese total; las inscripciones FORMACION anteriores se conservan, sin ofrecer nuevas plazas exclusivas de formación. Las columnas antiguas se mantienen por compatibilidad de datos, pero ya no se suman para crear o editar convocatorias. Reiniciar no vuelve a aumentar el total.

La nueva penalización por tardanza no se cobra retroactivamente a asistencias anteriores a la política: `policy_version=1` conserva el cálculo previo; nuevas asistencias/revisiones usan 2. PENDIENTE/EXPIRADA antiguas pueden actualizarse desde el detalle, sin aprobación silenciosa. Los códigos nulos de borradores nuevos no se autogeneran en reinicios.

La versión 2.4 convierte RECLUTADOR a VOLUNTARIO y registra el rol anterior en `legacy_account_roles`. Mantiene IDs, correos, contraseñas, estado, graduación, responsables existentes e historial; no asigna automáticamente al creador como responsable. Los correos demo antiguos se mantienen para no romper accesos. El requisito de horas antiguo se copia una sola vez a la meta de formación; graduados empieza sin meta si jefatura aún no la configuró. Las metas editadas no se sobrescriben al reiniciar.

La versión 2.5 conserva `training_level` y `minimum_level` solo internamente por compatibilidad histórica: no se muestran en formularios/API ni afectan elegibilidad. Añade historial de responsables, autor/fecha de resolución, historial de decisiones y ajustes anexos a cierres. No inventa autores ni fechas para decisiones previas. Solo sustituye dos textos demo predeterminados de nivel por conocimientos específicos, sin cambiar requisitos personalizados. La migración es idempotente.

Reinicia el backend para aplicar. **No usar `docker compose down -v`** para actualizar.

La versión 2.6 reorganiza el código con DDD/Clean Architecture. La actualización del flujo de reemplazos amplía `justifications` con inscripción original, nombre propuesto, persona asignada y evidencia privada; permite ausencia nula únicamente para solicitudes preventivas y evita dos reemplazos pendientes para una inscripción. No se eliminan solicitudes ni decisiones anteriores. El antiguo cuerpo de cancelación requiere ahora `proposedReplacementName`, y enviar `replacementFor` a la autoinscripción se rechaza explícitamente. Al actualizar, ejecuta **`mvn clean test` una vez** para retirar clases compiladas de los paquetes antiguos; después inicia con `mvn spring-boot:run`. No borres la base de datos.

La versión **2.7** añade contadores/categorías de códigos, ciclos anuales e historial de graduación. La migración de ciclos se ejecuta **una sola vez** y registra con origen MIGRACION los años de inscripciones confirmadas de graduados existentes; no inventa una solicitud del usuario ni una fecha de consentimiento. No renueva años nuevos en reinicios posteriores. En una base nueva, el graduado registra su ciclo desde Perfil/Convocatorias. Horas, cierres, códigos y archivos anteriores se conservan. `proposedReplacementName` pasa a ser opcional: nulo indica que se solicita búsqueda de Jefatura; una propuesta presente no puede ser vacía. La confirmación de reemplazo sigue siendo obligatoria para aprobar.

La numeración automática empieza en las nuevas altas: no se infiere categoría de los históricos ni se renumeran sus códigos. Si un borrador anterior aún no tenía código, Jefatura elige categoría en **Editar datos** y al guardar se genera uno por única vez; no queda bloqueado por retirar la entrada manual de códigos.

### Compatibilidad con los Issues existentes

Se revisaron en lectura los Issues del repositorio, sin cambiar estado, asignación, etiquetas, ramas ni evidencia:

- **#17–18 Membresía:** se conservan `Volunteer`, `JdbcVolunteerRepository.profile`, `AccountsUseCases.phone` y sus contratos de comportamiento.
- **#20–21 Convocatorias:** `Callout.canAdmit`, `JdbcCalloutRepository.members` y `CalloutWorkflow.cancel` se mantienen. `members` sigue devolviendo únicamente confirmados: un titular cancelado no se suma otra vez junto a su reemplazo.
- **#23–24 Asistencia:** se mantienen cálculo, confirmación y preparación idempotente; reportes anuales agregan las reglas existentes.
- **#26–27 Materiales:** las invariantes, persistencia y resolución opcional del nombre por catálogo permanecen; solo la interfaz deja de ofrecer el selector.
- **#29–30 Administración:** `HourTargets` y lectura/escritura de las dos metas se mantienen; se aclara que formación es histórica y graduados anual.
- **#32–33 Comunicaciones:** se conservan anuncios, audiencias y lectura por destinatario. No se publican mensajes ni cierres automáticos de Issues.

Estas comprobaciones no sustituyen el trabajo TDD ni la evidencia/commits individuales exigidos por esos Issues; no se declaran completados automáticamente.

## Datos demo

**8 convocatorias, 10 perfiles y 17 inscripciones base.** En una base nueva María aparece como responsable donde estaba configurada; una migrada conserva sus asignaciones. Fechas fijas desde la primera carga, sin vaciar datos.

Contraseña demo: `convocatorias`. Principales: `voluntario@demo.pe`, `reclutador@demo.pe`, `jefatura@demo.pe`.

| Correo | Caso |
|---|---|
| `maria@demo.pe` | Voluntaria graduada |
| `diego@demo.pe` | Voluntario en formación |
| `lucia@demo.pe` | Voluntaria en formación |
| `reclutador.formacion@demo.pe` | Voluntario en formación, correo histórico |
| `reclutador2@demo.pe` | Voluntaria graduada, correo histórico |
| `jefatura2@demo.pe` | Otra jefatura |
| `inactivo@demo.pe` | Acceso denegado |

## Ejecución, pruebas y CI

- Inicio: [README.md](README.md). Docker Compose inicia PostgreSQL en `127.0.0.1:5433` y Adminer. Spring no carga `.env` de Compose; revisar `DB_URL/DB_USER/DB_PASSWORD` del entorno si hay conflictos. No borrar volúmenes para cambiar credenciales.
- Frontend: `npm ci` y `npm run build` comprueban TypeScript y Vite; `npm run dev` inicia desarrollo.
- Desde backend: `mvn clean test` ejecuta JUnit, Mockito y ArchUnit. JaCoCo genera `target/site/jacoco/index.html` y XML. Surefire genera `target/surefire-reports/`. Arquitectura/dominio/casos de uso funcionan sin base; las pruebas HTTP requieren `TEST_DB_URL`.
- Pruebas HTTP/DB solo se habilitan con `TEST_DB_URL` explícita hacia una **base aislada exclusiva de pruebas**. Crean datos ficticios; nunca apuntar a datos reales. `TEST_DB_USER/TEST_DB_PASSWORD` opcionales.
- Selenium sigue aquí como regresión existente, **no se ha empezado el Lab 6 multirepo**. Con API/frontend aislados iniciados: `mvn "-Dtest=BrowserTest" "-De2e.url=http://localhost:5173" test`. Chrome local o `-Dselenium.url=http://localhost:4444` remoto. Modifica cuentas demo y crea servicios de prueba.
- CI: JUnit/PostgreSQL efímero, cobertura como artefacto, construcción frontend y regresión Selenium. No usa volumen local.
- Base técnica Lab 5: dominios sin Spring; `operations/application/GraduationService`, puerto `GraduationSettings` y adaptador JDBC. Pruebas de invariantes de agregados, casos de uso con puertos falsos, arquitectura, transacciones y adaptadores. No se afirma haber aplicado TDD retroactivamente ni completado el trabajo de los seis integrantes.

## Límites

Prototipo local, no despliegue de producción. Antes de uso real: HTTPS, copias/restauración, política de retención y consentimiento de fotos/datos, almacenamiento de imágenes con capacidad suficiente y pruebas de carga. Fotos en PostgreSQL, sin vencimiento automático; no equivale a garantía contra pérdida de disco.

Fuera de esta entrega: Lab 6/multirepo, modificación de Projects/Issues externos, asignaciones formales de equipo, catálogo administrado de actividades (más allá de E/I/G), tiempo de traslado, recuperación de contraseña, catálogo/certificación automática de cursos y firma digital. Los cursos sí pueden declararse como requisitos de una convocatoria y verificarse personalmente. Sin QR, renovación mensual de cuentas ni suspensiones automáticas.
