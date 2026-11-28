# xUnit y dobles de prueba en el backend

## Herramientas existentes

- **JUnit 5** es el framework Java de la familia xUnit. No necesitamos xUnit.net.
- **Mockito** ya llega con `spring-boot-starter-test`; no añadimos dependencias.
- **Surefire** ejecuta las pruebas durante Maven `test`/`verify`.
- **JaCoCo** mide el código ejercitado; no sustituye las aserciones.
- El paso `junit` de Jenkins publica XML Surefire. No ejecuta las pruebas ni
  requiere otro plugin xUnit para estos reportes.

## Ejemplo: MaterialsUseCasesTest

Ubicación: `backend/src/test/java/pe/cruzroja/convocatorias/operations/application/MaterialsUseCasesTest.java`.
Sigue el paquete del caso de uso y el patrón Arrange–Act–Assert:

| Caso / valores de prueba | Acción | Resultado esperado |
|---|---|---|
| Catálogo: ID MATERIAL, nombre enviado distinto, cantidad 2, fin NOW + 1 h | Solicitar material | Guardar el nombre del catálogo, no el enviado |
| Personalizado: ID null, nombre Manta termica, cantidad 3, fin NOW + 1 h | Solicitar sin ID de catálogo | Guardar nombre enviado sin consultar catálogo |
| Permiso: operationPermission lanza AccessDenied | Solicitar material | Propagar AccessDenied sin leer/escribir materiales |
| Estado CANCELADA, fin NOW + 1 h | Solicitar material | Rechazar sin acceder al repositorio de materiales |
| Servicio con fin NOW - 1 segundo | Solicitar material | Rechazar sin acceder al repositorio de materiales |
| Fin exactamente NOW | Solicitar material | Rechazar: ya no está dentro del plazo |
| Listado: operationPermission lanza AccessDenied | Consultar materiales | No leer materiales |

La clase bajo prueba **es real**. Sus colaboradores se reemplazan con `@Mock`,
inicializados por `MockitoExtension`. No se levanta Spring, servidor ni PostgreSQL.
La hora y los identificadores son fijos para obtener resultados reproducibles.

- `when(...).thenReturn(...)`: configura una respuesta del doble (uso como **stub**).
- `doThrow(...)`: simula una denegación del colaborador.
- `verify(...)`: comprueba una interacción relevante con el **mock**.
- `never()`/`verifyNoInteractions(...)`: comprueban que el rechazo evita accesos.
- `inOrder(...)`: protege que la autorización preceda a la escritura.
- `assertThrows`/`assertEquals`: comprueban excepciones y resultados observables.

No hacen falta spies ni todos los tipos de dobles en cada test. Aquí no se prueba
la implementación real de autorización ni persistencia: eso pertenece a sus
propias pruebas y a la integración. Las verificaciones solo protegen el contrato
que este caso de uso debe cumplir.

## Ejecutarlas desde la raíz

PowerShell Windows:

```powershell
cd backend
.\mvnw.cmd -B -ntp "-Dtest=MaterialsUseCasesTest,GraduationServiceTest" test
```

Linux:

```bash
cd backend
bash ./mvnw -B -ntp -Dtest=MaterialsUseCasesTest,GraduationServiceTest test
```

Para la suite completa: `./mvnw -B -ntp -Pquality verify` (Windows: `.\mvnw.cmd`).
Las nuevas pruebas se descubren automáticamente por su nombre `*Test` en los
pipelines existentes. No es necesario añadir dependencias ni otro ejecutor.
Los XML quedan en `backend/target/surefire-reports` y JaCoCo en
`backend/target/site/jacoco`. Una ejecución selectiva mide solo esa selección:
no presentar su cobertura como cobertura de toda la suite.

## Alcance y honestidad

Ya existían dobles Mockito en `GraduationServiceTest`, `UseCasesTest` y otras
suites. Estos casos amplían la protección del módulo de materiales.
No sustituyen PostgreSQL/Selenium ni certifican ausencia de fallos.
Son pruebas de regresión sobre comportamiento existente: no afirmar que este
cambio demuestra un ciclo TDD rojo–verde previo que no se haya realizado.

## Ver las pruebas en Jenkins, paso a paso

1. Instalar el plugin **JUnit** en Manage Jenkins → Plugins.
2. Usar el `Jenkinsfile` del commit que contiene esta clase: con una copia anterior
   del pipeline o del código no aparecerán las pruebas nuevas.
3. Ejecutar **Build Now**. La etapa **Pruebas JUnit y Mockito + JaCoCo** ejecuta
   Maven `-Pquality verify`, incluyendo todas las clases descubiertas por Surefire.
4. Entrar al número del build → **Test Result / Resultado de pruebas**.
5. Abrir el paquete `pe.cruzroja.convocatorias.operations.application`,
   después `MaterialsUseCasesTest`. Deben verse sus siete métodos y sus estados
   reales. Un test que usa Mockito aparece como test JUnit: no existe un resultado
   independiente llamado «Mockito».
6. Revisar también `GraduationServiceTest` (tres casos) y el resto de la suite.
7. Guardar URL del build, captura del resumen, detalle de casos y Console Output.
8. Descargar los XML en **Artifacts → backend/target/surefire-reports**.
9. Descargar `backend/target/site/jacoco` completo y abrir `index.html` localmente.
   Sonar importa `jacoco.xml`; no calcula esa cobertura ejecutando tests por su cuenta.

El paso `junit` se ejecuta en `post { always { ... } }` de la etapa de pruebas,
incluso si Maven devuelve fallo. Si una etapa anterior (checkout, formato,
compilación o Docker) falla, las pruebas pueden no llegar a ejecutarse: revisar
Stage View antes de interpretar la ausencia de Test Result.

No hace falta HTML Publisher para ver el listado de pruebas; el plugin JUnit lo
presenta de forma nativa. Los artefactos permiten inspeccionar JaCoCo sin añadir plugins.

## Validación local observada el 28 de noviembre de 2026

Se ejecutó en Windows con JDK 21.0.11:

```text
mvnw.cmd -B -ntp "-Dtest=MaterialsUseCasesTest,GraduationServiceTest" test
MaterialsUseCasesTest: 7, fallos 0, errores 0, omitidas 0
GraduationServiceTest: 3, fallos 0, errores 0, omitidas 0
BUILD SUCCESS
```

Es una ejecución local selectiva, **no un build de Jenkins**, y no demuestra que
las pruebas de integración o Selenium hayan pasado. La ejecución completa con
`-Pquality` en la carpeta compartida original encontró CRLF en 140 archivos.
No se reformateó masivamente el código del equipo ni se desactivó Spotless.
Para validación limpia utilizar un checkout nuevo que respete `.gitattributes`.
