# Push image to Aliyun ACR (step 2)
# Usage: powershell -ExecutionPolicy Bypass -File tools/docker-push-acr.ps1
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$EnvFile = Join-Path $PSScriptRoot "acr.local.env"
if (-not (Test-Path $EnvFile)) {
    Write-Host "Missing $EnvFile - copy from tools/acr.env.example" -ForegroundColor Red
    exit 1
}

Get-Content $EnvFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
        $name = $Matches[1]
        $val = $Matches[2].Trim().Trim('"').Trim("'")
        Set-Item -Path "Env:$name" -Value $val
    }
}

foreach ($k in @("ACR_REGISTRY", "ACR_NAMESPACE", "ACR_REPO", "LOCAL_IMAGE", "ACR_TAG", "ACR_USERNAME")) {
    if (-not (Get-Item "Env:$k" -ErrorAction SilentlyContinue)) {
        Write-Host "acr.local.env missing $k" -ForegroundColor Red
        exit 1
    }
}

$remote = "$($env:ACR_REGISTRY)/$($env:ACR_NAMESPACE)/$($env:ACR_REPO):$($env:ACR_TAG)"

docker image inspect $env:LOCAL_IMAGE 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "Local image not found: $($env:LOCAL_IMAGE)" -ForegroundColor Red
    Write-Host "Run tools/docker-build-local.ps1 first"
    exit 1
}

Write-Host "==> Tag  $remote" -ForegroundColor Cyan
docker tag $env:LOCAL_IMAGE $remote
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "==> Login $($env:ACR_REGISTRY)" -ForegroundColor Cyan
if ($env:ACR_PASSWORD) {
    $env:ACR_PASSWORD | docker login --username $env:ACR_USERNAME --password-stdin $env:ACR_REGISTRY
} else {
    Write-Host "Set ACR_PASSWORD in acr.local.env or run: docker login $($env:ACR_REGISTRY)" -ForegroundColor Yellow
    exit 1
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "==> Push $remote" -ForegroundColor Cyan
docker push $remote
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Push OK: $remote" -ForegroundColor Green
