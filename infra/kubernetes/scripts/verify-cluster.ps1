[CmdletBinding()]
param([string]$Namespace = "swiftdrop")

$ErrorActionPreference = "Stop"
if ($Namespace -ne "swiftdrop") { throw "Verification is restricted to namespace 'swiftdrop'." }

kubectl wait --namespace $Namespace --for=condition=Available deployment --all --timeout=600s
if ($LASTEXITCODE -ne 0) { throw "One or more Deployments did not become available." }
kubectl rollout status statefulset/postgres --namespace $Namespace --timeout=300s
kubectl rollout status statefulset/redis --namespace $Namespace --timeout=300s
kubectl rollout status statefulset/rabbitmq --namespace $Namespace --timeout=300s
kubectl get pods,services,ingress,jobs --namespace $Namespace -o wide

$failed = kubectl get pods --namespace $Namespace --field-selector=status.phase=Failed -o name
if ($failed) { throw "Failed pods detected: $failed" }
Write-Host "SwiftDrop cluster workloads are ready."
