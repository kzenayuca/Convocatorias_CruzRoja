# Pipeline backend y SonarQube local (Windows)

## Qué demuestra cada etapa

Archivo: `.github/workflows/ci.yml`.

1. **GitHub / job backend (Ubuntu):** levanta PostgreSQL efímero y ejecuta
   `./mvnw -B -ntp -Pquality verify`. Comprueba formato, compila, ejecuta JUnit
   (unitarias, arquitectura e integración), genera JaCoCo y empaqueta el JAR.
2. **Evidencias:** `tools/ci_backend_report.py` lee XML reales, cuenta aprobadas,
   fallidas y omitidas, calcula cobertura de líneas/ramas y publica el resumen.
   Si no hay pruebas ejecutadas o XML de cobertura, falla.
3. **Tu Windows / job sonar:** un runner descarga los binarios, resultados y XML
   de **esa misma ejecución**. No vuelve a ejecutar las pruebas ni necesita PostgreSQL
   local. El scanner Maven analiza solo `backend/src/main/java` y sus pruebas.
4. **SonarQube:** procesa el análisis, importa JaCoCo y evalúa el Quality Gate.
   El job espera hasta 300 segundos y consulta el Gate por el ID exacto del análisis.
   Solo pasa con scanner exitoso, cobertura presente y Gate `OK`.
5. **Artefactos:** Actions conserva los XML/HTML y los JSON de Sonar, incluso
   cuando el Gate rechaza el análisis.

Frontend y Selenium mantienen sus jobs anteriores, sin cambios. La cobertura E2E
no se suma a la cobertura backend de este análisis.

## Preparación en VS Code / Windows (una vez)

### 1. Encender SonarQube

Abre PowerShell en VS Code. Inicia tu instalación habitual de SonarQube; si usas
la distribución ZIP:

```powershell
# Sustituir por la ruta real de tu instalación, fuera del repositorio:
& "C:\ruta\sonarqube\bin\windows-x86-64\StartSonar.bat"
```

Mantén esa terminal abierta. En otra terminal:

```powershell
Invoke-RestMethod http://localhost:9000/api/system/status
```

Debe mostrar `status: UP`. El Java que requiere el servidor depende de su versión;
no cambies su instalación de Java por la del proyecto. El job Maven instala Java 17
con `setup-java`, independientemente del Java que inicia el servidor.

Entra a `http://localhost:9000`, comprueba el proyecto **convocatorias** y su rama
principal. Si no existe, créalo manualmente con esa clave. Si tiene otra clave,
usa la variable `SONAR_PROJECT_KEY` indicada abajo. No crees otro proyecto sin necesidad.

Genera un token de análisis para ese proyecto (My Account / Security o la pantalla
de configuración del proyecto). Guárdalo únicamente como secreto de GitHub.

### 2. Registrar el runner Windows

En GitHub: **Settings → Actions → Runners → New self-hosted runner → Windows → x64**.
Necesitas permisos de administración para registrar el runner y configurar variables.

En una terminal PowerShell de Windows, sigue **los comandos que GitHub muestra**:
descarga, verifica su hash, extrae y ejecuta `config.cmd`. Usa una carpeta fuera
del repositorio, por ejemplo `C:\actions-runner-cruzroja`.

Durante la configuración:

- Nombre sugerido: `cruzroja-sonar-windows`.
- Añade la etiqueta personalizada **`sonarqube`**.
- Conserva las etiquetas predeterminadas `self-hosted`, `Windows`, `X64`.
- Para la exposición no hace falta instalarlo como servicio. Inícialo con:

```powershell
Set-Location C:\actions-runner-cruzroja
.\run.cmd
```

Déjalo abierto. GitHub debe mostrar el runner **Idle**. Su token de registro
es temporal y **no** es el token de SonarQube. El runner conecta hacia GitHub;
no necesitas abrir el puerto 9000 a Internet.

**Seguridad:** el repositorio es público. Un runner ejecuta código con los permisos
de su usuario. Usa preferentemente una VM o cuenta Windows dedicada sin privilegios
de administrador, sin credenciales ni archivos personales sensibles. No lo uses para
PR externas/no confiables. Revisa cambios al workflow y protege `development`.
Las condiciones de este job evitan ejecutar PR, pero no sustituyen ese aislamiento
ni impiden que otro workflow del repositorio use el runner. No habilites workflows
no revisados. Detén el runner con Ctrl+C cuando no lo necesites.

### 3. Configurar GitHub

**Settings → Secrets and variables → Actions**:

| Tipo | Nombre | Valor |
|---|---|---|
| Secret | `SONAR_TOKEN` | Token de análisis del proyecto local |
| Variable | `SONAR_HOST_URL` | `http://localhost:9000` |
| Variable | `SONAR_PROJECT_KEY` | `convocatorias` (o la clave real existente) |
| Variable | `SONAR_BRANCH` | `development` |
| Variable | `SONAR_ENABLED` | `true` |

Activa `SONAR_ENABLED` **solo cuando servidor y runner estén listos**. Sin esta
variable, el job Sonar se omite: **un CI verde con Sonar omitido no demuestra análisis**.
Si hay un secreto antiguo `SONAR_HOST_URL`, este workflow no lo usa: utiliza la
**variable**, para que la dirección sea visible y no se confunda con un servidor externo.

Para compatibilidad con Community, un solo proyecto representa una sola rama:
`development`. No pasamos `sonar.branch.name` ni intentamos analizar PR.
Si el docente quiere analizar `main`, cambia `SONAR_BRANCH` a `main`; no alternes
ramas antes de la exposición, porque compartirían el historial del mismo proyecto.
Con ediciones que admitan ramas se puede ampliar esto más adelante.

### 4. Integrar y ejecutar

Revisa la PR hacia `development`. Sus jobs habituales se ejecutan en GitHub;
Sonar se omite en PR para no ejecutar código todavía no integrado en tu equipo.
Al integrar la PR en `development`, se activa automáticamente todo el pipeline,
incluido Sonar si lo habilitaste.

Para repetir: Actions → **CI · arquitectura y regresión** → **Run workflow** →
elige `development`. El botón manual requiere que el workflow esté también en la
rama predeterminada; si no aparece, usa el disparo automático al integrar en `development`.
No crees commits vacíos ni cambies pruebas para forzar un verde.

## Qué mostrar mañana

1. Una ejecución de Actions asociada al commit integrado en `development`.
2. Job **backend** verde: `verify` y resumen con cantidades de pruebas y cobertura.
3. Job **Backend · SonarQube local y Quality Gate** ejecutado (no `Skipped`).
4. SonarQube: fecha del análisis, commit, métricas de cobertura y Quality Gate.
5. Descarga de artefactos:
   - `junit-y-cobertura`: Surefire, JaCoCo HTML/XML y `ci-summary.md`.
   - `backend-sonarqube`: `report-task.txt`, `scanner.log`, `server.json`,
     `task.json`, `quality-gate.json`, `measures.json` y resumen.

**Explicación breve para la exposición:**

> Cada integración inicia el pipeline. Maven compila y prueba el backend contra una
> base desechable; JaCoCo mide el código ejercitado. El runner Windows analiza los
> mismos binarios en SonarQube local. Sonar importa esa cobertura y aplica el Quality
> Gate. Actions espera el resultado y conserva evidencias trazables al commit.

JaCoCo **no ejecuta pruebas**: instrumenta el código mientras JUnit las ejecuta.
Sonar **no genera cobertura**: importa el XML de JaCoCo.
`verify` incluye compilación, pruebas y empaquetado; Spotless añade comprobación de
formato y ArchUnit comprueba restricciones de arquitectura.
Un Gate aprobado no significa ausencia de defectos; significa que se cumplieron
las condiciones configuradas. Cobertura total y cobertura de código nuevo son distintas.

## Fallos frecuentes (sin ocultarlos)

| Síntoma | Comprobación |
|---|---|
| Sonar `Skipped` | Variable `SONAR_ENABLED`, evento y `SONAR_BRANCH`. PR se omite deliberadamente. |
| `Waiting for a runner` | Windows encendido, `run.cmd` abierto, runner Idle y etiqueta `sonarqube`. |
| `Connection refused` | SonarQube iniciado y `/api/system/status` en `UP` desde Windows. |
| 401 / 403 | Token vigente con permiso Execute Analysis en la clave configurada. |
| Falta `classes` o `jacoco.xml` | Artefacto `backend-sonar-input` del job backend exitoso de esta ejecución. |
| Gate `ERROR` | Consultar las condiciones reales del JSON y el dashboard; corregir hallazgos, no rebajar el Gate para aparentar éxito. |
| Gate sin resultado / timeout | Revisar Background Tasks de Sonar; no afirmar que pasó. |
| Coverage diferente entre herramientas | Comparar líneas con líneas; Sonar puede combinar líneas/condiciones y mostrar código nuevo. |

`localhost` en enlaces de artefactos solo funciona en el equipo del servidor.
No copies tokens ni `.env` a reportes o commits. La retención de artefactos depende
de la configuración de GitHub; descarga las evidencias antes de la exposición.

### Nota sobre una construcción manual en tu carpeta Windows

`.\mvnw.cmd clean verify` permite comprobar compilación, pruebas y empaquetado.
Sin `TEST_DB_URL`, las pruebas PostgreSQL se omiten deliberadamente; no presentes
ese resultado como prueba completa de integración. El CI sí define esa variable
y levanta su propia base desechable.

La carpeta local puede mezclar CRLF y LF después de cambios realizados desde WSL
y Windows. Esto puede hacer fallar `-Pquality` en Windows antes de compilar.
No ejecutes `spotless:apply` sobre el trabajo del equipo solo para ocultarlo:
revisa primero la configuración de finales de línea. El control de formato de este
pipeline se mantiene en el runner Linux; el runner Sonar no reformatea ni recompila.
