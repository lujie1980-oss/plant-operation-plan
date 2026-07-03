# Export workspace `te` from local H2 (./data/plantops) to factory-te-demo.json
# Requires: local DB contains TE workspace data (see tools/restore_te_workspace.py)
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root
.\mvnw.cmd test "-Dtest=SampleDataExporterTest#exportTeWorkspaceJson" "-DfailIfNoTests=false"
Write-Host "Wrote src/main/resources/sample-data/factory-te-demo.json" -ForegroundColor Green
