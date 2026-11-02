# Requisitos e historias BDD

## Descubrimiento

La lluvia de ideas partió de tareas, frustraciones y objetivos de voluntarios y jefatura. El diagrama de afinidad produjo tres grupos: **voluntario graduado** (equipo y estado), **jefatura/reclutamiento** (convocatorias, postulantes y mínimo) y **voluntario en formación** (elegibilidad, inscripción y duración). Se añadieron historias derivadas para completar inventario, asistencia, justificaciones y comunicación.

## Historias y trazabilidad

| Historia | Necesidad | Módulo y regla principal |
|---|---|---|
| H.1 | Ver voluntarios a cargo y su nivel | enrollments/users: solo confirmados |
| H.2 | Ver mínimo y estado del servicio | callouts: inscritos frente al mínimo |
| H.3 | Crear, publicar y cancelar convocatorias | callouts/users: `canManageCallouts()` |
| H.4 | Filtrar postulantes elegibles | enrollments: `EnrollmentPolicy` |
| H.5 | Confirmar únicamente con el mínimo | callouts/enrollments |
| H.6 | Ver convocatorias compatibles | callouts/users: `isCompatibleWith()` |
| H.7 | Consultar rápidamente la inscripción | enrollments/notifications |
| H.8 | Conocer inicio, fin y duración | callouts: fin posterior al inicio |
| H.9 | Asignar y prestar materiales | inventory: disponible = total − reservado |
| H.10 | Registrar asistencia y calcular horas | attendance: tolerancia 15 minutos |
| H.11 | Justificar una ausencia | justifications: máximo 72 horas |
| H.12 | Suspender por ausencias reiteradas | justifications: 3/90 días → 30 días |
| H.13 | Publicar comunicados oficiales | notifications: solo JEFATURA |
| H.14 | Acceder según rol y formación | users: email único, estado y nivel |

## Escenarios BDD esenciales

```gherkin
Característica: Elegibilidad de inscripción
  Escenario: Inscripción válida
    Dado una convocatoria PUBLICADA con cupo
    Y un voluntario compatible, no duplicado y no suspendido
    Cuando solicita inscribirse
    Entonces se crea una inscripción PENDIENTE

  Esquema del escenario: Rechazo de inscripción
    Dado estado <estado>, compatible <compatible>, cupo <cupo>, duplicado <duplicado>, suspendido <suspendido>
    Cuando solicita inscribirse
    Entonces se acepta solo si estado es PUBLICADA, compatible y cupo son verdaderos, y los otros son falsos
```

```gherkin
Característica: Confirmación de convocatoria
  Escenario: Se alcanzó el mínimo
    Dado una convocatoria PUBLICADA con mínimo 5 y 5 inscritos confirmados
    Cuando jefatura confirma el servicio
    Entonces queda CONFIRMADA y se publica CalloutConfirmed

  Escenario: No se alcanzó el mínimo
    Dado mínimo 5 y 4 inscritos
    Cuando se intenta confirmar
    Entonces permanece PUBLICADA y se informa cuántos faltan
```

```gherkin
Característica: Inventario
  Escenario: Préstamo y devolución
    Dado stock total 10 y reservado 2
    Cuando se prestan 5 unidades
    Entonces quedan 3 disponibles
    Cuando se devuelven
    Entonces el total no cambia y se liberan 5

  Escenario: Material perdido o dañado
    Dado un préstamo activo de 2 unidades
    Cuando se marca PERDIDO o DANADO
    Entonces el total disminuye en 2 y el préstamo no vuelve a cambiar
```

```gherkin
Característica: Asistencia
  Esquema del escenario: Puntualidad con inicio 08:00
    Cuando el check-in es <hora>
    Entonces el estado es <estado>
    Ejemplos:
      | hora  | estado   |
      | 07:55 | PRESENTE |
      | 08:15 | PRESENTE |
      | 08:16 | TARDE    |

  Escenario: Horas calculadas
    Dado check-in 08:00 y check-out 12:30
    Entonces las horas son 4.5
```

```gherkin
Característica: Ausencias y penalización
  Escenario: Justificación válida
    Dado una ausencia hace 48 horas
    Cuando el voluntario presenta un motivo
    Entonces se crea PENDIENTE

  Escenario: Suspensión automática
    Dado 3 ausencias injustificadas en 90 días
    Entonces se genera una suspensión de 30 días
```

```gherkin
Característica: Usuarios y comunicación
  Escenario: Gestión de convocatorias
    Dado un RECLUTADOR graduado o JEFATURA
    Entonces canManageCallouts es verdadero

  Escenario: Comunicado oficial
    Dado un usuario JEFATURA
    Cuando publica título, cuerpo y audiencia
    Entonces se genera el comunicado y sus notificaciones

  Escenario: Nivel de formación
    Dado un perfil INTERMEDIO
    Cuando JEFATURA asigna AVANZADO
    Entonces se actualiza
    Pero nunca puede bajar a BASICO
```

## Constantes

| Regla | Valor |
|---|---|
| Tolerancia de tardanza | 15 minutos |
| Plazo de justificación | 72 horas |
| Umbral de suspensión | 3 ausencias injustificadas en 90 días |
| Duración de suspensión | 30 días |
