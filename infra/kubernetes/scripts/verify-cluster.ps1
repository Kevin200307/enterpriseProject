[CmdletBinding()]
param([string]$Namespace = "swiftdrop")

$ErrorActionPreference = "Stop"
if ($Namespace -ne "swiftdrop") { throw "Verification is restricted to namespace 'swiftdrop'." }
$context = kubectl config current-context
if ($LASTEXITCODE -ne 0 -or $context -ne "kind-swiftdrop") {
    throw "Select kind-swiftdrop before running local verification."
}

kubectl wait --namespace $Namespace --for=condition=Available deployment --all --timeout=600s
if ($LASTEXITCODE -ne 0) { throw "One or more Deployments did not become available." }
kubectl rollout status statefulset/postgres --namespace $Namespace --timeout=300s
if ($LASTEXITCODE -ne 0) { throw "PostgreSQL is not ready." }
kubectl rollout status statefulset/redis --namespace $Namespace --timeout=300s
if ($LASTEXITCODE -ne 0) { throw "Redis is not ready." }
kubectl rollout status statefulset/rabbitmq --namespace $Namespace --timeout=300s
if ($LASTEXITCODE -ne 0) { throw "RabbitMQ is not ready." }
kubectl rollout status statefulset/minio --namespace $Namespace --timeout=300s
if ($LASTEXITCODE -ne 0) { throw "Object storage is not ready." }
kubectl get pods,services,ingress,jobs --namespace $Namespace -o wide

$failed = kubectl get pods --namespace $Namespace --field-selector=status.phase=Failed -o name
if ($failed) { throw "Failed pods detected: $failed" }
Write-Host "SwiftDrop cluster workloads are ready."
