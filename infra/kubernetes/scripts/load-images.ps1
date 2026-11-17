[CmdletBinding()]
param(
    [string]$ClusterName = "swiftdrop",
    [string]$Tag = "dev",
    [ValidatePattern('^linux/(amd64|arm64)(/v8)?$')][string]$Platform
)

$ErrorActionPreference = "Stop"
if ($ClusterName -ne "swiftdrop") {
    throw "This script loads images only into the 'swiftdrop' Kind cluster."
}

$services = @(
    "gateway",
    "identity-service",
    "parcel-service",
    "pricing-service",
    "dispatch-service",
    "merchant-service",
    "tracking-service",
    "notification-service",
    "ai-assistant-service",
    "web",
    "minio",
    "minio-client"
)
$images = @()
foreach ($service in $services) {
    $image = "swiftdrop/$service`:$Tag"
    $images += $image
}
$importArguments = @{ Images = $images; ClusterName = $ClusterName }
if ($Platform) { $importArguments.Platform = $Platform }
& (Join-Path $PSScriptRoot "import-kind-images.ps1") @importArguments
