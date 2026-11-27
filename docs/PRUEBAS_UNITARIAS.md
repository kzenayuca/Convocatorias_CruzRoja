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

| Caso | Acción | Resultado esperado |
|---|---|---|
| Material del catálogo, servicio vigente | Solicitar material | Guardar el nombre del catálogo, no el enviado |
| Material personalizado | Solicitar sin ID de catálogo | Guardar nombre enviado sin consultar catálogo |
| Permiso denegado | Solicitar material | Propagar AccessDenied sin leer/escribir materiales |
| Servicio cancelado | Solicitar material | Rechazar sin acceder al repositorio de materiales |
| Servicio terminado | Solicitar material | Rechazar sin acceder al repositorio de materiales |
| Fin exactamente igual a la hora actual | Solicitar material | Rechazar: ya no está dentro del plazo |
| Permiso denegado para listar | Consultar materiales | No leer materiales |

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
pipelines existentes. No es necesario editar Jenkinsfile ni ci.yml.
Los XML quedan en `backend/target/surefire-reports` y JaCoCo en
`backend/target/site/jacoco`. Una ejecución selectiva mide solo esa selección:
no presentar su cobertura como cobertura de toda la suite.

## Alcance y honestidad

Ya existían dobles Mockito en `GraduationServiceTest`, `UseCasesTest` y otras
suites. Estos casos amplían la protección del módulo de materiales.
No sustituyen PostgreSQL/Selenium ni certifican ausencia de fallos.
Son pruebas de regresión sobre comportamiento existente: no afirmar que este
cambio demuestra un ciclo TDD rojo–verde previo que no se haya realizado.
