# Práctica 07 — ejecución y entrega verificable

## Alcance y estado

El docente acepta `main` como equivalente de `master` y requiere Jenkins.
Esta entrega integra la parte de Lizeth; no declara terminadas las tareas de los
otros integrantes. La autoría se conserva en los commits recuperados.

El issue de integración es [#96](https://github.com/kzenayuca/Convocatorias_CruzRoja/issues/96).
La configuración Jenkins está en la PR
[#95](https://github.com/kzenayuca/Convocatorias_CruzRoja/pull/95).
Las PR #78–#82 se integraron entre ramas de trabajo, no directamente en development:
que un issue esté cerrado no demuestra que su cambio esté en la rama entregada.

No se afirma que Jenkins/Sonar hayan sido ejecutados por la existencia de sus
archivos. Deben adjuntarse los resultados de ejecuciones reales.

## 1. Herramientas (PowerShell de Windows)

Usar PowerShell evita mezclar instalaciones Windows con el Node antiguo de WSL.
Abrir Docker Desktop, activar contenedores Linux y comprobar:

```powershell
git --version
java -version
python --version
node --version
npm --version
docker version
sonar-scanner.bat --version
```

Java: JDK 21 para Jenkins actual; la aplicación compila con release 17.
Node: 22.12 o posterior compatible. Python: 3.10 o posterior.
SonarQube/Scanner: confirmar las versiones aceptadas por el docente antes de obtener
el antes y el después. La práctica cita 9.9.7 y Scanner 5–6.2.1; el CI existente
documenta Sonar 25.1 y Scanner 7.2. No comparar servidores con versiones distintas
ni degradar el servidor actual sobre sus datos. Un entorno antiguo, si se exige,
debe ser separado y solo local.

Instalar **SonarQube for IDE** en VS Code y registrar versión, archivos analizados
y hallazgos. La extensión no sustituye el reporte del servidor.

## 2. Código que se entregará

Antes de cambiar de rama comprobar `git status`. No descartar cambios locales.
Revisar las PR de integración y comprobar CI antes de fusionarlas a development.
Una vez integradas:

```powershell
git fetch origin
git switch development
git pull --ff-only origin development
git rev-parse HEAD
```

Registrar el SHA completo en el informe. No modificar main directamente.
Para probar esta rama antes de integrar, usar
`refactor/lizeth-practica07-entrega` en lugar de development.

## 3. Pruebas frontend

Desde la raíz del repositorio:

```powershell
cd frontend
npm ci
npm test -- --maxWorkers=1
npm run build
cd ..
```

Un worker reduce consumo y problemas de arranque de procesos en WSL sobre NTFS.
No rebaja aserciones ni omite archivos. Guardar salida, versión de Node y SHA.
No confundir build exitoso con pruebas aprobadas.

Casos de Lizeth: HTTP/CSRF inválido, JSON inválido, sesión expirada, fallos y reintento
de notificaciones, exclusión de peticiones simultáneas, desmontaje del hook y
fronteras temporales de asistencia. Se mantienen las pruebas de DetailPage del equipo.

## 4. SonarQube local

Iniciar la instalación existente de SonarQube con su procedimiento habitual.
No hay servicio Sonar en el compose de la aplicación. No arrancar dos servidores
sobre el puerto 9000 ni borrar volúmenes para solucionar fallos.

```powershell
Invoke-RestMethod http://localhost:9000/api/system/status
```

Esperar `UP`. Abrir http://localhost:9000/dashboard?id=convocatorias.
Crear el proyecto `convocatorias` si no existe y generar un token con permiso
de análisis y consulta. No copiarlo al repositorio ni a capturas.

El alcance del Scanner actual es **backend Java**. Las pruebas frontend y la
revisión manual se reportan aparte, sin atribuirles cobertura JaCoCo.

## 5. Jenkins obligatorio

Seguir [JENKINS.md](JENKINS.md) para instalación detallada.
Guardar jenkins.war fuera del repositorio y, desde su carpeta:

```powershell
$env:JENKINS_HOME = "$HOME\jenkins-cruzroja"
java -jar .\jenkins.war --httpPort=8090 --httpListenAddress=127.0.0.1
```

1. Abrir http://localhost:8090, desbloquear con la contraseña indicada por Jenkins
   y crear administrador propio.
2. Instalar Pipeline, Git, Credentials Binding, Timestamper, JUnit,
   SonarQube Scanner y Pipeline: Stage View.
3. Manage Jenkins → Tools: JDK llamado `JAVA`, Scanner CLI llamado `sonar-scanner`.
4. Credentials → Secret text: guardar el token con ID `sonarqube-token`.
5. Crear Pipeline `Convocatorias_Backend`, definición **Pipeline script from SCM**.
6. SCM Git, URL `https://github.com/kzenayuca/Convocatorias_CruzRoja.git`.
7. Branch `*/development` después de integrar; para validación previa usar
   `*/refactor/lizeth-practica07-entrega`. Script Path: `Jenkinsfile`.
8. Build Now. Revisar cada etapa y Console Output, no solo el color del build.
9. Descargar artefactos: pruebas, JaCoCo, sonar-evidence y JAR cuando esté disponible.
10. Guardar URL y número del build, SHA, Stage View y Console Output.

El usuario que ejecuta Jenkins debe poder usar Docker, Java y Python.
El pipeline crea PostgreSQL desechable, no la base personal. El smoke test usa
8083 y detiene el JAR al finalizar: no es un despliegue permanente.
9000 corresponde a Sonar y 8090 a Jenkins. `localhost` es el equipo del agente;
un agente en contenedor necesita una dirección accesible hacia el servidor Sonar.
Un Gate rechazado se investiga, no se oculta relajando reglas.

## 6. Reporte antes/después

1. Elegir commit A anterior al cambio concreto y commit B posterior.
2. Guardar reportes iniciales existentes. Si faltan, analizar A en otro checkout
   controlado y declarar el reporte **reconstruido posteriormente**.
3. Usar el mismo servidor/versiones, perfil, exclusiones y alcance para A y B.
4. Construir y ejecutar pruebas del commit exacto; no reutilizar bytecode ni XML
   de otro commit. Nunca resetear el trabajo actual para reconstruir A.
5. Descargar métricas, issues, Gate y hotspots del análisis exacto y guardar SHA.
6. Comparar código global con código global; distinguirlo de código nuevo.
7. Revisar hotspots manualmente: no son vulnerabilidades confirmadas por defecto.

No inventar un ciclo RED–GREEN retrospectivo. Para refactoring puro, las pruebas
de caracterización deben pasar antes y después. Para un bug, la prueba reproduce
el defecto primero. Un fallo de integración no demuestra TDD original.

| Indicador | Antes (SHA A) | Después (SHA B) | Evidencia |
|---|---|---|---|
| Versión Sonar / Scanner / perfil | Pendiente | Pendiente | Registro de herramientas |
| Issues por tipo y regla | Pendiente | Pendiente | CSV/JSON |
| Cobertura backend | Pendiente | Pendiente | JaCoCo + métricas |
| Duplicación / deuda estimada | Pendiente | Pendiente | Métricas Sonar |
| Ejecutadas / fallidas / omitidas | Pendiente | Pendiente | Surefire |
| Pruebas frontend | Pendiente | Pendiente | Vitest |
| Gate y condiciones | Pendiente | Pendiente | quality-gate.json |

Los campos pendientes requieren ejecución, no se rellenan con cifras supuestas.

## 7. Inventario individual de Lizeth

| Smell/bug/riesgo | Componente | Responsable | Prioridad | Estrategia | Categoría | Descripción/issue |
|---|---|---|---|---|---|---|
| R04: CSRF sin validar | api.ts | Lizeth | Alta | Extract Method + validación | Confiabilidad, G26/T5 | #60: estado y estructura antes de escribir |
| M13: respuestas any | api.ts | Lizeth | Media | Contratos tipados | G4/G26 | #66: unknown por defecto; Row legado documentado, no eliminado |
| R03: rechazo sin manejar | ActivityPages.tsx | Lizeth | Media | Extract Method | Confiabilidad | #73: error local y reintento |
| M17: ciclo asíncrono duplicado | useRequest.ts, components.tsx | Lizeth | Media | Extract Function/hook | G5 | #64: parte compartida; no declarar cero duplicación |
| M16: plazos literales | attendanceRules.ts | Lizeth | Media | Constantes + Extract Function | G25/G33 | #75: probar fronteras |
| M19: infraestructura de pruebas | frontend/src/test | Lizeth | Media | Pruebas de caracterización | T1 | #67: no equivale a cobertura completa |
| M15: manejadores extensos | Páginas compartidas | Lizeth/Mikaela | Baja | Extract Function | G16/G24 | #74: conservar división del equipo; alcance parcial |
| R02: exposición Adminer | compose.yaml | Lizeth | Media | Enlace local y perfil | Configuración | #61: ya integrado en development |

La matriz del equipo debe conservar las asignaciones reales: Sharmely (Membresía),
Katherin (Convocatorias), Mikaela (UI convocatorias/cuentas), Daavid (Persistencia
y herramientas), Natalie (Asistencia) y Lizeth (HTTP/frontend compartido y calidad).
No cerrar issues ajenos ni atribuirles cambios generados en esta integración.

## 8. Tablero y entrega

GitHub Projects: TO-DO → CURRENT ITERATION → IN PROGRESS → FIX VALIDATION → DONE.
Cada issue necesita responsable, etiqueta, prioridad, duración estimada, estrategia,
criterios verificables y enlaces a commit/PR/pruebas. Gestionar los permisos de
Projects con la cuenta del equipo; el token CLI actual puede no tener permiso project.

No cerrar #96 hasta validar integración, CI y evidencia pendiente. Una palabra
`Fix #N` puede cerrar automáticamente al llegar a la rama predeterminada, no
necesariamente al integrar en development. Revisar estados explícitamente.

Entregar:
- Informe con objetivo, entorno, arquitectura, diagnóstico y matriz del equipo.
- URLs de Project, issues, PR y commits (autoría real).
- Sonar antes/después y revisión manual complementaria.
- Pruebas y cobertura, diferenciando omitidas de aprobadas.
- Jenkins: build, Stage View, Console Output y artefactos reales.
- Limitaciones: backend-only en Sonar, Row legado y trabajo del equipo pendiente.
- Evidencia IDE y conclusiones por integrante.

Referencias: [smells](https://refactoring.guru/refactoring/smells),
[técnicas](https://refactoring.guru/refactoring/techniques),
[Clean Code capítulo 17](https://vivekkhatri.com/chapter-17-smells-heuristics-clean-code-robert-c-martin/).
