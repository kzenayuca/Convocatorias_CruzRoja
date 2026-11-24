$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

# Run on Windows, from the repository root, using ONLY the trusted backend artifact.
$root = Split-Path $PSScriptRoot -Parent
$backend = Join-Path $root "backend"
$evidence = Join-Path $backend "target/sonar-evidence"
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
$summaryPath = Join-Path $evidence "summary.md"
if (Test-Path $summaryPath) { Remove-Item $summaryPath }
$sha = if ($env:GITHUB_SHA) { $env:GITHUB_SHA } else { (git -C $root rev-parse HEAD) }
$scannerExit = $null
$gateStatus = "NO VERIFICADO"

function Get-SonarJson([string] $Path) {
    $url = $env:SONAR_HOST_URL.TrimEnd("/") + $Path
    Invoke-RestMethod -Uri $url -Headers @{
        Authorization = "Bearer $env:SONAR_TOKEN"
    } -TimeoutSec 30
}

try {
    foreach ($name in @("SONAR_HOST_URL", "SONAR_PROJECT_KEY", "SONAR_TOKEN")) {
        if (-not [Environment]::GetEnvironmentVariable($name)) {
            throw "Falta $name. Configurar las variables y el secreto del repositorio."
        }
    }
    foreach ($path in @("target/classes", "target/test-classes",
                         "target/surefire-reports", "target/site/jacoco/jacoco.xml")) {
        if (-not (Test-Path (Join-Path $backend $path))) {
            throw "Falta $path. Descargar el artefacto del job backend, sin recompilar."
        }
    }
    $status = Get-SonarJson "/api/system/status"
    if ($status.status -ne "UP") {
        throw "SonarQube no esta listo: $($status.status)"
    }
    $status | ConvertTo-Json -Depth 10 | Set-Content (Join-Path $evidence "server.json") -Encoding UTF8

    Push-Location $backend
    try {
        # Remove only the previous task marker, never reuse a stale server result.
        $taskFile = Join-Path $backend "target/sonar/report-task.txt"
        if (Test-Path $taskFile) { Remove-Item $taskFile }
        & .\mvnw.cmd -B -ntp `
            org.sonarsource.scanner.maven:sonar-maven-plugin:5.1.0.4751:sonar `
            "-Dsonar.projectKey=$env:SONAR_PROJECT_KEY" `
            "-Dsonar.host.url=$env:SONAR_HOST_URL" `
            "-Dsonar.scm.revision=$sha" `
            "-Dsonar.sources=src/main/java" `
            "-Dsonar.tests=src/test/java" `
            "-Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml" `
            "-Dsonar.junit.reportPaths=target/surefire-reports" `
            "-Dsonar.qualitygate.wait=true" `
            "-Dsonar.qualitygate.timeout=300" |
            Tee-Object -FilePath (Join-Path $evidence "scanner.log")
        $scannerExit = $LASTEXITCODE
    } finally {
        Pop-Location
    }
    if (-not (Test-Path $taskFile)) {
        throw "El scanner no genero report-task.txt. Revisar el log de Maven."
    }
    $task = Get-Content $taskFile | ConvertFrom-StringData
    $taskId = [Uri]::EscapeDataString($task.ceTaskId)
    $processing = Get-SonarJson "/api/ce/task?id=$taskId"
    $processing | ConvertTo-Json -Depth 30 | Set-Content (Join-Path $evidence "task.json") -Encoding UTF8
    if ($processing.task.status -ne "SUCCESS") {
        throw "Procesamiento Sonar: $($processing.task.status). No hay Gate verificable."
    }
    # Query this analysis ID, not the latest result of a different run.
    $analysisId = [Uri]::EscapeDataString($processing.task.analysisId)
    $gate = Get-SonarJson "/api/qualitygates/project_status?analysisId=$analysisId"
    $gate | ConvertTo-Json -Depth 30 | Set-Content (Join-Path $evidence "quality-gate.json") -Encoding UTF8
    $gateStatus = $gate.projectStatus.status
    $projectKey = [Uri]::EscapeDataString($env:SONAR_PROJECT_KEY)
    $measures = Get-SonarJson "/api/measures/component?component=$projectKey&metricKeys=coverage,line_coverage,branch_coverage,tests,test_failures,test_errors,skipped_tests,ncloc"
    $measures | ConvertTo-Json -Depth 30 | Set-Content (Join-Path $evidence "measures.json") -Encoding UTF8
    if (-not ($measures.component.measures | Where-Object { $_.metric -eq "coverage" })) {
        throw "El analisis no muestra cobertura. Revisar importacion JaCoCo en scanner.log."
    }
    @(
        "# Backend: SonarQube"
        ""
        "- Commit: ``$sha``"
        "- Proyecto: ``$env:SONAR_PROJECT_KEY``"
        "- Analisis: ``$($processing.task.analysisId)``"
        "- Quality Gate: **$gateStatus**"
        "- Dashboard local: $($task.dashboardUrl)"
        ""
        "| Condicion | Estado | Valor | Umbral |"
        "|---|---|---|---|"
        foreach ($condition in $gate.projectStatus.conditions) {
            $actual = if ($condition.PSObject.Properties["actualValue"]) { $condition.actualValue } else { "N/A" }
            "| $($condition.metricKey) | $($condition.status) | $actual | $($condition.errorThreshold) |"
        }
        ""
        "La cobertura importada procede del XML JaCoCo del job backend de esta ejecucion."
        "El dashboard localhost solo abre en el equipo que ejecuta SonarQube."
        ""
        "## Metricas del servidor"
        foreach ($measure in $measures.component.measures) {
            "- $($measure.metric): $($measure.value)"
        }
    ) | Set-Content $summaryPath -Encoding UTF8
    if ($scannerExit -ne 0 -or $gateStatus -ne "OK") {
        throw "Scanner exit=$scannerExit; Quality Gate=$gateStatus. Consultar quality-gate.json."
    }
} catch {
    if (-not (Test-Path $summaryPath)) {
        "# SonarQube: no se pudo verificar el Quality Gate.`n`nCommit: $sha" |
            Set-Content $summaryPath -Encoding UTF8
    }
    "Error: $($_.Exception.Message)" | Add-Content $summaryPath -Encoding UTF8
    throw
} finally {
    if ($env:GITHUB_STEP_SUMMARY -and (Test-Path $summaryPath)) {
        Get-Content $summaryPath | Out-File $env:GITHUB_STEP_SUMMARY -Append -Encoding UTF8
    }
}
