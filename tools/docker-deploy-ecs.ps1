# Step 3: deploy to Aliyun ECS via SSH
# Usage: powershell -ExecutionPolicy Bypass -File tools/docker-deploy-ecs.ps1
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Import-EnvFile {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) { return }
        if ($line -match '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
            $name = $Matches[1]
            $val = $Matches[2].Trim().Trim('"').Trim("'")
            if (-not (Get-Item "Env:$name" -ErrorAction SilentlyContinue)) {
                Set-Item -Path "Env:$name" -Value $val
            }
        }
    }
}

$EcsEnv = Join-Path $PSScriptRoot "ecs.local.env"
if (-not (Test-Path $EcsEnv)) {
    Write-Host "Missing $EcsEnv - copy from tools/ecs.env.example" -ForegroundColor Red
    exit 1
}

Import-EnvFile (Join-Path $PSScriptRoot "acr.local.env")
Import-EnvFile $EcsEnv

foreach ($k in @("ECS_HOST", "ECS_USER", "ACR_REGISTRY", "ACR_NAMESPACE", "ACR_REPO", "ACR_TAG", "ACR_USERNAME", "ACR_PASSWORD")) {
    if (-not (Get-Item "Env:$k" -ErrorAction SilentlyContinue)) {
        Write-Host "Missing $k in ecs.local.env or acr.local.env" -ForegroundColor Red
        exit 1
    }
}

$port = if ($env:ECS_SSH_PORT) { $env:ECS_SSH_PORT } else { "22" }
$container = if ($env:ECS_CONTAINER_NAME) { $env:ECS_CONTAINER_NAME } else { "plantops" }
$hostPort = if ($env:ECS_HOST_PORT) { $env:ECS_HOST_PORT } else { "8080" }
$dataDir = if ($env:ECS_DATA_DIR) { $env:ECS_DATA_DIR } else { "/data/plantops" }
$sample = if ($env:PLANTOPS_SAMPLE_DATA_ENABLED) { $env:PLANTOPS_SAMPLE_DATA_ENABLED } else { "false" }
$image = "$($env:ACR_REGISTRY)/$($env:ACR_NAMESPACE)/$($env:ACR_REPO):$($env:ACR_TAG)"

$remoteScript = Join-Path $PSScriptRoot "ecs-deploy-remote.sh"
if (-not (Test-Path $remoteScript)) {
    Write-Host "Missing $remoteScript" -ForegroundColor Red
    exit 1
}

$sshArgs = @("-p", $port, "-o", "StrictHostKeyChecking=accept-new", "-o", "ConnectTimeout=15")
$scpArgs = @("-P", $port, "-o", "StrictHostKeyChecking=accept-new", "-o", "ConnectTimeout=15")
if ($env:ECS_SSH_KEY -and (Test-Path $env:ECS_SSH_KEY)) {
    $sshArgs += @("-i", $env:ECS_SSH_KEY)
    $scpArgs += @("-i", $env:ECS_SSH_KEY)
} elseif (-not $env:ECS_SSH_KEY) {
    Write-Host "ECS_SSH_KEY is empty in ecs.local.env - set path to your .pem private key" -ForegroundColor Red
    exit 1
} else {
    Write-Host "ECS_SSH_KEY not found: $($env:ECS_SSH_KEY)" -ForegroundColor Red
    exit 1
}
$target = "$($env:ECS_USER)@$($env:ECS_HOST)"

Write-Host "==> Upload deploy script to ECS" -ForegroundColor Cyan
scp @scpArgs $remoteScript "${target}:/tmp/ecs-deploy-remote.sh"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$envExports = @(
    "export ECS_CONTAINER_NAME='$container'",
    "export ECS_HOST_PORT='$hostPort'",
    "export ECS_DATA_DIR='$dataDir'",
    "export ACR_REGISTRY='$($env:ACR_REGISTRY)'",
    "export ACR_USERNAME='$($env:ACR_USERNAME)'",
    "export ACR_PASSWORD='$($env:ACR_PASSWORD)'",
    "export ACR_IMAGE='$image'",
    "export PLANTOPS_SAMPLE_DATA_ENABLED='$sample'"
) -join "; "

Write-Host "==> Run deploy on $($env:ECS_HOST)" -ForegroundColor Cyan
ssh @sshArgs $target "sed -i 's/\r$//' /tmp/ecs-deploy-remote.sh; chmod +x /tmp/ecs-deploy-remote.sh; $envExports; bash /tmp/ecs-deploy-remote.sh"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "Deploy OK. Open: http://$($env:ECS_HOST):${hostPort}/#/" -ForegroundColor Green
Write-Host "Health:  http://$($env:ECS_HOST):${hostPort}/q/health/ready" -ForegroundColor Green
