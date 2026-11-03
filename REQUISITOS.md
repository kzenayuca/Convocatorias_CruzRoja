# Requisitos acordados

Este documento conserva el alcance del sistema. No implica que todas las funciones estén implementadas: actualmente solo la autenticación está conectada a PostgreSQL.

## Acceso

Sesión con cookie; roles VOLUNTARIO, RECLUTADOR y JEFATURA. Jefatura cambia roles y estados de cuenta. La graduación no concede por sí sola permisos de reclutamiento.

## Convocatorias e inscripciones

- Jefatura y reclutadores graduados gestionan convocatorias.
- Datos obligatorios: fecha, horario, lugar, mínimo de participantes, requisitos y contacto de emergencia. Se contempla un máximo de cupos.
- Inscripción automática, sin duplicados ni horarios superpuestos. Las convocatorias que se superponen a una inscripción activa no se muestran al voluntario.
- Solo las inscripciones activas cuentan para el mínimo. El servicio se confirma al alcanzarlo; si no lo alcanza al cierre del plazo, se cancela. Falta acordar la fecha límite de evaluación.
- Cancelar un servicio anula las inscripciones y avisa a los participantes sin penalizarlos.
- Propuesta por confirmar: cancelación individual hasta tres días hábiles antes; después, justificación. Reemplazos/lista de espera y calendario de días hábiles pendientes de precisar.

## Asistencia y horas

- Registro de nombre, DNI, celular, entrada y salida con firmas. Encargado presenta foto del equipo antes del servicio. Falta confirmar cómo se digitalizan firmas y evidencia.
- Horas = salida menos entrada, sin descontar pausas. Un registro incompleto queda pendiente; se pide evidencia al voluntario y lo revisa jefatura.
- El voluntario consulta solo sus horas y puede reportar errores. Jefatura puede corregir registros, incluso de servicios cerrados. Historial de cambios pendiente de definir.
- Una ausencia sin justificación aprobada descuenta el doble de las horas programadas del total del mes. No se crean suspensiones automáticas por número de ausencias.
- Ejemplo: 56 horas realizadas menos dos veces un servicio ausente de 3 horas = 50 horas netas.
- Falta precisar si el saldo puede ser negativo y cómo se ajustan meses ya cerrados.

## Justificaciones

- Plazo: 48 horas. Aviso y reemplazo antes del inicio del servicio, con constancia del motivo.
- Motivos de salud, trabajo, estudio, viaje u otro respaldado por evidencia; jefatura decide su validez.
- Jefatura resuelve; se puede solicitar revisión. La aprobación y el reemplazo válido eliminan la deducción. Un rechazo mantiene el descuento.
- El cálculo y el plazo tienen reglas puras en `ServiceRules`; todavía no están conectados a un flujo persistente de asistencia/justificación.

## Reportes

Jefatura y reclutamiento consultan horas mensuales por voluntario, con detalle de servicio y fecha. Mostrar horas realizadas, pendientes de revisión y deducciones diferenciadas. El voluntario consulta únicamente sus meses con actividad. Página imprimible como PDF.

## Avisos y materiales

- Notificaciones solo en la web: convocatorias, inscripciones, justificaciones, comunicados y registros de asistencia incompletos. Filtros, urgentes destacados y contador; leída al abrir.
- Materiales: catálogo estático para pedir y asignar a convocatorias, registrando receptor y devolución. No administrar stock ni procesos del departamento de inventario. Faltantes se anotan en observaciones, no bloquean el servicio.
- Formación y beneficios quedan fuera de la interfaz hasta contar con información suficiente; no se fija un requisito de graduación de 120 horas.

## Organización mínima

Backend: autenticación, consultas HTTP y reglas puras. Frontend: una aplicación con componentes de pantalla y una hoja de estilos. SQL: un esquema y un archivo de datos iniciales, sin borrar tablas existentes. No se crean capas ni contratos vacíos.
