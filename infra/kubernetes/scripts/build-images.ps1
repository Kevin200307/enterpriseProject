[CmdletBinding()]
param(
    [ValidatePattern('^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$')][string]$Tag = "dev",
    [switch]$UseHostJavaBuild
)

$ErrorActionPreference = "Stop"
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw "Docker was not found." }
docker info --format '{{.ServerVersion}}' | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Start Docker before building the local images." }
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "../../.."))
$services = @("gateway", "identity-service", "parcel-service", "pricing-service",
    "dispatch-service", "merchant-service", "tracking-service", "notification-service", "ai-assistant-service", "web")
if ($UseHostJavaBuild) {
    & (Join-Path $PSScriptRoot 'build-host-java-images.ps1') -Tag $Tag
    $services = @("ai-assistant-service", "web")
}
foreach ($service in $services) {
    $buildContext = if ($service -eq "web") { Join-Path $repository "apps/web" } else { $repository }
    $dockerfile = if ($service -eq "web") { Join-Path $buildContext "Dockerfile" } else { Join-Path $repository "services/$service/Dockerfile" }
    docker build --file $dockerfile --tag "swiftdrop/${service}:$Tag" $buildContext
    if ($LASTEXITCODE -ne 0) { throw "Image build failed for $service." }
}
foreach ($target in @("server", "client")) {
    $image = if ($target -eq "server") { "minio" } else { "minio-client" }
    docker build --file (Join-Path $repository "infra/minio/Dockerfile") --target $target `
        --tag "swiftdrop/${image}:$Tag" $repository
    if ($LASTEXITCODE -ne 0) { throw "Image build failed for $image." }
}
