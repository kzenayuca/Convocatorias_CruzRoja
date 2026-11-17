# Convenciones, estilos y Clean Code — prácticas 9, 10 y 11

Convenciones y ejemplos de la implementación. Las evidencias de SonarLint y la evaluación
individual requieren ejecución real; no se deducen de la existencia de esta documentación.

## Convenciones (práctica 9)

| Práctica | Fragmento/ubicación real |
|---|---|
| Paquetes en minúsculas | `operations.domain`, `operations.infrastructure.persistence` |
| Tipos PascalCase | `Enrollment`, `EnrollmentStatus`, `CalloutEntity` |
| Métodos camelCase con intención | `cancelForReplacement`, `removeResponsibility` |
| Constantes explícitas | `ServiceRules.JUSTIFICATION_DEADLINE_HOURS` |
| Formato uniforme | `mvn -Pquality spotless:check` |
| Alcance reducido | Variables locales declaradas antes de su utilización en `EnrollmentUseCases` |

```java
public Enrollment cancelForReplacement() {
    valid(status == EnrollmentStatus.CONFIRMADA, "Solo se reemplaza una inscripción confirmada.");
    return withStatus(EnrollmentStatus.CANCELADA);
}
```

Fuente: `operations/domain/Enrollment.java`. El nombre expresa el efecto sin consultar SQL.

## Cuatro estilos (práctica 10)

La clasificación debe contrastarse con las definiciones del aula; no se cuentan MVC o SOLID como
sustitutos de los estilos solicitados.

### Things

`Enrollment` reúne identidad y decisiones (`requireNewRequest`, `assignResponsible`,
`cancelForReplacement`). `TeamCapacity` reúne datos de composición y `fits`/`complete`.
No son simples contenedores de datos con setters.

### Cookbook

`EnrollmentUseCases.enrollFor` expresa una secuencia: validar el puesto, bloquear convocatoria,
comprobar requisitos, bloquear participante, comprobar elegibilidad, conservar reservas,
guardar, sincronizar y notificar. La secuencia es importante para consistencia y bloqueo.
El caso de uso coordina, sin absorber el cálculo de cupos del dominio.

### Pipeline

En `EnrollmentUseCases.candidates`, cada etapa filtra una condición independiente:

```java
return enrollments.candidates(id).stream()
        .filter(user -> workflow.qualified(callout, id(user, "id"), position))
        .filter(user -> !workflow.overlap(callout, id(user, "id")))
        .filter(user -> workflow.canAdmit(callout, id(user, "id"), position, null))
        .toList();
```

Su limitación actual es que las proyecciones siguen usando mapas y las consultas por candidato
pueden requerir optimización por lote al crecer el volumen. No se declara rendimiento ilimitado.

### Error/Exception Handling

`Position.parse` transforma una entrada desconocida en un error con significado para el usuario:

```java
try {
    return valueOf(value);
} catch (IllegalArgumentException error) {
    throw new IllegalArgumentException("Puesto no válido.", error);
}
```

`ApiErrorHandler` adapta errores del núcleo a HTTP. Las excepciones técnicas no se convierten
en resultados exitosos. La autorización permanece en el backend.

## Siete categorías Clean Code (práctica 11)

| Categoría | Aplicación y fragmento identificable |
|---|---|
| Nombres | `Enrollment.cancelForReplacement()` distingue cancelación por sustitución |
| Funciones | `Position.requireSelfEnrollment()` valida una intención concreta |
| Comentarios | `ManagedEntities.lock()` explica el refresco durante coexistencia ORM/SQL |
| Estructura | Formato AOSP, espacios y eliminación de imports no usados mediante Spotless |
| Objetos/datos | `Enrollment` mantiene estado privado; DTO y entidades ORM no son el agregado |
| Errores | `ManagedEntities.lock()` lanza `NotFound`, no devuelve una entidad nula |
| Clases | `EnrollmentController` adapta HTTP; `EnrollmentUseCases` coordina admisión |

No se exige un comentario por método: comentar el porqué de bloqueos, compatibilidad o decisiones,
no repetir el nombre de una asignación. No borrar migraciones ni contratos históricos como supuesto
código muerto sin comprobar sus consumidores.

## SOLID y principios adicionales

- SRP: separar inscripción del controlador general de servicios.
- OCP: adaptadores detrás de contratos; no introducir jerarquías sin variación real.
- LSP: conservar rutas, estados, permisos, errores y transacciones al sustituir JDBC por ORM.
- ISP: separar contratos cuando existan consumidores distintos, evitando interfaces vacías.
- DIP: ningún modelo del dominio importa Hibernate ni Jakarta Persistence; ArchUnit lo verifica.
- Menor sorpresa: distinguir lectura de mantenimiento. Las consultas antiguas aún llaman a refresh;
  su separación completa queda pendiente porque cambiarlo afecta la expiración visible.
- Demeter: invocar operaciones del agregado en lugar de modificar su estructura desde HTTP.
- Boy Scout: corregir el área intervenida y protegerla con pruebas; no reescribir funcionalidades ajenas.

## FIRST

- Fast: dominio sin servidor/DB; integración y navegador separados por configuración.
- Independent: fixtures por prueba, rollback en `JpaPersistenceTest` y UUID distintos.
- Repeatable: fecha fija en pruebas nuevas y PostgreSQL exclusivo.
- Self-validating: aserciones de estado, cantidades, persistencia y errores; captura no es aserción.
- Timely: añadir comprobaciones junto con cada modificación. No se afirma TDD retroactivo.

## SonarLint / SonarQube for IDE: pendiente de ejecución en VS Code

1. Instalar la extensión vigente de SonarSource y registrar su versión.
2. Abrir el proyecto con Java 17 e importar Maven.
3. Construir y analizar los archivos modificados.
4. Revisar cada hallazgo y corregir riesgos reales, especialmente Blocker/Critical.
5. Reejecutar pruebas y análisis; conservar alcance, fecha, hallazgo y resolución.

Plantilla de reporte: `archivo | regla | severidad | problema | corrección | resultado posterior`.
Spotless y ArchUnit se ejecutan automáticamente, pero **no sustituyen un reporte real de SonarLint**.
No se declara ausencia de vulnerabilidades ni se inventa un antes/después.
