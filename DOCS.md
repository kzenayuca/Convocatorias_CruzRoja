# Documentación esencial

## Estado real

- **Persistente:** cuentas y aprobación, cambios de perfil, convocatorias, inscripciones, asistencia, horas, justificaciones, solicitudes de materiales y notificaciones. Se reemplazaron los contadores ficticios y las inscripciones en `localStorage`; las inscripciones demo antiguas del navegador no se importan porque no fueron verificadas por el servidor.
- **Organización:** `auth` gestiona sesión y permisos vigentes; `api` agrupa los procesos JDBC por área; `domain/ServiceRules` conserva los cálculos puros. Frontend dividido en pantallas, API y componentes compartidos. Sin microservicios ni multirepo todavía.
- **Datos:** `schema.sql` amplía el esquema de forma aditiva y `data.sql` carga ejemplos sin duplicarlos. Compose solo inicia PostgreSQL y Adminer. Reinicia el backend al actualizar.

## Reglas acordadas

Las decisiones más recientes sustituyen la línea base inicial: 48 h, sin sanción por tardanza, sin suspensiones automáticas y materiales como formulario.

| Área | Regla principal |
|---|---|
| Cuenta | Cualquier persona solicita cuenta con nombres, apellidos, correo, contraseña, DNI, ocupación y teléfono. Login con correo y contraseña. Jefatura activa y asigna rol/nivel; graduarse no concede reclutamiento. |
| Perfil | Teléfono directo; otros datos se proponen y jefatura aprueba/rechaza. Los datos actuales siguen vigentes mientras se revisan. Correo y DNI únicos. No se baja el nivel a BASICO. |
| Permisos | Jefatura administra todas las convocatorias; un reclutador graduado, las que crea y tiene a cargo. Desactivación/suspensión manual impide acceso y cancela inscripciones futuras sin borrar historial. |
| Convocatorias | Tipos INGRESO y SERVICIO. Borrador → publicada → confirmada; cancelación y cierre sin borrado. Confirmar/abrir exige mínimo de confirmados. Edición futura permitida; con postulantes no se cambia horario, tipo ni requisitos. |
| Inscripciones | Solicitud PENDIENTE, aprobación por responsable autorizado o jefatura. Solo confirmar ocupa cupo. Nivel, aceptación de requisitos, duplicados, cruces de horario y capacidad se validan en servidor. |
| Plazos | Cinco días hábiles (lunes a viernes, sin calendario de feriados): después EXPIRADA. Si empieza el servicio sin aprobación, RECHAZADA. Revisión automática cada 30 s y antes de consultas. |
| Reemplazo | El reemplazante solicita usando el código de la inscripción original. Aprobación antes del inicio; el original pasa a REEMPLAZADA y el reemplazante ocupa su vacante. Debe cumplir los requisitos y estar activo. |
| Historial | Terminado el horario deja de aparecer entre disponibles. El voluntario ve servicios donde marcó asistencia; el reclutador también los que tuvo a cargo; jefatura ve todo. Mis inscripciones conserva resultados incluso sin participación. |
| Asistencia | Foto de equipo JPG/PNG (máximo 2 MB) antes de abrir. Voluntario marca, responsable confirma/corrige; confirmación autenticada, sin QR ni firma digital. Se abre dentro de la hora previa; marcas hasta una hora después del fin; fuera de esa ventana, revisión. |
| Horas | Tiempo dentro del horario autorizado, confirmado por responsable. Sin penalización por tardanza. Marcas incompletas no suman hasta revisión. Extensiones/ajustes manuales solo por jefatura, con observaciones; no se calcula tiempo extra automáticamente. |
| Penalización | Inasistencia de una inscripción confirmada/reemplazada: descuento provisional automático de 2 × duración programada. Una solicitud de revisión no elimina el descuento; una justificación aprobada sí. Sin duplicación de descuentos o devoluciones. |
| Justificación | Descripción, plazo ordinario de 48 h desde el fin, presentación tardía señalada como excepción. Jefatura resuelve/revisa. Aprobar inasistencia exige reemplazo confirmado antes del inicio. No acredita horas no realizadas. |
| Resumen | Cada mes comienza en cero, admite negativos e incluye domingos/feriados; conserva meses anteriores. El servicio se atribuye al mes de su inicio. Aprobaciones posteriores corrigen el mes original. Reporte imprimible por usuario. |
| Avisos | Persistentes y personales; lectura y contador. Solo jefatura publica comunicados para todos o por rol. Sin correo ni WhatsApp. |
| Materiales | Formulario por convocatoria: material, cantidad, especificaciones/observaciones. No se implementa inventario, préstamos ni stock. Las tablas antiguas se conservan para no borrar datos. |

Ejemplo: **24 − (2 × 3) = 18 h**; justificación aprobada → **24 h**. Con saldo inicial cero → **−6 h**. Una asistencia 13:50–15:10 en un servicio 14:00–15:00 reconoce **1 h**.

## Límites y pendientes

- Demo local: antes de operación real se necesitan HTTPS, respaldo/recuperación, política de datos personales y medición de carga. Cookies HttpOnly/SameSite y CSRF habilitados; permisos se recargan en cada petición.
- Recuperación/cambio de contraseña, adjuntos a justificaciones, firma digital, QR, lista de espera, calendario institucional de feriados y módulos de formación/cursos quedan fuera de esta entrega.
- Disponibilidad y habilidades específicas se declaran al aceptar requisitos y las revisa el responsable; solo nivel y cruces con servicios confirmados se verifican automáticamente. No se inventa una meta de horas para graduación.
- No se cancela automáticamente por mínimo insuficiente: el responsable no puede abrir/confirmar y decide cancelar. No hay renovación mensual ni suspensión automática.

## Datos demo

**8 convocatorias, 10 perfiles y 17 inscripciones base.** Casos: cupos libres, mínimo pendiente, último cupo, llena, confirmada, urgente, cancelada y finalizada. Las cuentas principales están en el README; las adicionales usan la misma contraseña: **`convocatorias`**.

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
| Dos aprobaciones compiten por una vacante | Solo una puede confirmarse. |
| Servicio empieza con solicitud pendiente | Solicitud rechazada con motivo. |
| Inasistencia y justificación aprobada con reemplazo | Descuento revertido una sola vez. |
| Marcas fuera del horario / incompletas | Se limita el cálculo; incompletas requieren revisión. |
| Navegador: login, detalle, perfil y logout | Datos persistentes y sesión invalidada. |
