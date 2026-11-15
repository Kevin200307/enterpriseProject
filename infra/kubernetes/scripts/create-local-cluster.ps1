[CmdletBinding()]
param(
    [string]$ClusterName = "swiftdrop"
)

$ErrorActionPreference = "Stop"
$expectedName = "swiftdrop"
if ($ClusterName -ne $expectedName) {
    throw "This script manages only the '$expectedName' Kind cluster."
}

foreach ($command in @("docker", "kind", "kubectl", "helm")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Required command '$command' was not found in PATH."
    }
}

$scriptRoot = Split-Path -Parent $PSScriptRoot
$clusterConfig = Join-Path $scriptRoot "kind\cluster.yaml"
$clusters = @(kind get clusters)
if ($clusters -contains $ClusterName) {
    Write-Host "Kind cluster '$ClusterName' already exists."
} else {
    kind create cluster --name $ClusterName --config $clusterConfig --wait 180s
    if ($LASTEXITCODE -ne 0) { throw "Kind cluster creation failed." }
}

kubectl config use-context "kind-$ClusterName" | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Could not select the Kind cluster context." }
kubectl wait --for=condition=Ready nodes --all --timeout=180s
if ($LASTEXITCODE -ne 0) { throw "Kind cluster nodes did not become ready." }
kubectl get nodes -o wide
