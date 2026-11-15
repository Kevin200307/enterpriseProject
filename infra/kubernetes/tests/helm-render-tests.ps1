[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$chart = Join-Path $PSScriptRoot "..\helm\swiftdrop"
$valueFiles = @("values-local.yaml", "values-production.example.yaml")

foreach ($valueFile in $valueFiles) {
    $values = Join-Path $chart $valueFile
    helm lint $chart --values $values
    if ($LASTEXITCODE -ne 0) { throw "Helm lint failed for $valueFile" }

    $rendered = helm template swiftdrop $chart --namespace swiftdrop --values $values
    if ($LASTEXITCODE -ne 0) { throw "Helm rendering failed for $valueFile" }
    $text = $rendered -join "`n"

    if ($text -match 'image:\s+[^\r\n]*:latest(?:\s|$)') {
        throw "$valueFile contains a mutable latest image tag"
    }
    if ($text -match 'privileged:\s+true') {
        throw "$valueFile contains a privileged container"
    }
    if ($text -match 'type:\s+(NodePort|LoadBalancer)') {
        throw "$valueFile exposes an internal Service publicly"
    }

    $deploymentCount = ([regex]::Matches($text, '(?m)^kind: Deployment$')).Count
    $startupProbeCount = ([regex]::Matches($text, '(?m)^\s+startupProbe:$')).Count
    $resourceCount = ([regex]::Matches($text, '(?m)^\s+resources:$')).Count
    if ($deploymentCount -lt 10) { throw "$valueFile rendered only $deploymentCount Deployments" }
    if ($startupProbeCount -lt 10) { throw "$valueFile is missing application startup probes" }
    if ($resourceCount -lt $deploymentCount) { throw "$valueFile has workloads without resources" }

    foreach ($required in @("web", "gateway", "identity-service", "ai-assistant-service")) {
        if ($text -notmatch "(?m)^  name: $([regex]::Escape($required))$") {
            throw "$valueFile does not render required workload/service '$required'"
        }
    }
}

Write-Host "Helm render policy tests passed for local and production values."
