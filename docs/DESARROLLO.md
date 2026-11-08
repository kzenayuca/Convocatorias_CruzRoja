# Desarrollo y operación local

## Entorno y puertos

- Java 17+, Maven Wrapper, Node 22.12+.
- PostgreSQL; Compose ofrece la versión 16.4 en 127.0.0.1:5433.
- API 8080; frontend 5173; Adminer 8081.
- `DB_URL`, `DB_USER`, `DB_PASSWORD` configuran el backend. Compose no exporta automáticamente su `.env` a Spring.
- El frontend puede cambiar el destino API mediante `API_PROXY_TARGET`, sin cambiar las rutas `/api`.

No guardar credenciales reales en Git. Las cuentas demo son únicamente para desarrollo.

## Base existente: antes del primer arranque

1. Detener el backend anterior.
2. Hacer copia mediante `pg_dump` o Adminer y comprobar restauración en otra base.
3. Verificar URL, usuario y nombre de base.
4. Adoptar explícitamente Flyway una sola vez:

```powershell
$env:DB_BASELINE = "true"
cd backend
.\mvnw.cmd spring-boot:run
```

En Bash: `DB_BASELINE=true ./mvnw spring-boot:run`.
Después de la adopción correcta, retirar esa variable del entorno.

Flyway aplica baseline 0 y `V1__legacy_schema.sql` sobre el esquema anterior. Una base vacía no
necesita baseline. Hibernate usa `ddl-auto=validate`, no modifica el esquema.
No editar una migración aplicada: añadir V2, V3, etc. No usar `create`, `create-drop` ni borrar volúmenes.
`data.sql` conserva la inicialización demo idempotente; una migración no debe restaurar datos sobre los del usuario.

## Compilar y comprobar

Desde `backend`:

```powershell
.\mvnw.cmd -B -ntp -Pquality clean verify
```

Sin base exclusiva configurada, se omiten pruebas HTTP/ORM. Para habilitarlas:

```powershell
$env:TEST_DB_URL = "jdbc:postgresql://127.0.0.1:5433/convocatorias_test"
$env:TEST_DB_USER = "convocatorias"
$env:TEST_DB_PASSWORD = "convocatorias"
.\mvnw.cmd -B -ntp -Pquality verify
```

La base debe existir y ser **exclusiva de pruebas**. Los fixtures crean y modifican datos.

Desde `frontend`:

```bash
npm ci
npm run build
```

Desde la raíz, comprobar el generador y las referencias del modelo:

```bash
python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/build_model.py
python3 tools/build_model.py --check backend/target/model/Convocatorias.mdj
```

## Pruebas

- Dominio/aplicación: reglas y coordinación sin servidor.
- ArchUnit: dependencias y aislamiento del núcleo.
- ORM/HTTP: PostgreSQL aislado, mapeos, restricciones, permisos y transacciones.
- Comunicaciones: privacidad, primera lectura, rollback y entrega única concurrente.
- Ciclo anual/graduación: unicidad concurrente, metadatos, compromisos futuros, historial y rollback conjunto.
- Selenium: aplicación completa aislada y Chrome/ChromeDriver compatibles.

Con API y frontend de pruebas ya iniciados:

```powershell
.\mvnw.cmd -B -ntp "-Dtest=BrowserTest" "-De2e.url=http://localhost:5173" test
```

Se puede utilizar Selenium remoto con `-Dselenium.url=http://localhost:4444`.
No confundir `BUILD SUCCESS` con ejecución de pruebas omitidas; revisar cantidades y reportes.

## Reportes y calidad

- JUnit: `backend/target/surefire-reports/`.
- Cobertura dominio/integración: `backend/target/site/jacoco/index.html`.
- Cobertura E2E del backend: `backend/target/site/jacoco-e2e/index.html`, cuando se instrumenta su JVM.
- Formato: `./mvnw -Pquality spotless:apply`; comprobación temprana con `-Pquality validate`.
- SonarLint/SonarQube for IDE: analizar archivos modificados en VS Code, registrar hallazgos y resolver riesgos.

Selenium ejecutado en otra JVM no mide por sí solo el backend. Actions inicia el backend con agente
JaCoCo, lo cierra normalmente y produce su reporte separado. No sumar porcentajes de reportes distintos.
Spotless no sustituye SonarLint; un análisis sin hallazgos no garantiza ausencia de vulnerabilidades.

## Integración continua

`.github/workflows/ci.yml` construye backend/frontend, ejecuta integración PostgreSQL efímera,
comprueba formato y ejecuta Selenium con cobertura backend. Se conservan reportes y diagnóstico.
Los resultados remotos solo se conocen después de ejecutar Actions; tener YAML no prueba que pasó.

## Mantenimiento y seguridad

Conservar auditoría de responsables, códigos, decisiones y cierres. No confundir historial del negocio
con notas del desarrollo: el primero es información funcional y no debe borrarse por limpieza.
Antes de uso real: HTTPS, credenciales, copias/restauración, retención y consentimiento, seguridad y capacidad.
No hay recuperación de contraseña, QR, firma digital, certificación automática de cursos ni reserva de stock.

## Publicar una rama revisada

Revisar diferencias y pruebas antes de guardar o publicar:

```bash
git status
git diff --check
git diff --stat
git branch --show-current
```

Hacer commit y push de la rama de evolución cuando su contenido esté revisado. No usar force-push
ni modificar ramas del equipo para publicar una adaptación. `.env`, `target`, dependencias y copias
de datos están fuera de los entregables. La organización del equipo y los PR se gestionan aparte.
