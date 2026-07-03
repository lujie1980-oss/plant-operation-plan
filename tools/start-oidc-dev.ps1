param(
    [switch]$Down
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if ($Down) {
    docker compose -f docker-compose.oidc.yml down
    Write-Host "Keycloak stopped."
    exit 0
}

Write-Host "Starting Keycloak (plantops realm on :8081)..."
docker compose -f docker-compose.oidc.yml up -d

Write-Host "Waiting for Keycloak..."
$deadline = (Get-Date).AddMinutes(2)
$ready = $false
while ((Get-Date) -lt $deadline) {
    try {
        $r = Invoke-WebRequest -Uri "http://localhost:8081/realms/plantops" -UseBasicParsing -TimeoutSec 5
        if ($r.StatusCode -eq 200) { $ready = $true; break }
    } catch { }
    Start-Sleep -Seconds 3
}

if (-not $ready) {
    Write-Error "Keycloak did not become ready in time. Check: docker logs plantops-keycloak"
}

Write-Host @"

Keycloak is ready.
  Realm:     http://localhost:8081/realms/plantops
  Admin UI:  http://localhost:8081/admin  (admin / admin)

Next steps:
  1. Backend:  `$env:QUARKUS_PROFILE='oidc'; .\mvnw.cmd quarkus:dev -Dskip.frontend.build=true`
  2. Frontend: cd frontend; npm run dev
  3. Browser:  http://localhost:5173  -> 使用企业账号登录 (planner / planner)
  4. Smoke:    .\tools\oidc-smoke-test.ps1

See docs/oidc-keycloak-dev.md

"@