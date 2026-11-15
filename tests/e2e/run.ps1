[CmdletBinding()]
param(
    [switch]$Keep,
    [string]$GatewayUrl = "http://localhost:18081"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$baseCompose = Join-Path $root "infra/docker-compose.yml"
$parcelCompose = Join-Path $root "infra/docker-compose.parcel.yml"
$e2eCompose = Join-Path $PSScriptRoot "docker-compose.e2e.yml"
$project = "swiftdrop-e2e"
$compose = @("compose", "-p", $project, "-f", $baseCompose, "-f", $parcelCompose, "-f", $e2eCompose)

$env:GATEWAY_HOST_PORT = if ($env:E2E_GATEWAY_HOST_PORT) { $env:E2E_GATEWAY_HOST_PORT } else { "18081" }
$env:RABBITMQ_MANAGEMENT_HOST_PORT = if ($env:E2E_RABBITMQ_MANAGEMENT_HOST_PORT) { $env:E2E_RABBITMQ_MANAGEMENT_HOST_PORT } else { "25673" }
$env:MINIO_API_HOST_PORT = if ($env:E2E_MINIO_API_HOST_PORT) { $env:E2E_MINIO_API_HOST_PORT } else { "29000" }
$env:MINIO_CONSOLE_HOST_PORT = if ($env:E2E_MINIO_CONSOLE_HOST_PORT) { $env:E2E_MINIO_CONSOLE_HOST_PORT } else { "29001" }
$env:MAILPIT_SMTP_PORT = if ($env:E2E_MAILPIT_SMTP_PORT) { $env:E2E_MAILPIT_SMTP_PORT } else { "21025" }
$env:MAILPIT_UI_PORT = if ($env:E2E_MAILPIT_UI_PORT) { $env:E2E_MAILPIT_UI_PORT } else { "28025" }
$env:MERCHANT_HOST_PORT = if ($env:E2E_MERCHANT_HOST_PORT) { $env:E2E_MERCHANT_HOST_PORT } else { "18080" }
$env:REDIS_PASSWORD = "swiftdrop-e2e"
$env:RABBITMQ_USERNAME = "swiftdrop"
$env:RABBITMQ_PASSWORD = "swiftdrop-e2e"
$env:MINIO_ACCESS_KEY = "swiftdrop"
$env:MINIO_SECRET_KEY = "swiftdrop-e2e-secret"
$env:ADMIN_BOOTSTRAP_ENABLED = "true"
$env:ADMIN_BOOTSTRAP_EMAIL = "admin@e2e.swiftdrop.local"
$env:ADMIN_BOOTSTRAP_PASSWORD = "E2eAdmin123!"

function Invoke-Compose([string[]]$Arguments) {
    & docker @compose @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker compose failed with exit code $LASTEXITCODE" }
}

try {
    Invoke-Compose @("build", "minio", "minio-init")
    Invoke-Compose @("up", "-d", "--wait", "--wait-timeout", "900", "e2e-postgres", "redis", "rabbitmq", "minio", "mailpit")

    Invoke-Compose @("build", "identity-service", "pricing-service")
    Invoke-Compose @("up", "-d", "--wait", "--wait-timeout", "900", "identity-service", "pricing-service")

    Invoke-Compose @("build", "parcel-service", "dispatch-service")
    Invoke-Compose @("up", "-d", "--wait", "--wait-timeout", "900", "parcel-service", "dispatch-service")

    Invoke-Compose @("build", "merchant-service", "tracking-service", "notification-service")
    Invoke-Compose @("up", "-d", "--wait", "--wait-timeout", "900", "merchant-service", "tracking-service", "notification-service")

    Invoke-Compose @("build", "gateway")
    Invoke-Compose @("up", "-d", "--wait", "--wait-timeout", "900", "gateway")
    1..2 | ForEach-Object {
        & (Join-Path $PSScriptRoot "workflow.ps1") -GatewayUrl $GatewayUrl -Run $_
        if ($LASTEXITCODE -ne 0) { throw "E2E workflow run $_ failed" }
    }

    # A public request must fail in a controlled way while its dependency is down.
    Invoke-Compose @("stop", "pricing-service")
    try {
        $controlledFailure = $false
        for ($attempt = 1; $attempt -le 10; $attempt++) {
            try {
                Invoke-WebRequest -Uri "$GatewayUrl/api/v1/pricing/zones" -TimeoutSec 5 -ErrorAction Stop | Out-Null
            } catch {
                $status = [int]$_.Exception.Response.StatusCode
                if ($status -in @(502, 503, 504)) { $controlledFailure = $true; break }
                throw "Expected 502/503/504 during outage, got $status"
            }
            Start-Sleep -Seconds 2
        }
        if (-not $controlledFailure) { throw "Pricing zones did not report a controlled gateway error during outage" }
    } catch {
        throw
    } finally {
        Invoke-Compose @("start", "pricing-service")
    }
} finally {
    if (-not $Keep) { Invoke-Compose @("down", "--volumes", "--remove-orphans") }
}
