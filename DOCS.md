# Documentación esencial · versión 2.4.0

## Estado real

Aplicación local persistente: cuentas, perfiles, convocatorias, inscripciones, programación, asistencia, horas, justificaciones, materiales y notificaciones. No se usa `localStorage` como fuente de inscripciones.

Se conserva el diseño: colores, tarjetas y barra lateral; se añaden espacios, páginas de detalle y secciones de gestión plegables. No se ha separado Selenium en otro repositorio ni implementado el Lab 6.

## Reglas y permisos

| Área | Comportamiento |
|---|---|
| Cuentas | Solo roles VOLUNTARIO y JEFATURA. Jefatura activa, asigna nivel, graduación y habilitaciones. Los permisos se recargan en cada petición. Ser graduado no otorga privilegios globales; RESPONSABLE es una función por servicio. RECLUTADOR ya no puede asignarse como rol. |
| Perfil | Primero ficha de lectura; **Modificar** abre los formularios. Teléfono de cambio directo, otros datos requieren aprobación. Correo/DNI únicos. No se baja a nivel BASICO. |
| Convocatorias | Una página por convocatoria. Jefatura crea; el responsable graduado asignado puede gestionar únicamente su servicio, asistencia, materiales y fotos. Haber creado un servicio o ser un antiguo reclutador no otorga permisos. Jefatura gestiona todos los servicios. |
| Código | Manual, solo jefatura. Se admite borrador sin código; no se publica hasta asignarlo. El responsable asignado puede editar los demás datos del servicio, no cambiar su código. Unicidad sin distinguir mayúsculas; los anteriores quedan reservados. Administración muestra los 100 más recientes filtrables por tipo. |
| Tipo de servicio | Texto libre en el formulario: Guardia, Apoyo a Bienestar, etc. No hay catálogo obligatorio de tipos. |
| Cupos | Un único **total de voluntarios**, incluidos responsable, piloto y radioperador. “Voluntario” admite graduados y personas en formación; “voluntario graduado” solo graduados. Se configura un **mínimo de graduados dentro del total**, no plazas adicionales. Total 4, mínimo 1 graduado = 4 personas, no 5. Total 1, mínimo 1 = una única persona graduada. No hay una nueva categoría exclusiva para formación. |
| Funciones | Piloto/radio son funciones obligatorias cuando se solicitan, dentro del total, con graduación y habilitación verificada por jefatura. Una persona ocupa un solo puesto y cuenta una sola vez; su graduación puede satisfacer simultáneamente el mínimo de graduados. Se reserva espacio para funciones aún vacantes y graduados faltantes; ni una inscripción ni un reemplazo pueden consumir ese espacio. |
| Responsable | Opcional, solo un usuario VOLUNTARIO graduado activo, con nivel mínimo y sin cruce, asignado por jefatura; **consume una plaza del total** y cuenta para el mínimo de graduados. Se puede convertir a un voluntario general ya aceptado sin duplicarlo; no al piloto/radioperador. No se añade un responsable externo con el equipo lleno. Retirarlo libera la plaza y sus permisos de gestión. Sin responsable, opera jefatura. |
| Inscripción | Aceptación automática de una cuenta validada si cumple nivel, puesto, requisitos declarados, horario y cupo. Estado interno CONFIRMADA, mostrado como ACEPTADA. Requisitos personales los verifica jefatura; no se afirma que el sistema certifique documentos. |
| Inscripción directa | Solo jefatura, desde el detalle de una convocatoria publicada, antes del inicio, de la apertura operativa y del plazo de inscripción. Puede seleccionar puesto y persona de una lista elegible y debe confirmar la verificación de los requisitos declarados. El servidor vuelve a comprobar cuenta activa, formación, graduación, habilitaciones, cupo inclusivo, reservas, duplicados y horario en la misma transacción que la autoinscripción. Queda CONFIRMADA, notifica al inscrito y aparece en su agenda. Se guardan `assigned_by`, `assigned_at` y `requirements_accepted`; no se inventa un reemplazo ni se revierte una inscripción rechazada/cancelada. El responsable se asigna en su sección específica. |
| Horarios | Se controla el cruce de la **misma persona**, no entre actividades. Fin 13:00/inicio 13:00 permitido. Sin margen de traslado. Servicios solapados con equipos diferentes permitidos. |
| Cobertura | ABIERTA si faltan personas, graduados mínimos o funciones requeridas; CERRADA solo cuando se cumplen todos y el total es exacto. Un responsable asignado cuenta como persona; no es obligatorio asignarlo para cerrar. Sin cobertura al vencer el plazo: CANCELADA, avisos, sin inasistencia. Sin lista de espera. |
| Ejecución | PROGRAMADO, EN CURSO, FINALIZADO o CANCELADO; separado de cobertura. Abre con puestos completos, desde una hora antes; cierra después del fin programado. Foto no obligatoria. INGRESO no registra horas de servicio. |
| Programación | Voluntarios, incluidos los responsables asignados, solo ven servicios con inscripción CONFIRMADA propia. Jefatura ve todo. Selección diaria, servicios que atraviesan medianoche, enlaces y PDF con los mismos permisos. No se incluyen DNI ni teléfonos en los PDF. |
| Equipos | Nómina por puesto para jefatura y gestores operativos autorizados del servicio, no para participantes ordinarios. Solo jefatura consulta todos los usuarios y reportes generales. |
| Reemplazo | Titular solicita con 48 h de anticipación y conserva obligación/plaza hasta confirmación del reemplazo compatible, mismo puesto, por encargado o jefatura antes del inicio. Original CANCELADA sin inasistencia. |
| Cambios | Antes del inicio se puede cambiar horario/lugar/requisitos y notificar a inscritos. Un cambio de horario no puede crear cruces; con participantes no se cambia tipo de convocatoria ni nivel mínimo. No se eliminan plazas ocupadas. |
| Fotos | JPG/PNG, máximo 50 MB y 40 megapíxeles, sin vencimiento automático. Solo jefatura o el responsable graduado asignado pueden subir; ser participante, creador o antiguo reclutador no basta. Cuentas activas pueden verlas. En perfil aparecen fotos de servicios con entrada registrada. Se muestra una foto actual por servicio y se conserva la anterior en un archivo histórico de la base. |
| Materiales | Solicitud de almacén externo, no inventario. Catálogo orientativo y texto libre, cantidades, recepción/devolución, responsable y observaciones. Vista previa y PDF después de solicitar. No reserva stock ni bloquea el servicio. |
| Notificaciones | Personales y persistentes: publicaciones, cambios, inscripciones, asistencia y reportes. Contador y lectura; comunicados oficiales solo por jefatura. No se envían correos ni WhatsApp. |

## Asistencia y horas

- Voluntario marca; responsable asignado o jefatura confirma/corrige después del servicio. Sin QR ni firma digital.
- Entrada dentro de la ventana y antes del fin; salida hasta una hora después. Sin marcas, se puede solicitar revisión.
- Tras **7 días desde el fin**, marcas completas pendientes se reconocen usando el horario y tolerancia. No se inventan horas: entrada sin salida queda INCOMPLETA con cero horas hasta revisión; sin entrada queda AUSENTE. Proceso idempotente cada 30 s y al consultar.
- Base programada; llegada anticipada/salida tardía no añaden horas. Salida anticipada reduce el crédito. Solo jefatura autoriza ajustes excepcionales con motivo.
- Hasta **10:00 minutos**, tolerancia sin penalización y base completa si se permanece hasta el final. Desde **10:01**, tiempo presente más deducción adicional de **2 × retraso**.
- Inasistencia: deducción de **2 × duración programada**. Una petición de revisión no la elimina.
- Justificaciones: plazo ordinario **48 horas** desde el fin; fuera de plazo se señala excepción. Jefatura resuelve. TARDANZA no requiere reemplazo; AUSENCIA exige reemplazo histórico aceptado antes del inicio. Aprobar elimina solo la deducción, sin acreditar tiempo no trabajado. Se puede revisar antes del cierre mensual sin duplicar devoluciones.

Ejemplo: servicio de 5 h, llegada 20 min tarde → **4 h 40 min reconocidas − 40 min de penalización = 4 h de saldo**. Justificación aprobada → **4 h 40 min**, no 5 h.

### Meses y graduación

- El servicio se atribuye al mes de su inicio. Saldo mensual admite negativos y domingos/feriados.
- Al comenzar el siguiente mes se prepara un aviso para jefatura: publicar entre los días **1 y 3**. Si se retrasa, se muestra aviso y se permite aprobar registrando la fecha real.
- Aprobar guarda una instantánea por usuario y notifica su publicación. No hay reapertura ni corrección posterior, tampoco por procesos automáticos o justificaciones.
- **Revisar incidencias antes de aprobar:** los pendientes/incompletos se conservan como tales, sin inventar crédito. El cierre mensual tiene prioridad sobre el plazo de 7 días: una aprobación temprana congela también esos resultados. La interfaz lo advierte y pide confirmación.
- Jefatura configura en **Administración** una meta general para FORMACION y otra para GRADUADO. Son metas de **saldo acumulado**, no mensuales ni individuales; el indicador usa la condición actual (`graduated`), independientemente del rol de acceso. Perfil y Mis servicios muestran meta, saldo, faltante, deuda y detalle mensual. Cambiar las metas o la condición selecciona el objetivo correspondiente sin reiniciar horas, recalcular asistencias ni modificar cierres mensuales. Alcanzar la meta no gradúa automáticamente: jefatura valida la graduación.

## Migración sin borrar datos

`schema.sql` añade campos/tablas e índices. Mantiene cuentas, inscripciones, fotos, asistencias y códigos anteriores. No reclasifica inscripciones históricas ni reasigna responsables silenciosamente.

El ajuste de cupos añade `total_volunteers`: para servicios anteriores, suma una sola vez los cupos independientes previos y el responsable aceptado, sin bajar del número de personas ya aceptadas. Así no elimina compromisos ni inventa cuál era el total que se quería pedir. **Jefatura debe revisar el total de las convocatorias anteriores** si deseaba uno diferente. `graduate_slots` pasa a representar el mínimo de graduados dentro de ese total; las inscripciones FORMACION anteriores se conservan, sin ofrecer nuevas plazas exclusivas de formación. Las columnas antiguas se mantienen por compatibilidad de datos, pero ya no se suman para crear o editar convocatorias. Reiniciar no vuelve a aumentar el total.

La nueva penalización por tardanza no se cobra retroactivamente a asistencias anteriores a la política: `policy_version=1` conserva el cálculo previo; nuevas asistencias/revisiones usan 2. PENDIENTE/EXPIRADA antiguas pueden actualizarse desde el detalle, sin aprobación silenciosa. Los códigos nulos de borradores nuevos no se autogeneran en reinicios.

La versión 2.4 convierte RECLUTADOR a VOLUNTARIO y registra el rol anterior en `legacy_account_roles`. Mantiene IDs, correos, contraseñas, estado, graduación, responsables existentes e historial; no asigna automáticamente al creador como responsable. Los correos demo antiguos se mantienen para no romper accesos. El requisito de horas antiguo se copia una sola vez a la meta de formación; graduados empieza sin meta si jefatura aún no la configuró. Las metas editadas no se sobrescriben al reiniciar.

Reinicia el backend para aplicar. **No usar `docker compose down -v`** para actualizar.

## Datos demo

**8 convocatorias, 10 perfiles y 17 inscripciones base.** En una base nueva María aparece como responsable donde estaba configurada; una migrada conserva sus asignaciones. Fechas fijas desde la primera carga, sin vaciar datos.

Contraseña demo: `convocatorias`. Principales: `voluntario@demo.pe`, `reclutador@demo.pe`, `jefatura@demo.pe`.

| Correo | Caso |
|---|---|
| `maria@demo.pe` | Voluntaria graduada avanzada |
| `diego@demo.pe` | En formación intermedia |
| `lucia@demo.pe` | En formación básica |
| `reclutador.formacion@demo.pe` | Voluntario en formación, correo histórico |
| `reclutador2@demo.pe` | Voluntaria graduada, correo histórico |
| `jefatura2@demo.pe` | Otra jefatura |
| `inactivo@demo.pe` | Acceso denegado |

## Ejecución, pruebas y CI

- Inicio: [README.md](README.md). Docker Compose inicia PostgreSQL en `127.0.0.1:5433` y Adminer. Spring no carga `.env` de Compose; revisar `DB_URL/DB_USER/DB_PASSWORD` del entorno si hay conflictos. No borrar volúmenes para cambiar credenciales.
- Frontend: `npm ci` y `npm run build` comprueban TypeScript y Vite; `npm run dev` inicia desarrollo.
- Desde backend: `mvn test` ejecuta JUnit y Mockito. JaCoCo genera `target/site/jacoco/index.html` y XML. Surefire genera `target/surefire-reports/`.
- Pruebas HTTP/DB solo se habilitan con `TEST_DB_URL` explícita hacia una **base aislada exclusiva de pruebas**. Crean datos ficticios; nunca apuntar a datos reales. `TEST_DB_USER/TEST_DB_PASSWORD` opcionales.
- Selenium sigue aquí como regresión existente, **no se ha empezado el Lab 6 multirepo**. Con API/frontend aislados iniciados: `mvn "-Dtest=BrowserTest" "-De2e.url=http://localhost:5173" test`. Chrome local o `-Dselenium.url=http://localhost:4444` remoto. Modifica cuentas demo y crea servicios de prueba.
- CI: JUnit/PostgreSQL efímero, cobertura como artefacto, construcción frontend y regresión Selenium. No usa volumen local.
- Base técnica Lab 5: `domain` sin Spring; caso de uso `application/GraduationService`; puerto `GraduationSettings` y adaptador JDBC. Pruebas de invariantes, Mockito y del adaptador. No se afirma haber aplicado TDD retroactivamente ni completado el trabajo de los seis integrantes.

## Límites

Prototipo local, no despliegue de producción. Antes de uso real: HTTPS, copias/restauración, política de retención y consentimiento de fotos/datos, almacenamiento de imágenes con capacidad suficiente y pruebas de carga. Fotos en PostgreSQL, sin vencimiento automático; no equivale a garantía contra pérdida de disco.

Fuera de esta entrega: Lab 6/multirepo, Projects/Issues externos, asignaciones formales de equipo, automatización de prefijos, catálogo administrado de tipos, tiempo de traslado, recuperación de contraseña, cursos, adjuntos a justificaciones y firma digital. Sin QR, renovación mensual de cuentas ni suspensiones automáticas.
