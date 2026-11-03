# Documentación esencial

## Estado real

- **Con PostgreSQL:** login, cookie `JSESSIONID`, logout y consulta de convocatorias. Los cupos cuentan inscripciones confirmadas, no canceladas.
- **Solo demo:** las nuevas inscripciones se guardan en el navegador y no cambian los cupos de la base. Mis servicios muestra esas inscripciones locales; horas, avisos y reportes todavía no son datos operativos.
- **Organización:** backend con autenticación, consultas HTTP y reglas puras; frontend React con sus pantallas y estilos. `schema.sql` define tablas y `data.sql` carga ejemplos. Compose solo inicia PostgreSQL y Adminer.

## Reglas acordadas

Son el alcance a completar, no una lista de funciones ya terminadas.

| Área | Regla principal |
|---|---|
| Permisos | Jefatura administra roles y estados; jefatura y reclutadores graduados gestionan convocatorias. Graduarse no concede el rol de reclutador. |
| Convocatorias | Fecha, horario, lugar, mínimo, requisitos y contacto de emergencia obligatorios; máximo de cupos. Confirmar al alcanzar el mínimo activo; cancelar si no se alcanza al cierre y avisar a los inscritos. |
| Inscripciones | Automáticas, compatibles con el nivel, sin duplicados ni solapamientos. Ocultar servicios incompatibles con el horario; las canceladas no cuentan para el mínimo. |
| Asistencia | Entrada y salida con firmas, datos del voluntario y foto del equipo antes del servicio. Horas = salida − entrada, sin descontar pausas. Registros incompletos requieren evidencia y revisión. |
| Penalización | Ausencia sin justificación aprobada: descontar el doble de las horas programadas del total mensual. Sin suspensiones automáticas por número de ausencias. |
| Justificación | Plazo de 48 horas, constancia, aviso y reemplazo antes del servicio. Jefatura resuelve y admite revisión. Aprobación con reemplazo válido elimina el descuento; rechazo lo mantiene. |
| Reportes | Jefatura y reclutamiento ven el equipo; el voluntario, solo sus meses con actividad. Detallar servicios, fechas, horas, pendientes y descuentos; página imprimible como PDF. |
| Avisos | Solo web, filtros, urgentes destacados, contador y lectura al abrir. Incluir cambios de convocatorias, inscripciones, justificaciones, comunicados y registros incompletos. |
| Materiales | Catálogo estático para solicitudes por convocatoria, con receptor y devolución. No administrar inventario; faltantes en observaciones, sin bloquear el servicio. |

Ejemplo de horas netas: **56 realizadas − (2 × 3 de un servicio ausente) = 50 horas**. `ServiceRules` conserva el cálculo y el plazo, aún sin un flujo persistente completo.

## Pendientes de definir

- Plazo para cancelar un servicio por mínimo insuficiente y gestión de lista de espera/reemplazos.
- Cancelación individual hasta tres días hábiles antes: todavía es una propuesta.
- Digitalización de firmas/evidencia e historial de correcciones de jefatura, incluso después del cierre.
- Saldos negativos y ajustes de meses cerrados. Formación y beneficios quedan fuera por falta de información.

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
- Solo hay `npm run dev`. CI valida Maven/Compose e instalación del frontend, sin compilación, lint ni suite de pruebas. Quedan dos comprobaciones pequeñas de reglas. Un CI verde no certifica todas las funciones.
