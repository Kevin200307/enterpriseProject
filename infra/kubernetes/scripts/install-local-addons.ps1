[CmdletBinding()]
param(
    [string]$ClusterName = "swiftdrop",
    [ValidateRange(1, 60)][int]$TimeoutMinutes = 15
)

$ErrorActionPreference = "Stop"
if ($ClusterName -ne "swiftdrop") {
    throw "This script configures only the 'swiftdrop' Kind cluster."
}
$context = kubectl config current-context
if ($context -ne "kind-$ClusterName") {
    throw "Current kubectl context '$context' is not kind-$ClusterName."
}

$root = Split-Path -Parent $PSScriptRoot
$values = Join-Path $root "kind\ingress-values.yaml"
helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx --force-update
if ($LASTEXITCODE -ne 0) { throw "Could not configure the ingress chart repository." }
$rollbackFlag = if ((helm upgrade --help) -match '--rollback-on-failure') { "--rollback-on-failure" } else { "--atomic" }
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx `
    --kube-context "kind-$ClusterName" `
    --namespace ingress-nginx --create-namespace `
    --version 4.13.3 `
    --values $values `
    $rollbackFlag --wait --timeout "${TimeoutMinutes}m"
if ($LASTEXITCODE -ne 0) { throw "Ingress controller installation failed." }
kubectl --context "kind-$ClusterName" wait --namespace ingress-nginx --for=condition=Available `
    deployment/ingress-nginx-controller --timeout=300s
