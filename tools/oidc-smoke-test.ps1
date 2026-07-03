# Keycloak OIDC smoke test — requires Keycloak (docker-compose.oidc.yml) and Quarkus (QUARKUS_PROFILE=oidc)
param(
    [string]$KeycloakBase = "http://localhost:8081/realms/plantops",
    [string]$ApiBase = "http://localhost:8080/api/v1",
    [string]$ClientId = "plantops-ui",
    [string]$ClientSecret = "plantops-ui-secret",
    [string]$Username = "planner",
    [string]$Password = "planner"
)

$ErrorActionPreference = "Stop"

Write-Host "==> OIDC discovery"
$discovery = Invoke-RestMethod -Uri "$KeycloakBase/.well-known/openid-configuration"
Write-Host "    issuer: $($discovery.issuer)"

Write-Host "==> Password grant token ($Username)"
$tokenBody = @{
    grant_type    = "password"
    client_id     = $ClientId
    client_secret = $ClientSecret
    username      = $Username
    password      = $Password
}
$tokenRes = Invoke-RestMethod -Method Post -Uri $discovery.token_endpoint -Body $tokenBody -ContentType "application/x-www-form-urlencoded"
$accessToken = $tokenRes.access_token
if (-not $accessToken) { throw "No access_token in response" }
Write-Host "    access_token: $($accessToken.Substring(0, [Math]::Min(24, $accessToken.Length)))..."

Write-Host "==> GET /iam/me with Bearer token"
$me = Invoke-RestMethod -Uri "$ApiBase/iam/me" -Headers @{ Authorization = "Bearer $accessToken" }
Write-Host "    userId: $($me.userId)"
Write-Host "    displayName: $($me.displayName)"
Write-Host "    workspaces: $($me.workspaces.Count)"

if ($me.userId -ne "planner" -and $Username -eq "planner") {
    throw "Expected userId planner, got $($me.userId)"
}

Write-Host "`nOIDC smoke test OK"
