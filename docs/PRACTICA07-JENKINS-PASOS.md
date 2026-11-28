# Laboratorio 07: desde el análisis inicial hasta las evidencias de Jenkins

## 1. Interpretar la captura de SonarQube

La captura aportada muestra:
- Proyecto visible: `cruz-roja-gestion-inscripciones`.
- Quality Gate Passed sobre la vista **New Code**.
- Último análisis de hace un mes y advertencias pendientes.
- Cobertura y duplicación de código nuevo: **Not computed**.

No permite concluir cero problemas globales, cobertura 0 %, cobertura completa
ni que ese análisis corresponda al commit actual. Pulsar **See details** y guardar
las advertencias. Cambiar a **Overall Code** para revisar el total y abrir Issues,
Security Hotspots y Measures.

El repositorio configura la clave `convocatorias`, nombre `Convocatorias Cruz Roja`.
El nombre de la captura es diferente: puede ser otro proyecto o un nombre cambiado.
Comprobar **Project Information → Project Key**, versión del servidor y SHA del
análisis en Activity/Background Tasks o en la evidencia exportada antes de compararlos.
No cambiar la clave del repositorio solo para que coincida visualmente una captura.

## 2. Preparación de herramientas

El docente acepta main como master y requiere Jenkins. Para versiones, la ficha
pide Sonar 9.9.7 y Scanner 5–6.2.1. El servidor local observado el 28/11/2026
respondió **25.12.0.117093 UP** y el PATH Windows contiene Scanner CLI 8.0.1.
Debe confirmarse aceptación de estas versiones o usar un laboratorio separado.
No mezclar versiones en la comparación ni degradar el servidor sobre sus datos.

En PowerShell:

```powershell
java -version
python --version
node --version
docker version
sonar-scanner.bat --version
Invoke-RestMethod http://localhost:9000/api/system/status
```

Se observó Jenkins respondiendo en 8090 y Sonar en 9000, pero sus API rechazan
acceso anónimo. Que respondan HTTP no demuestra que un build esté configurado o aprobado.
No publicar tokens ni contraseñas en la evidencia.

## 3. Elegir la versión antes de tocar más código

Guardar el SHA inicial y el alcance. Si las correcciones ya existen, reconstruir
el «antes» en otro checkout y declarar su procedencia. No resetear la carpeta actual.
Usar las mismas reglas, exclusiones, versiones y procedimiento en ambos análisis.
Conservar los archivos de evidencia antes de ejecutar un análisis que sustituya
el dashboard del proyecto.

## 4. Revisión manual y gestión

1. Revisar el módulo asignado, no solo el listado de Sonar.
2. Documentar archivo/método, evidencia, efecto y referencia a Clean Code/SOLID.
3. Registrar si es smell confirmado, bug reproducido o riesgo pendiente.
4. Crear/agrupar issues sin duplicar los que ya existen (#50–#75 y #96).
5. Asignar persona, prioridad, etiqueta y duración estimada.
6. En GitHub Projects usar TO-DO, CURRENT ITERATION, IN PROGRESS,
   FIX VALIDATION, DONE.
7. DONE requiere criterios comprobados y enlaces, no solo un commit.

### Formato del docente, ejemplo individual

| Smell | Módulo o componente | Responsable | Estrategia de refactoring |
|---|---|---|---|
| M13: contrato HTTP any (G4/G26) | api.ts | Lizeth | Contratos explícitos y unknown por defecto; Row legado documentado |
| R04: respuesta CSRF sin validar (G26/T5) | api.ts | Lizeth | Extract Method + validación del estado y estructura |
| M17: ciclo asíncrono duplicado (G5) | components.tsx / useRequest.ts | Lizeth, coordinando con Mikaela | Extract Function/custom hook |
| M16: plazos dispersos (G25/G33) | ActivityPages / attendanceRules | Lizeth | Constantes + Extract Function |
| R03: error local de notificación | ActivityPages | Lizeth | Extract Method y manejo del rechazo |

Añadir al inventario final prioridad, categoría, descripción, estado y evidencia,
como solicita la tabla ampliada. El inventario del equipo está descrito en
[PRACTICA07.md](PRACTICA07.md); las tareas ajenas deben validarse por sus responsables.

## 5. Pruebas y refactoring

- Para bug real: prueba que reproduce el fallo → corrección mínima → refactoring.
- Para refactoring puro: caracterización aprobada antes y después.
- No romper pruebas artificialmente para fabricar un RED.
- Diseñar valores, acción y resultado esperado; implementar Arrange–Act–Assert.
- Usar JUnit 5, Mockito y pruebas del dominio/caso de uso real.

Ejemplo completo con siete casos de Materiales:
[PRUEBAS_UNITARIAS.md](PRUEBAS_UNITARIAS.md).
No se asigna a Lizeth autoría exclusiva del módulo por publicar sus pruebas.

## 6. Configurar el job Jenkins

La instalación detallada está en [JENKINS.md](JENKINS.md).

1. Iniciar Docker Desktop antes del agente Jenkins.
2. Abrir http://localhost:8090 con tu cuenta.
3. Manage Jenkins → Plugins: Pipeline, Git, JUnit, Credentials Binding,
   Timestamper, SonarQube Scanner, Pipeline: Stage View.
4. Manage Jenkins → Tools: JDK `JAVA`, Scanner `sonar-scanner`.
5. Credentials → Secret text → ID `sonarqube-token`; token con permiso de analizar
   y consultar el proyecto `convocatorias`.
6. Job Pipeline → Pipeline script from SCM → Git.
7. URL: `https://github.com/kzenayuca/Convocatorias_CruzRoja.git`.
8. Para validar PR #97: Branch `*/refactor/lizeth-practica07-entrega`.
   Tras revisión/integración: `*/development`. Script Path `Jenkinsfile`.
9. Guardar → Build Now.
10. Comprobar el SHA de checkout y todas las etapas.

La creación de proyecto Sonar y la credencial corresponden al administrador.
No modificar contraseñas ni desactivar seguridad para superar un 401/403.
localhost significa el equipo del agente. Si agente y Sonar no comparten host,
configurar una URL accesible y segura.

## 7. Mostrar pruebas en Jenkins

Tras **Pruebas JUnit y Mockito + JaCoCo**, entrar al número de build →
**Test Result / Resultado de pruebas**. Abrir paquete, clase y método.
Las pruebas que usan Mockito aparecen dentro de JUnit, no como herramienta aparte.
El paso `junit` publica Surefire; Maven es quien ejecutó las pruebas.

Si no aparece:
- verificar plugin JUnit;
- verificar SHA y Jenkinsfile utilizado;
- comprobar si una etapa anterior impidió llegar a pruebas;
- comprobar XML en `backend/target/surefire-reports`;
- comprobar que no se usó `skipTests`.

JaCoCo se descarga en Artifacts → `backend/target/site/jacoco`.
El resumen backend y las evidencias Sonar también se archivan. No es necesario
instalar plugins adicionales de cobertura para satisfacer la visualización de tests.

## 8. Analizar el resultado y cerrar

1. Jenkins Test Result: registrar aprobadas, fallidas y omitidas por separado.
2. Sonar: verificar fecha reciente, proyecto correcto y resultado del Gate.
3. Revisar Overall Code, Issues, Hotspots, Measures y advertencias.
4. Si Coverage no está calculada, revisar generación de `jacoco.xml`, importación,
   alcance y logs; no interpretar «-» como 0 %.
5. Exportar archivos antes/después del commit exacto. El pipeline guarda
   `backend/target/sonar-evidence`; no basta con capturas.
6. Revisar PR y actualizar Project a FIX VALIDATION, luego DONE al cumplir criterios.
7. El enlace a development puede no cerrar automáticamente el issue: verificarlo.

## 9. Evidencia para entregar y exponer

- Commit A/B y versiones/configuración idénticas.
- Reportes Sonar antes/después, advertencias y decisiones de hotspots.
- Matriz manual con responsables y técnicas.
- URL del Project, issues, PR y commits.
- Jenkins build, Stage View, Console Output y Test Result.
- XML Surefire, JaCoCo y métricas exportadas.
- Evidencia SonarQube for IDE.
- Resultados frontend separados (Sonar actual solo analiza backend).
- Limitaciones y trabajo pendiente sin atribuciones falsas.

Guion individual: problema → referencia → prueba → técnica → código antes/después
→ resultado → enlace. No afirmar «sin bugs» por tener Gate Passed.
