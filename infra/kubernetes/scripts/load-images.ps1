[CmdletBinding()]
param(
    [string]$ClusterName = "swiftdrop",
    [string]$Tag = "dev"
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
    "web"
)
foreach ($service in $services) {
    $image = "swiftdrop/$service`:$Tag"
    docker image inspect $image | Out-Null
    kind load docker-image $image --name $ClusterName
}
