# 修复 Flyway V53 校验和不一致（修改过 V53 后本地启动失败）。请先停止 Quarkus / 后端进程。
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$lock = Join-Path $Root "data\plantops.lock.db"
if (Test-Path $lock) {
    Write-Host "检测到 data/plantops.lock.db — 请先停止后端，再重新运行本脚本。" -ForegroundColor Yellow
    exit 1
}

$v53 = "src/main/resources/db/migration/V53__jinghua_mrp_slitting.sql"
if (-not (Test-Path $v53)) {
    Write-Host "缺少 $v53" -ForegroundColor Red
    exit 1
}

$checksumLine = python tools/flyway_checksum.py $v53 2>&1 | Select-Object -First 1
$checksum = $null
if ($checksumLine -match ':\s*(-?\d+)\s*$') {
    $checksum = [int]$Matches[1]
}

if ($null -eq $checksum) {
    Write-Host "无法计算 V53 checksum" -ForegroundColor Red
    exit 1
}

@"
UPDATE `"flyway_schema_history`" SET `"checksum`" = $checksum WHERE `"version`" = '53';
"@ | Set-Content -Path "tools/repair_flyway_v53.sql" -Encoding ASCII -NoNewline
Add-Content -Path "tools/repair_flyway_v53.sql" -Value "" -Encoding ASCII

Write-Host "Building classpath..."
& .\mvnw.cmd -q dependency:build-classpath "-Dmdep.outputFile=cp.txt" | Out-Null
$cp = Get-Content "cp.txt" -Raw

Write-Host "Updating flyway_schema_history checksum for V53 -> $checksum"
java -cp "@cp.txt" org.h2.tools.RunScript `
    -url "jdbc:h2:file:./data/plantops" `
    -user sa `
    -password "" `
    -script "tools/repair_flyway_v53.sql"

Write-Host "Done. Restart the backend." -ForegroundColor Green
