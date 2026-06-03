# 修复 Flyway V48 失败状态。请先停止 Quarkus / 后端进程。
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$lock = Join-Path $Root "data\plantops.lock.db"
if (Test-Path $lock) {
    Write-Host "检测到 data/plantops.lock.db — 请先停止后端，再重新运行本脚本。" -ForegroundColor Yellow
    exit 1
}

Write-Host "Building classpath..."
& .\mvnw.cmd -q dependency:build-classpath "-Dmdep.outputFile=cp.txt" | Out-Null
$cp = Get-Content "cp.txt" -Raw

Write-Host "Repairing flyway_schema_history (V48 failed row)..."
java -cp "@cp.txt" org.h2.tools.RunScript `
    -url "jdbc:h2:file:./data/plantops" `
    -user sa `
    -password "" `
    -script "tools/repair_flyway_v48.sql"

Write-Host "Done. Restart the backend; Flyway will re-apply V48." -ForegroundColor Green
