# 本地 Docker 构建（先 Maven 打包，再打镜像，避免 buildx 缺路径 / target 被忽略）
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

Write-Host "==> Maven package (frontend + backend)..." -ForegroundColor Cyan
& .\mvnw.cmd -B package -DskipTests "-Dquarkus.package.jar.enabled=true"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (-not (Test-Path "target\quarkus-app\quarkus-run.jar")) {
    Write-Host "缺少 target\quarkus-app\quarkus-run.jar" -ForegroundColor Red
    exit 1
}

Write-Host "==> Docker build (Dockerfile.prebuilt)..." -ForegroundColor Cyan
docker build -f Dockerfile.prebuilt -t plant-operation-plan:1.0.0 .
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "==> Done. Run: docker compose up -d" -ForegroundColor Green
Write-Host "    Or:  docker run -d --name plantops -p 8080:8080 -v plantops-data:/app/data -e QUARKUS_PROFILE=docker plant-operation-plan:1.0.0"
