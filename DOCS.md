# Documentación esencial

## Versión 2.2.0

Mismo diseño, nuevos puestos y aceptación automática. La coordinación (creador) se separa del encargado (graduado inscrito como responsable). No se añade QR, lista de espera ni suspensión automática.

## Estado real

- **Persistente:** cuentas y aprobación, cambios de perfil, convocatorias, inscripciones, asistencia, horas, justificaciones, solicitudes de materiales y notificaciones. Se reemplazaron los contadores ficticios y las inscripciones en `localStorage`; las inscripciones demo antiguas del navegador no se importan porque no fueron verificadas por el servidor.
- **Organización:** `auth` gestiona sesión y permisos vigentes; `api` agrupa los procesos JDBC por área, con `CalloutWorkflow` compartiendo permisos/cupos/transiciones; `domain/ServiceRules` conserva los cálculos puros. Frontend dividido en pantallas, API y componentes compartidos. Sin microservicios ni multirepo todavía.
- **Datos:** `schema.sql` amplía el esquema de forma aditiva y `data.sql` carga ejemplos sin duplicarlos. Compose solo inicia PostgreSQL y Adminer. Reinicia el backend al actualizar.

## Reglas acordadas

Estas decisiones sustituyen la línea base anterior: inscripción automática, puestos completos, tolerancia de 10 minutos y justificaciones en 48 h. Nunca se duplica una persona cancelada y su reemplazo en el conteo.

| Área | Regla principal |
|---|---|
| Cuenta | Cualquier persona solicita cuenta con nombres, apellidos, correo, contraseña, DNI, ocupación y teléfono. Login con correo y contraseña. Jefatura activa y asigna rol/nivel; graduarse no concede reclutamiento. |
| Perfil | Teléfono directo; otros datos se proponen y jefatura aprueba/rechaza. Los datos actuales siguen vigentes mientras se revisan. Correo y DNI únicos. No se baja el nivel a BASICO. |
| Permisos | Jefatura administra todas las convocatorias; reclutador graduado edita/publica/cancela las que crea. El graduado inscrito como RESPONSABLE opera su servicio, sin obtener rol de reclutador. Se revocan permisos en servidor al cambiar la cuenta. |
| Convocatorias | Código único, tipo de servicio, lugar, horario, horas programadas y requisitos. BORRADOR → ABIERTA → CERRADA (todos los puestos cubiertos) → FINALIZADA. CANCELADA conserva historial. Edición futura notifica a inscritos; con participantes no se cambia horario, tipo ni requisitos. |
| Puestos | SERVICIO: 1 responsable graduado, N socorristas y radioperador/piloto si se requieren. Todos los puestos declarados son obligatorios para confirmar. Formación solo ocupa socorrista; radioperador/piloto requieren graduación y habilitación que verifica jefatura. INGRESO solo tiene vacantes generales, sin asistencia de servicio. |
| Inscripciones | Aceptación automática con estado interno CONFIRMADA (interfaz: ACEPTADA), validando cuenta, nivel, puesto, declaraciones, duplicados y cruces. Se ocultan oportunidades solapadas; dos servicios contiguos sí se permiten. Jefatura puede rechazar con motivo y liberar el puesto. |
| Plazos | Se elimina la espera de cinco días. Al completar puestos se cierra automáticamente. Si vence el plazo sin completarse, se cancela y notifica, sin inasistencias ni penalización. Revisión cada 30 s y al consultar. Sin lista de espera. |
| Reemplazo | El titular solicita cancelación con motivo al menos 48 h antes del inicio. Conserva su plaza y obligación de asistir hasta que el reemplazante, compatible y sin cruce, sea aceptado por el encargado o jefatura antes del inicio. El original pasa a CANCELADA, sin inasistencia; solo cuenta el reemplazo. |
| Historial | Terminado el horario deja de aparecer entre disponibles. El voluntario ve servicios donde marcó asistencia; el reclutador también los que tuvo a cargo; jefatura ve todo. Mis inscripciones conserva resultados incluso sin participación. |
| Asistencia | Solo un servicio completo puede abrirse, con foto de equipo JPG/PNG (máximo 2 MB). Voluntario marca, encargado confirma/corrige después del horario; autenticación suficiente, sin QR ni firma digital. Se abre dentro de la hora previa; entrada hasta antes del fin y salida hasta una hora después. Fuera de ventana, revisión. |
| Horas | Base programada fija según el horario. Hasta 10:00 min de tardanza se reconoce la base completa si permanece hasta el fin. Por encima se resta el retraso completo; salida anticipada reduce el tiempo reconocido. Llegar antes o salir después no genera extras. Ajustes/extension manual solo por jefatura, con observaciones. |
| Penalización | Inasistencia: −2 × duración programada. Tardanza mayor de 10:00 min (el segundo 601 ya está en el minuto 11): −2 × retraso, adicional al tiempo no trabajado. Las marcas incompletas requieren revisión. Un servicio sin abrir no genera ausencias automáticamente. |
| Justificación | Descripción, plazo ordinario de 48 h desde el fin y presentación tardía excepcional. Jefatura resuelve/revisa. AUSENCIA requiere reemplazo confirmado antes del inicio; TARDANZA no requiere reemplazo. Aprobar revierte solo la deducción correspondiente, una sola vez. |
| Resumen | Cada mes comienza en cero, admite negativos e incluye domingos/feriados; conserva meses anteriores. El servicio se atribuye al mes de su inicio. Aprobaciones posteriores corrigen el mes original. Reporte imprimible por usuario. |
| Avisos | Persistentes y personales; lectura y contador. Solo jefatura publica comunicados para todos o por rol. Sin correo ni WhatsApp. |
| Materiales | Catálogo orientativo y formulario vinculado al servicio, solo para jefatura/encargado. Registro de cantidad, quién recibe/devuelve, fechas y pérdidas/daños en observaciones. Solicitud imprimible para almacén externo; no reserva ni modifica stock, no bloquea el servicio. |

Ejemplos: **24 − (2 × 3) = 18 h** por inasistencia; aprobada → **24 h**; desde cero → **−6 h**. Servicio de 5 h con 40 min de tardanza: **4 h 20 min reconocidas − 1 h 20 min de penalización = 3 h**; aprobada la justificación → **4 h 20 min**, no 5 h. Con 10 min exactos y salida al final: **5 h sin penalización**.

### Migración sin borrar datos

Los registros previos conservan IDs, inscripciones, asistencias y saldos históricos. Reciben código `LEG-…`; los cupos anteriores se conservan como socorristas, sin convertir a nadie en responsable sin su elección. El coordinador conserva gestión; el puesto de responsable debe cubrirlo un graduado. Las solicitudes PENDIENTE/EXPIRADA previas pueden actualizarse eligiendo un puesto desde el detalle; no se aprueban silenciosamente. La nueva penalización por tardanza no se cobra retroactivamente: los registros anteriores mantienen `policy_version=1`; los nuevos y las revisiones administrativas usan `2`.

## Límites y pendientes

- Demo local: antes de operación real se necesitan HTTPS, respaldo/recuperación, política de datos personales y medición de carga. Cookies HttpOnly/SameSite y CSRF habilitados; permisos se recargan en cada petición.
- Recuperación/cambio de contraseña, adjuntos a justificaciones, firma digital, QR y módulos de formación/cursos quedan fuera de esta entrega. No se necesita calendario de días hábiles: la aceptación es inmediata.
- Disponibilidad y habilidades específicas se declaran al aceptar requisitos y las revisa el responsable; solo nivel y cruces con servicios confirmados se verifican automáticamente. No se inventa una meta de horas para graduación.
- No hay renovación mensual ni suspensión automática. El envío al almacén se hace fuera del sistema usando la solicitud; no se integra con su inventario. Las habilitaciones se verifican administrativamente, sin conexión a registros externos.

## Datos demo

**8 convocatorias, 10 perfiles y 17 inscripciones base.** En una base nueva María ocupa el puesto de responsable en los ejemplos donde ya participaba; otros puestos siguen pendientes. En una base migrada no se reasignan participantes. Las cuentas principales están en el README; las adicionales usan **`convocatorias`**.

| Correo | Caso |
|---|---|
| `maria@demo.pe` | Voluntaria graduada, nivel avanzado |
| `diego@demo.pe` | Voluntario en formación, nivel intermedio |
| `lucia@demo.pe` | Voluntaria en formación, nivel básico |
| `reclutador.formacion@demo.pe` | Reclutador sin graduación |
| `reclutador2@demo.pe` | Otra reclutadora graduada |
| `jefatura2@demo.pe` | Otra cuenta de jefatura |
| `inactivo@demo.pe` | Login denegado por cuenta inactiva |

Datos ficticios, consultables en Adminer. Se cargan al iniciar el backend sin duplicar registros; las fechas se fijan en la primera carga. No hace falta vaciar la base.

## Ejecución y CI

- La configuración local tiene valores por defecto; `.env` es opcional. Credenciales solo para desarrollo.
- PostgreSQL de Docker usa `127.0.0.1:5433`; Adminer se conecta a `db:5432`. Así no interfiere con PostgreSQL de Windows.
- Si un puerto está ocupado, revisa la instancia anterior antes de abrir otra. Detén tus servidores con `Ctrl+C`.
- Un volumen antiguo conserva sus credenciales; cambiar `.env` no las modifica. No uses `docker compose down -v` si quieres conservar datos. Variables antiguas `DB_USER`, `DB_PASSWORD` o `DB_URL` en PowerShell pueden sobrescribir los valores del backend; Spring no carga el `.env` de Compose.
- Solo hay `npm run dev`, sin lint ni paso de empaquetado frontend. CI ejecuta JUnit, procesos con PostgreSQL efímero y Selenium con Chrome; no usa tu volumen local.

## Pruebas y criterios de aceptación

Desde `backend`, `mvn test` ejecuta las unitarias. Las pruebas HTTP requieren `TEST_DB_URL` apuntando **exclusivamente a una base de pruebas**; crean cuentas y servicios ficticios. Credenciales opcionales: `TEST_DB_USER` y `TEST_DB_PASSWORD`.

Con API y frontend de prueba iniciados, Selenium se ejecuta con JUnit:

```powershell
mvn "-Dtest=BrowserTest" "-De2e.url=http://localhost:5173" test
```

Necesita Chrome (Selenium Manager obtiene el controlador). Para un navegador remoto, añadir `"-Dselenium.url=http://localhost:4444"`. No ejecutar contra datos reales: el recorrido registra una cuenta y cambia un teléfono demo.

| Dado / cuando | Resultado verificado |
|---|---|
| Cuenta pendiente intenta entrar | Acceso rechazado; jefatura puede activarla. |
| Cuenta suspendida con sesión abierta | La próxima petición pierde acceso. |
| Dos inscripciones compiten por el mismo puesto | Solo una puede aceptarse. |
| Falta un puesto obligatorio al vencer el plazo | Servicio cancelado y avisos, sin penalizar. |
| Puestos completos y reemplazo solicitado | Original conserva plaza hasta aceptación; luego solo cuenta el reemplazo. |
| Tardanza de 10:00 / 10:01 / 11:00 minutos | Sin penalización / con penalización / doble del retraso. |
| Tardanza justificada | Se devuelve penalización, no tiempo no trabajado. |
| Inasistencia y justificación aprobada con reemplazo | Descuento revertido una sola vez. |
| Marcas fuera del horario / incompletas | Se limita el cálculo; incompletas requieren revisión. |
| Navegador: login, detalle, perfil y logout | Datos persistentes y sesión invalidada. |
| Navegador: crear, publicar e inscribirse | Puestos visibles y aceptación inmediata sin aprobación normal. |
