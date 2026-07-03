# P1 acceptance: AC-IAM automated tests + optional OIDC live smoke
param(
    [switch]$SkipOidc,
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host "==> P1: IAM acceptance (AC-IAM-01..05)" -ForegroundColor Cyan

if (-not $SkipTests) {
    & .\mvnw.cmd test "-Dtest=IamAcTest,AuthenticationFilterTest" "-Dskip.frontend.build=true"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host "    IAM unit/integration tests OK" -ForegroundColor Green
}

if ($SkipOidc) {
    Write-Host "==> OIDC checks skipped (-SkipOidc)" -ForegroundColor Yellow
    exit 0
}

Write-Host "==> P1: OIDC — ensure Keycloak on :8081" -ForegroundColor Cyan
$keycloakUp = $false
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8081/realms/plantops/.well-known/openid-configuration" -UseBasicParsing -TimeoutSec 5
    $keycloakUp = $r.StatusCode -eq 200
} catch { }

if (-not $keycloakUp) {
    Write-Host "    Starting Keycloak via docker compose..." -ForegroundColor Yellow
    & "$PSScriptRoot\start-oidc-dev.ps1"
    Start-Sleep -Seconds 5
}

Write-Host "==> OIDC password-grant smoke (backend must be on :8080)" -ForegroundColor Cyan
$backendUp = $false
try {
    $h = Invoke-WebRequest -Uri "http://127.0.0.1:8080/api/v1/auth/config" -UseBasicParsing -TimeoutSec 5
    $backendUp = $h.StatusCode -eq 200
} catch { }

if (-not $backendUp) {
    Write-Host @"

    Backend not reachable on :8080.
    Start with OIDC profile in another terminal:
      `$env:QUARKUS_PROFILE='oidc'
      .\mvnw.cmd quarkus:dev -Dskip.frontend.build=true

    Then re-run: .\tools\p1-acceptance.ps1 -SkipTests

"@ -ForegroundColor Yellow
    exit 1
}

& "$PSScriptRoot\oidc-smoke-test.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "==> Optional: OidcLiveIntegrationTest (if QUARKUS_PROFILE=oidc on test JVM — skip in default test profile)" -ForegroundColor DarkGray
Write-Host "==> P1 complete. See docs/iam-p1-runbook.md for browser SSO checklist." -ForegroundColor Green
