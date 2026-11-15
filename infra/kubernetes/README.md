# SwiftDrop Kubernetes deployment

This directory contains the Helm-based Kubernetes deployment for the complete SwiftDrop
platform. Docker Compose remains the normal single-machine development environment.

## Architecture

The Ingress exposes only `web` and `gateway`. All application and infrastructure dependencies
use private `ClusterIP` Services. Existing Compose DNS names and ports are preserved so the
applications require no Kubernetes-specific service discovery code.

| Workload | Runtime | Internal port | Probe |
| --- | --- | ---: | --- |
| web | nginx | 80 | `/healthz` |
| gateway | Spring Boot | 8080 | `/actuator/health` |
| identity-service | Spring Boot | 8080 | `/actuator/health` |
| parcel-service | Spring Boot | 8080 | `/actuator/health` |
| pricing-service | Spring Boot | 8080 | `/actuator/health` |
| dispatch-service | Spring Boot | 8080 | `/actuator/health` |
| merchant-service | Spring Boot | 8080 | `/actuator/health` |
| tracking-service | Spring Boot | 8080 | `/actuator/health` |
| notification-service | Spring Boot | 8086 | `/actuator/health` |
| ai-assistant-service | Python/FastAPI | 8080 | `/actuator/health` |

Redis and RabbitMQ can run in the local cluster. Production values disable those local
StatefulSets and point applications to managed services. PostgreSQL is external in production,
with one logical database and credential boundary per owning service.

## Prerequisites

- Docker Desktop or another OCI runtime
- Kubernetes `kubectl` compatible with the target cluster
- Helm 4.3 or a compatible Helm 3 release
- Kind 0.30 or newer for the documented local workflow
- An Ingress controller
- Metrics Server when autoscaling is enabled

Check the tools before deployment:

```powershell
docker version
kubectl version --client
helm version
kind version
```

## Environments

`values-local.yaml` is designed for a disposable Kind cluster: one application replica, local
Redis/RabbitMQ/Mailpit, locally loaded images, and HTTP ingress. It is not a production topology.

`values-production.example.yaml` demonstrates managed data-service endpoints, external secret
integration, TLS, multiple replicas, disruption budgets, topology spread, and autoscaling. Copy
it to a protected deployment repository or CI environment and provide real secret references;
never add credentials to this repository.

## Security boundaries

- Application JWT/RBAC enforcement remains authoritative.
- Workloads use dedicated ServiceAccounts and do not mount Kubernetes API tokens by default.
- Pods run without root, privilege escalation, or Linux capabilities where their images permit.
- NetworkPolicies default-deny traffic and then allow only documented application paths.
- Production secrets come from a secret manager, not checked-in Kubernetes Secret manifests.
- Only immutable version or Git-SHA images may be deployed to production.

## Deployment lifecycle

The intended order is infrastructure readiness, database migrations, identity and domain
services, AI assistant, gateway/web, then ingress smoke tests. Migration Jobs must succeed before
the associated rollout. CI renders and validates both local and production values before a Helm
release is allowed.

Detailed commands and recovery procedures are added alongside the implementation commits.

## Create the local cluster

Install Kind, then run:

```powershell
.\infra\kubernetes\scripts\create-local-cluster.ps1
```

The script operates only on the cluster named `swiftdrop`. It is idempotent and will not delete
or replace other clusters. Local Ingress ports are mapped to `http://localhost:8088` and
`https://localhost:8443`. Delete the disposable cluster explicitly with:

```powershell
.\infra\kubernetes\scripts\destroy-local-cluster.ps1
```
