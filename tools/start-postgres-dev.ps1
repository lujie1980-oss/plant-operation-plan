param(
    [switch]$Down
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if ($Down) {
    docker compose -f docker-compose.postgres.yml down
    Write-Host "PostgreSQL stopped."
    exit 0
}

Write-Host "Starting PostgreSQL (plantops on :5432)..."
docker compose -f docker-compose.postgres.yml up -d

Write-Host "Waiting for PostgreSQL..."
$deadline = (Get-Date).AddMinutes(2)
$ready = $false
while ((Get-Date) -lt $deadline) {
    try {
        $r = docker exec plantops-postgres pg_isready -U plantops -d plantops 2>$null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    } catch { }
    Start-Sleep -Seconds 2
}

if (-not $ready) {
    Write-Error "PostgreSQL did not become ready. Check: docker logs plantops-postgres"
}

Write-Host @"

PostgreSQL is ready.
  JDBC:  jdbc:postgresql://localhost:5432/plantops
  User:  plantops / plantops

Next steps (ontology / TODO-12 work):
  `$env:QUARKUS_PROFILE='postgres'
  .\mvnw.cmd quarkus:dev -Dskip.frontend.build=true

Default dev (legacy H2): omit QUARKUS_PROFILE or use default profile.

See docs/ont-postgres-dev.md

"@
