# Jenkins: construcción, pruebas, JaCoCo y SonarQube del backend

El pipeline del laboratorio está versionado en **`Jenkinsfile`**. GitHub Actions
se conserva como respaldo; una ejecución verde de Actions **no demuestra** una
ejecución de Jenkins. Para entregar la práctica hacen falta Console Output y
Stages de un build real de Jenkins.

## Arquitectura y alcance

```text
GitHub (development) -> Jenkins -> compilación Maven
                              -> PostgreSQL desechable
                              -> JUnit + ArchUnit + JaCoCo
                              -> Scanner CLI -> SonarQube persistente
                              -> publicar JAR
                              -> arrancar JAR en 8083, comprobar salud y cerrarlo
```

- Agente Windows o Linux: el pipeline selecciona `bat` o `sh` con `isUnix()`.
- Java, Python y Docker deben estar disponibles para **el usuario del agente**.
- PostgreSQL usa un puerto libre de localhost y un contenedor por build, sin
  montar volúmenes del proyecto. Nunca se usa la base personal.
- SonarQube es el servidor del laboratorio: no se cambia su contraseña, no se
  elimina su información ni se crea un servidor temporal.
- Solo backend. Frontend/Selenium conservan sus comprobaciones de Actions;
  este Jenkinsfile no pretende ejecutarlas.
- No hay despliegue permanente a producción. El JAR se publica como artefacto y
  se prueba en 8083 durante el build; después se detiene deliberadamente.

**Disponibilidad:** Jenkins no elimina la necesidad de una máquina encendida.
Si Jenkins está en tu PC, depende de tu PC. Para independencia del equipo,
instalar controlador/agentes y SonarQube en un servidor compartido disponible.

## 1. Preparar el entorno

Necesitas Git, Python 3.10+, Docker con contenedores Linux y acceso a Internet.
En Windows, iniciar Docker Desktop **antes** de arrancar Jenkins y comprobar:

```powershell
java -version
git --version
python --version
docker version
```

En Linux, utilizar `python3` en lugar de `python`. El usuario del agente debe poder
ejecutar Docker. Ese acceso tiene privilegios elevados: usar una máquina/cuenta
dedicada sin secretos personales, no ejecutar código de PR no confiables.
En Windows es más sencillo el WAR con tu usuario que un servicio LocalSystem:
ese servicio puede no tener acceso a Docker Desktop ni al PATH de tu sesión.

**Java de Jenkins:** las versiones actuales de Jenkins requieren Java **21 o 25**;
el “Java 17+” del material no basta para el Jenkins LTS actual. Tu aplicación sigue
compilando con `release 17`. El Java del controlador y el de la construcción son
conceptos distintos. Puedes usar JDK 21 en ambos para este laboratorio.

Descargar el WAR LTS desde:
https://www.jenkins.io/download/ o
https://get.jenkins.io/war-stable/latest/jenkins.war

## 2. Arrancar Jenkins sin ocupar el puerto del backend

Guardar el WAR **fuera del repositorio**. Desde su carpeta, en PowerShell:

```powershell
$env:JENKINS_HOME = "$HOME\jenkins-cruzroja"
java -jar .\jenkins.war --httpPort=8090 --httpListenAddress=127.0.0.1
```

En Linux:

```bash
export JENKINS_HOME="$HOME/jenkins-cruzroja"
java -jar jenkins.war --httpPort=8090 --httpListenAddress=127.0.0.1
```

Abrir **http://localhost:8090**. Usar la contraseña inicial indicada en la consola,
instalar los plugins sugeridos y crear un administrador con contraseña propia.
No desactivar autenticación, CSRF ni el sandbox para hacer funcionar el ejemplo.
8090 es Jenkins; 9000 SonarQube; 8083 la prueba del JAR.
Para un servidor compartido, configurar acceso seguro/HTTPS y firewall antes de
exponerlo: estos comandos lo publican intencionadamente solo en localhost.

## 3. Plugins y herramientas

Manage Jenkins -> Plugins. Comprobar:

| Plugin | Uso |
|---|---|
| Pipeline (incluido Declarative) | Interpretar Jenkinsfile y ejecutar etapas |
| Git | Checkout y consulta de cambios |
| Credentials Binding | Inyectar token sin escribirlo en el código |
| Timestamper | Añadir hora a Console Output con `timestamps()` |
| JUnit | Publicar resultados y tendencias de pruebas |
| SonarQube Scanner | Registrar la instalación de Scanner CLI |
| Pipeline: Stage View | Mostrar el overview de etapas solicitado |

No son obligatorios Maven Integration, JaCoCo antiguo ni HTML Publisher para este
pipeline: Maven Wrapper ejecuta Maven y los reportes HTML/XML se archivan.

Manage Jenkins -> Tools:

1. **JDK:** crear una instalación llamada exactamente **`JAVA`**. Indicar el
   directorio raíz de un JDK completo (no `bin`), preferiblemente tu JDK 21.
2. **SonarQube Scanner:** crear una instalación llamada **`sonar-scanner`**.
   Seleccionar Scanner CLI **7.2.0.5079** mediante instalador, o indicar su carpeta
   descomprimida si no aparece en la lista. No indicar la carpeta del servidor.
3. Git debe estar instalado/detectado. Maven se obtiene mediante `mvnw` del repo.

Si utilizas agentes remotos, herramientas, Python y Docker deben estar disponibles
en el agente elegido, no solamente en el controlador. Configurar las ubicaciones
de herramientas por nodo cuando Windows y Linux tengan rutas distintas.
Las tres pruebas de herramientas del bootstrap Bash se ejecutan solo en POSIX:
en Windows se omiten explícitamente, pues Jenkins usa `retry` nativo con `mvnw.cmd`.
No son pruebas omitidas del código Java de negocio.

## 4. SonarQube y credencial

Iniciar el SonarQube del laboratorio y esperar:

```powershell
Invoke-RestMethod http://localhost:9000/api/system/status
```

En Linux: `curl --fail http://localhost:9000/api/system/status`.
Debe indicar `UP`. Comprobar/crear el proyecto con clave **`convocatorias`**.
Generar un token con permisos de analizar y consultar ese proyecto.

Jenkins -> Manage Jenkins -> Credentials -> System -> Global credentials:

- Kind: **Secret text**.
- Secret: tu token local (no el token copiado del ejemplo del docente).
- ID: **`sonarqube-token`**.

El pipeline inyecta `SONAR_TOKEN` solo durante el análisis. No lo pega en comandos,
Groovy, propiedades ni artefactos. Nunca compartir capturas con tokens.
Si el token del ejemplo fuera una credencial real, su propietario debe revocarlo.

El servidor se configura en `Jenkinsfile`:

```groovy
SONAR_HOST_URL = 'http://localhost:9000'
```

`localhost` significa **el equipo del agente**. Si Sonar está en otra máquina,
reemplazar esa dirección por la URL segura y accesible desde el agente.
Se usa CLI con espera del Gate, no `waitForQualityGate`; por tanto no hace falta
configurar un webhook Sonar -> Jenkins para este pipeline.
Los reportes usan `sonar-project.properties`, el mismo alcance backend de Actions.

## 5. Crear el job desde Git

Dashboard -> New Item -> nombre **`Convocatorias_Backend`** -> **Pipeline**.

En Pipeline:

- Definition: **Pipeline script from SCM**.
- SCM: Git.
- Repository URL: `https://github.com/kzenayuca/Convocatorias_CruzRoja.git`.
- Credentials Git: ninguna para leer el repositorio público.
- Branch Specifier: **`*/development`**, después de integrar la PR.
- Script Path: **`Jenkinsfile`**.

Para validar la PR antes de integrarla, un administrador puede seleccionar
temporalmente **`*/ci/jenkins-backend`**, tras revisar el código. Después volver
a `*/development`. No ejecutar Jenkinsfile de forks desconocidos con credenciales.
Sonar Community usa una rama de proyecto: no configurar análisis concurrentes de
ramas distintas sobre el mismo proyecto persistente.

Guardar y hacer **Build Now** para la primera ejecución. El pipeline declara
`pollSCM('H/5 * * * *')`: después revisa cambios aproximadamente cada cinco minutos
y construye cuando encuentra cambios, siempre que Jenkins esté funcionando.
Esto sirve en localhost sin exponer un webhook de GitHub. No crear commits vacíos
solo para simular automatización.

## 6. Etapas que debes ver

| Etapa | Resultado esperado |
|---|---|
| Git Checkout | Commit identificado en la consola |
| Preparar herramientas | Java/Python/Docker disponibles y pruebas de herramientas |
| Construccion Maven | Spotless y compilación exitosas |
| PostgreSQL aislado | Contenedor propio listo, puerto dinámico |
| Pruebas y JaCoCo | JUnit real, sin contar omitidas como aprobadas |
| Preparar analisis Java | Librerías copiadas para Scanner CLI |
| SonarQube y Quality Gate | Análisis procesado, Gate y métricas exportados |
| Publicar JAR | JAR descargable con fingerprint |
| Probar JAR en 8083 | HTTP 200 y `status UP`, cierre del proceso propio |

Si el puerto 8083 está ocupado, el build falla: no mata la aplicación ajena.
PostgreSQL se retira al finalizar, verificando una etiqueta que identifica al build.
No se utilizan `docker system prune`, limpieza de volúmenes personales ni resets Git.
La política del job retiene hasta 20 builds y artefactos de hasta 10 builds.

El formato Java usa LF explícito en `.gitattributes` y Spotless para evitar
diferencias Windows/Linux. Usar un checkout nuevo de Jenkins para la primera
ejecución; no reformatear masivamente el trabajo del equipo en una carpeta antigua.
Al comenzar se limpia solo `backend/target`, que es salida generada. Si falla el
checkout no se archivan reportes anteriores. La prueba del JAR también verifica
que su URL PostgreSQL corresponda al contenedor etiquetado de ese mismo build.

## 7. Evidencias para el docente

1. **Stages:** captura del job y sus etapas reales.
2. **Console Output:** abrir el build -> Console Output -> vista de texto completo
   (`<BUILD_URL>consoleText`) y guardar el log.
3. **Test Result:** cantidades, suites, fallos y omitidas publicadas por JUnit.
4. **Artifacts:** JAR, JaCoCo HTML/XML, resumen, issues CSV/JSON, Gate y procedencia.
5. **SonarQube:** abrir el dashboard local, fecha, hallazgos y cobertura.

`provenance.json` identifica `provider: jenkins`, el commit y el enlace al build;
no se presenta un resultado de GitHub como si lo hubiera producido Jenkins.
El JAR solo corre durante su prueba: no quedará escuchando en 8083 al terminar.
Si se exige una aplicación publicada permanentemente, eso es otra etapa de
despliegue con servicio/base propios; no dejar un `java -jar` bloqueando el build.

Un Gate rechazado hace fallar Jenkins, pero se conservan las evidencias. No reducir
umbrales para aparentar un verde. En un primer análisis, `OK` con condiciones vacías
no demuestra evaluación efectiva de código nuevo ni ausencia de defectos.
No sumar coberturas backend/E2E ni afirmar TDD solo porque existan pruebas.

## 8. Fallos frecuentes

| Síntoma | Revisar |
|---|---|
| `JAVA` o `sonar-scanner` no encontrado | Nombres exactos en Tools y ubicación en el agente |
| Jenkins no inicia con Java 17 | Usar JDK 21/25 compatible con el LTS instalado |
| Docker no conecta en Windows | Docker Desktop iniciado, Linux containers y mismo usuario |
| Python no encontrado | PATH del usuario/agente; reiniciar Jenkins tras cambiarlo |
| Sonar no responde | Servidor UP y URL accesible desde el agente |
| HTTP 401/403 | Credencial vigente, proyecto existente, análisis y lectura permitidos |
| Falla Spotless en Windows | Checkout nuevo con LF, no omitir el control para ocultarlo |
| Gate rojo | Condiciones reales del JSON y hallazgos del dashboard |
| Esperando agente | Nodo conectado y ejecutores disponibles |

**Validación:** las pruebas Python y la compilación/sintaxis pueden comprobarse
sin Jenkins. El pipeline completo y su Overview solo quedan validados después
de ejecutar este job en una instalación Jenkins configurada.
