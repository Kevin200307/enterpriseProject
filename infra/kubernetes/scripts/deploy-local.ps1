[CmdletBinding()]
param([ValidatePattern('^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$')][string]$Tag = "dev")

$ErrorActionPreference = "Stop"
$context = "kind-swiftdrop"
foreach ($command in @("helm", "kubectl")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Required command '$command' was not found in PATH."
    }
}
$currentContext = kubectl config current-context
if ($LASTEXITCODE -ne 0 -or $currentContext -ne $context) {
    throw "Select the local kind-swiftdrop context before deploying."
}
$root = Split-Path -Parent $PSScriptRoot
$chart = Join-Path $root "helm/swiftdrop"
$values = Join-Path $chart "values-local.yaml"
$rollbackFlag = if ((helm upgrade --help) -match '--rollback-on-failure') { "--rollback-on-failure" } else { "--atomic" }
$arguments = @("--kube-context", $context, "--namespace", "swiftdrop", "--values", $values,
    $rollbackFlag, "--wait", "--wait-for-jobs", "--timeout", "15m")
foreach ($service in @("web", "gateway", "identity-service", "parcel-service", "pricing-service",
    "dispatch-service", "merchant-service", "tracking-service", "notification-service", "ai-assistant-service")) {
    $arguments += @("--set-string", "services.$service.image.tag=$Tag")
}
$arguments += @("--set-string", "objectStorage.local.serverImage=swiftdrop/minio:$Tag",
    "--set-string", "objectStorage.local.clientImage=swiftdrop/minio-client:$Tag")

$namespaceName = kubectl --context $context get namespace swiftdrop --ignore-not-found -o name
if ($LASTEXITCODE -ne 0) { throw "Could not reach the local cluster." }
if (-not $namespaceName) {
    kubectl --context $context create namespace swiftdrop
    if ($LASTEXITCODE -ne 0) { throw "Could not create the local namespace." }
}
kubectl --context $context label namespace swiftdrop pod-security.kubernetes.io/enforce=baseline --overwrite
if ($LASTEXITCODE -ne 0) { throw "Could not label the local namespace." }
& (Join-Path $PSScriptRoot "create-local-secret.ps1")

$releaseNames = helm list --kube-context $context --namespace swiftdrop --deployed --failed --pending --short
if ($LASTEXITCODE -ne 0) { throw "Could not inspect local Helm releases." }
if (@($releaseNames) -notcontains "swiftdrop") {
    # PostgreSQL is a normal Helm resource, ready before any application migration hook runs.
    helm upgrade --install swiftdrop $chart @arguments --set bootstrapOnly=true
    if ($LASTEXITCODE -ne 0) { throw "Dependency bootstrap failed; applications were not deployed." }
}
helm upgrade --install swiftdrop $chart @arguments --set bootstrapOnly=false
if ($LASTEXITCODE -ne 0) { throw "Application deployment failed; inspect migration Jobs and Helm status." }
Write-Host "SwiftDrop local deployment completed."
