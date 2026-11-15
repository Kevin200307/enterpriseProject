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

Each workload gets a dedicated ServiceAccount with API token automount disabled. The chart uses
the RuntimeDefault seccomp profile, non-root execution, no privilege escalation, and drops all
Linux capabilities. The local chart may create its dedicated namespace with Pod Security labels;
production namespace ownership should remain with the platform team.

## Deployment lifecycle

The intended order is infrastructure readiness, database migrations, identity and domain
services, AI assistant, gateway/web, then ingress smoke tests. Migration Jobs must succeed before
the associated rollout. CI renders and validates both local and production values before a Helm
release is allowed.

Detailed commands and recovery procedures are added alongside the implementation commits.

## Ingress

Only `web` and `gateway` are externally routed. `/api` and `/ws` go to the gateway; all other
paths go to the SPA. NGINX buffering is disabled and one-hour stream timeouts preserve assistant
SSE and tracking WebSocket connections. Production values must enable TLS and provide a
certificate Secret through cert-manager or the cloud ingress integration.

## Database migrations

The Helm release creates bounded migration Jobs. Java services launch their packaged Spring Boot
application in non-web mode so embedded Flyway migrations run and the process exits. The AI
assistant executes `alembic upgrade head`. Jobs retry once, time out, use service-specific
database keys, and are removed after success. A failed Job fails an atomic Helm operation; inspect
it before retrying:

```powershell
kubectl get jobs -n swiftdrop
kubectl logs job/<migration-job-name> -n swiftdrop
```

The AI assistant uses the same internal DNS names as Compose, validates identity-service JWKS,
and forwards user JWTs to downstream services. Its Redis and PostgreSQL URLs come from the
external Secret. Gemini remains optional: an absent `GOOGLE_API_KEY` disables model-backed
responses safely while deterministic assistant behavior remains available. Autoscaling defaults
remain conservative to protect Gemini quota and Java downstream capacity.

The chart never creates application credentials. Create the Secret named by `existingSecret`
through a secret manager integration. `secret-keys.example.yaml` documents its key contract and
contains no usable values. Generated local Secret files match `*secret*.local.yaml` and are
ignored by Git.

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
