$ErrorActionPreference = "Stop"

$projectKey = if ($env:SONAR_PROJECT_KEY) { $env:SONAR_PROJECT_KEY } else { "com:sistema_contabilidade" }
$sonarHost = if ($env:SONAR_HOST_URL) { $env:SONAR_HOST_URL.TrimEnd("/") } else { "http://localhost:9000" }
$sonarToken = if ($env:SONAR_TOKEN) { $env:SONAR_TOKEN } else { $env:SONARQUBE_TOKEN }

if ([string]::IsNullOrWhiteSpace($sonarToken)) {
  Write-Host "SONAR_TOKEN/SONARQUBE_TOKEN nao definido. Abortando pre-commit."
  Write-Host "Defina: SONAR_HOST_URL (opcional), SONAR_PROJECT_KEY (opcional) e SONAR_TOKEN."
  exit 1
}

$headers = @{ Authorization = "Bearer $sonarToken" }

Write-Host "Executando analise SonarQube..."
& .\mvnw.cmd -q sonar:sonar "-Dsonar.host.url=$sonarHost" "-Dsonar.token=$sonarToken"
if ($LASTEXITCODE -ne 0) {
  Write-Host "Falha ao executar sonar:sonar."
  exit $LASTEXITCODE
}

Write-Host "Aguardando processamento da tarefa SonarQube..."
$reportTaskPath = Join-Path $PSScriptRoot "..\target\sonar\report-task.txt"
if (-not (Test-Path $reportTaskPath)) {
  Write-Host "Relatorio da tarefa SonarQube nao encontrado."
  exit 1
}

$reportTask = Get-Content $reportTaskPath | ConvertFrom-StringData
$taskCompleted = $false
for ($i = 0; $i -lt 30; $i++) {
  try {
    $task = Invoke-RestMethod -Headers $headers -Uri $reportTask.ceTaskUrl
    if ($task.task.status -eq "SUCCESS") {
      $taskCompleted = $true
      break
    }
    if ($task.task.status -in @("CANCELED", "FAILED")) {
      Write-Host ("Tarefa SonarQube terminou com status: {0}" -f $task.task.status)
      exit 1
    }
  } catch {
    # A tarefa pode ainda nao estar disponivel para consulta.
  }
  Start-Sleep -Seconds 2
}

if (-not $taskCompleted) {
  Write-Host "A tarefa SonarQube nao concluiu a tempo."
  exit 1
}

$measureUrl = "$sonarHost/api/measures/component?component=$([uri]::EscapeDataString($projectKey))&metricKeys=alert_status,bugs,vulnerabilities,code_smells,coverage,duplicated_lines_density"
$measures = Invoke-RestMethod -Headers $headers -Uri $measureUrl

$metricMap = @{}
foreach ($m in $measures.component.measures) {
  $metricMap[$m.metric] = $m.value
}

$issuesUrl = "$sonarHost/api/issues/search?componentKeys=$([uri]::EscapeDataString($projectKey))&resolved=false&ps=1"
$issues = Invoke-RestMethod -Headers $headers -Uri $issuesUrl

Write-Host ""
Write-Host "===== SonarQube Report ====="
Write-Host ("Projeto        : {0}" -f $projectKey)
Write-Host ("Quality Gate   : {0}" -f ($metricMap["alert_status"]))
Write-Host ("Bugs           : {0}" -f ($metricMap["bugs"]))
Write-Host ("Vulnerabilities: {0}" -f ($metricMap["vulnerabilities"]))
Write-Host ("Code Smells    : {0}" -f ($metricMap["code_smells"]))
Write-Host ("Coverage       : {0}%" -f ($metricMap["coverage"]))
Write-Host ("Duplications   : {0}%" -f ($metricMap["duplicated_lines_density"]))
Write-Host ("Open Issues    : {0}" -f $issues.total)
Write-Host ("Dashboard      : {0}/dashboard?id={1}" -f $sonarHost, [uri]::EscapeDataString($projectKey))
Write-Host "============================"
Write-Host ""

if ($metricMap["alert_status"] -ne "OK") {
  Write-Host "Quality Gate falhou. Commit bloqueado."
  exit 1
}

exit 0
