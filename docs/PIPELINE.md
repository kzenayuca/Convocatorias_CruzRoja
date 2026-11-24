# Pipeline backend: automático en GitHub, local para el laboratorio

Para el laboratorio que exige **Jenkins**, usar el `Jenkinsfile` y la
[guía Jenkins Windows/Linux](JENKINS.md). Este documento describe GitHub Actions,
que se conserva como respaldo; su ejecución no sustituye la evidencia de Jenkins.

## Dos entornos, una configuración de análisis

- **CI:** GitHub crea un runner Linux y un SonarQube temporal. No depende de ninguna
  computadora del equipo, runner personal, token guardado ni servidor externo.
- **Laboratorio:** cada integrante puede ejecutar Scanner CLI contra su SonarQube
  en `localhost:9000`, desde Windows o Linux.
- Ambos usan `sonar-project.properties` desde la raíz del repositorio, con rutas
  relativas y análisis solo de backend. Frontend y Selenium mantienen sus jobs.

Versiones CI: SonarQube Community **25.1.0.102122** (imagen fijada por digest),
Scanner CLI **7.2.0.5079** y Java **17** para el backend.
Para comparar antes/después, usar las mismas versiones y reglas en ambos análisis.
Un servidor local de otra versión puede producir hallazgos diferentes.

## Qué hace el pipeline

Archivo: `.github/workflows/ci.yml`.

1. **backend / GitHub Ubuntu:** PostgreSQL efímero y `./mvnw -B -ntp -Pquality verify`.
   Comprueba formato, compila, ejecuta JUnit (unitarias, arquitectura e integración),
   produce JaCoCo y empaqueta. No toca bases de datos personales.
   La distribución Maven Wrapper se conserva en caché para reducir descargas
   repetidas (también en el job funcional).
2. Lee los XML Surefire/JaCoCo y publica cantidades reales, separando omitidas de
   aprobadas. Resuelve las dependencias Java para Scanner CLI y comparte bytecode,
   librerías y reportes de esa misma ejecución.
3. **sonar / otro runner GitHub Ubuntu:** descarga esos artefactos y levanta SonarQube
   en un contenedor aislado, accesible solo en el localhost del runner.
4. Espera el servidor, cambia la contraseña inicial, crea el proyecto y genera un
   token temporal enmascarado. No utiliza los secretos Sonar del repositorio.
5. Scanner CLI lee `sonar-project.properties`, analiza fuentes/pruebas e importa
   `jacoco.xml`. Espera hasta 300 segundos por el Quality Gate.
6. Consulta el Gate por el ID exacto del análisis y exporta métricas, issues
   paginados, hotspots, commit y ejecución. Un fallo del scanner no queda oculto
   aunque se pueda recuperar un Gate aprobado.
7. Guarda diagnóstico, elimina contenedor y volúmenes, y publica las evidencias
   incluso cuando el Gate falla. Si el job se cancela abruptamente, GitHub elimina
   de todas formas la máquina temporal al terminar.

Se activa en los eventos ya existentes: push a `main`, `development`, `refactor/**`,
PR hacia `main`/`development` y ejecución manual. Sonar necesita que backend pase.
Cada ejecución analiza un snapshot independiente; no se utiliza decoración de PR
ni análisis multirrama de pago de SonarQube.

**No hay que configurar `SONAR_ENABLED`, `SONAR_BRANCH`, `SONAR_HOST_URL` ni
`SONAR_TOKEN` en GitHub.** Las variables/secretos de la propuesta anterior ya no se
consultan. No hace falta registrar un runner Windows ni abrir puertos en tu PC.
Las PR externas pueden requerir aprobación según la política de GitHub.

## Evidencias y limitaciones importantes

En Actions → ejecución → Artifacts:

| Artefacto | Contenido |
|---|---|
| `junit-y-cobertura` | Surefire XML, JaCoCo HTML/XML y resumen de pruebas/cobertura |
| `backend-sonar-input` | Bytecode, dependencias y reportes de entrada; retención 7 días |
| `backend-sonarqube` | Gate, métricas, issues JSON/CSV, hotspots, tarea, servidor, procedencia y resumen; retención 30 días |

El log del scanner se consulta en el paso de Actions; `server.log` se adjunta al
artefacto para diagnosticar arranque/procesamiento. No se exportan credenciales.
Los CSV neutralizan prefijos de fórmula al abrir mensajes del código en Excel.
Los hotspots requieren revisión humana; no equivalen automáticamente a vulnerabilidades.

**El servidor temporal no conserva dashboard ni historial.** Su base H2 integrada
es únicamente para esta evaluación desechable, no para producción. Las evidencias
persistentes son los artefactos: descargarlos antes de que expire su retención.
Para el dashboard del laboratorio, utilizar el servidor local.

El Quality Gate por defecto no se rebaja ni se reemplaza. Sus condiciones pueden
referirse a código nuevo: en una instancia recién creada puede no existir una base
para evaluarlo. **Gate OK no significa cero issues, deuda reducida ni cumplimiento
completo de la práctica.** Revisar también los issues y las condiciones devueltas.
Esta arquitectura no sirve para seguir tendencias históricas de código nuevo;
para eso se necesita posteriormente un servidor persistente compartido.

Para el reporte antes/después, conservar artefactos de dos commits identificados,
comparar sus issues bajo las mismas reglas y añadir la revisión manual. El informe
debe relacionar issue → responsable → refactoring → commit/PR → pruebas.

## Laboratorio en VS Code: Windows

Requisitos: Java compatible con el servidor, Java 17+ para el proyecto, Scanner CLI
7.2 instalado y disponible en PATH, y SonarQube local iniciado. No necesitas runner.

1. Inicia tu servidor con su instalación habitual:

   ```powershell
   & "C:\ruta\sonarqube\bin\windows-x86-64\StartSonar.bat"
   ```

2. En otra terminal comprueba:

   ```powershell
   Invoke-RestMethod http://localhost:9000/api/system/status
   ```

   Espera `UP`. Entra al dashboard, comprueba/crea el proyecto `convocatorias` y
   genera un token con permiso de análisis. Nunca copies el token a un archivo.

3. Construye y prepara las librerías:

   ```powershell
   cd backend
   .\mvnw.cmd -B -ntp clean verify dependency:copy-dependencies "-DincludeScope=test" "-DoutputDirectory=target/sonar-libraries"
   cd ..
   ```

4. Desde la raíz, introduce el token sin guardarlo en el historial:

   ```powershell
   $token = Read-Host "Token de tu SonarQube local" -AsSecureString
   $env:SONAR_TOKEN = [System.Net.NetworkCredential]::new("", $token).Password
   sonar-scanner.bat
   Remove-Item Env:SONAR_TOKEN
   Remove-Variable token
   ```

5. Abre `http://localhost:9000` para inspeccionar los hallazgos. El scanner espera
   el Gate; un Gate rechazado devuelve fallo aunque el análisis ya esté disponible.

## Laboratorio en Linux

Inicia tu instalación local de SonarQube como usuario no root. Desde la raíz:

```bash
cd backend
bash ./mvnw -B -ntp clean verify dependency:copy-dependencies -DincludeScope=test -DoutputDirectory=target/sonar-libraries
cd ..
read -rsp "Token de tu SonarQube local: " SONAR_TOKEN
export SONAR_TOKEN
sonar-scanner
unset SONAR_TOKEN
```

**Pruebas locales:** sin `TEST_DB_URL`, las pruebas PostgreSQL se omiten; Selenium
también se omite sin su configuración. No presentar esas omitidas como aprobadas.
El CI sí levanta PostgreSQL y configura sus pruebas de integración.
La cobertura E2E se conserva aparte; no se suman porcentajes de reportes distintos.
En carpetas compartidas entre WSL/Windows, LF/CRLF mezclados pueden hacer fallar
Spotless. No reformatear código de compañeros para ocultarlo: revisar primero
finales de línea. El control `-Pquality` del CI se mantiene en Linux.

## Explicación para la exposición

> Cada integración construye y prueba el backend con una base desechable. JaCoCo
> mide qué código ejercitan las pruebas. Otro job inicia SonarQube temporal,
> analiza los mismos binarios con Scanner CLI, importa cobertura y evalúa el Gate.
> Exportamos los resultados vinculados al commit y eliminamos el servidor.
> El SonarQube local se usa para inspección interactiva y el laboratorio.

JaCoCo no ejecuta pruebas: instrumenta lo que JUnit ejecuta. Sonar no genera esa
cobertura: importa el XML. Spotless comprueba formato; ArchUnit comprueba reglas
arquitectónicas. Ninguna de estas herramientas sustituye la revisión manual.

## Si algo falla

- **Arranque/timeout:** revisar `server.log`; la descarga de la imagen y el primer
  inicio pueden tardar varios minutos.
- **Faltan clases, librerías o XML:** revisar el job backend y su artefacto.
- **Gate rechazado:** revisar `quality-gate.json` y el resumen; corregir hallazgos,
  no cambiar umbrales para aparentar éxito.
- **Sonar omitido:** comprobar si el job backend falló o la ejecución se canceló.
- **No hay botón Run workflow:** debe estar habilitado el workflow en la rama
  predeterminada; las PR y las integraciones ya lo disparan automáticamente.
- **Localhost de GitHub no abre en mi navegador:** es normal; el contenedor ya
  no existe. Consultar los artefactos o ejecutar el análisis local.
