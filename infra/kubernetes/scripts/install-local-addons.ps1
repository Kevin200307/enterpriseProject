[CmdletBinding()]
param([string]$ClusterName = "swiftdrop")

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
helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx `
    --namespace ingress-nginx --create-namespace `
    --version 4.13.3 `
    --values $values `
    --atomic --wait --timeout 5m
if ($LASTEXITCODE -ne 0) { throw "Ingress controller installation failed." }
kubectl wait --namespace ingress-nginx --for=condition=Available `
    deployment/ingress-nginx-controller --timeout=300s
