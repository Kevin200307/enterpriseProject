# SwiftDrop Kubernetes deployment

This directory contains the Helm-based Kubernetes deployment for the complete SwiftDrop
platform. Docker Compose remains the normal single-machine development environment.

## Architecture

The Ingress exposes only `web` and `gateway`. All application and infrastructure dependencies
use private `ClusterIP` Services. Existing Compose DNS names and ports are preserved so the
applications require no Kubernetes-specific service discovery code.

| Workload | Runtime | Internal port | Probe |
| --- | --- | ---: | --- |
| web | nginx | 8080 | `/healthz` |
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
- Helm 3 or Helm 4 (local scripts select the matching rollback flag)
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

## Clean local installation

```powershell
.\infra\kubernetes\scripts\create-local-cluster.ps1
.\infra\kubernetes\scripts\install-local-addons.ps1
.\infra\kubernetes\scripts\build-images.ps1 -Tag dev
.\infra\kubernetes\scripts\load-images.ps1 -Tag dev
.\infra\kubernetes\scripts\deploy-local.ps1 -Tag dev
.\infra\kubernetes\scripts\verify-cluster.ps1
.\infra\kubernetes\tests\smoke-tests.ps1
.\infra\kubernetes\tests\smoke-tests.ps1 -Workflow
```

The local PostgreSQL StatefulSet creates separate users and databases for every owning service.
It is disposable test infrastructure, not a production database design. See `RECOVERY.md` for
rollback, restore, rotation, certificate, pod, and incident procedures.

RabbitMQ uses AMQP TCP startup/readiness checks and container exit handling, following
[RabbitMQ's Kubernetes probe guidance](https://www.rabbitmq.com/docs/monitoring#health-checks).
It avoids repeatedly launching Erlang diagnostic processes and killing a healthy broker when CPU
is busy. PostgreSQL and Redis have bounded startup probes and five-second command probe timeouts.
Local application startup probes allow ten minutes for concurrent Java cold starts on shared
Docker Desktop CPUs. Production retains a 150-second window. Configure `probes.startup` for the
target capacity; Deployment progress deadlines include time beyond the startup window.
Gateway probes use the dedicated Spring readiness/liveness groups. Its aggregate health endpoint
checks downstream services and remains available for diagnostics without controlling gateway restarts.

Start Docker before running these commands. The deployment and Secret scripts require the
`kind-swiftdrop` context and preserve existing local credentials when rerun. Existing Secrets
created before object storage support need `MINIO_ACCESS_KEY` and `MINIO_SECRET_KEY` added
through your local secret tooling; the script intentionally does not replace existing credentials.
`build-images.ps1` also builds the two tagged MinIO source targets already used by parcel Compose.
Use the same `-Tag` for build, load, and deploy; Git SHA tags are suitable for repeatable local builds.

The image loader exports one platform-filtered archive on modern Docker engines, avoiding Kind's
`content digest ... not found` error with the containerd image store, following
[Kind's workaround](https://kind.sigs.k8s.io/docs/user/known-issues/#unable-to-kind-load-docker-images).
It detects the Docker engine
architecture; `load-images.ps1 -Platform linux/amd64` can override it for an emulated cluster.
Older Docker engines use Kind's batched `docker-image` loader.

If a Kind node cannot pull the supporting images because of network or TLS errors, pull them through
Docker Desktop and import them without changing registry security settings:

```powershell
$dependencies = @('postgres:17.6-alpine', 'redis:7.4-alpine',
    'rabbitmq:4.1-management-alpine', 'axllent/mailpit:v1.27')
foreach ($image in $dependencies) {
    docker pull $image
    if ($LASTEXITCODE -ne 0) { throw "Download failed: $image" }
}
.\infra\kubernetes\scripts\import-kind-images.ps1 -Images $dependencies
```

Cluster creation allows ten minutes for readiness and retains failed SwiftDrop nodes for inspection.
The ingress installation allows fifteen minutes for a cold image download; adjust `-WaitSeconds`
on `create-local-cluster.ps1` or `-TimeoutMinutes` on `install-local-addons.ps1` when needed.

With Java 21/Javac installed, `build-images.ps1 -UseHostJavaBuild -Tag dev` freshly compiles the
Java reactor on the host and packages the same executable artifacts into non-root Linux runtime
images. This avoids downloading a container JDK for local validation. Release CI continues to use
the service-specific multi-stage Dockerfiles. Both build paths use the checked-in Maven Central
mirror settings to avoid obsolete authenticated repositories inherited from dependencies.

If node image pulls fail because Docker Desktop's bridge DNS gateway times out, run
`configure-local-dns.ps1 -DnsServers @('8.8.8.8','8.8.4.4')` after cluster creation, using DNS server
IPs reachable on your network. The script changes only SwiftDrop Kind nodes and restarts only
that cluster's CoreDNS; it verifies container ownership before writing resolver configuration.

On the first installation, `deploy-local.ps1` installs the same release with `bootstrapOnly=true`.
This starts PostgreSQL, Redis, RabbitMQ, Mailpit, and private MinIO and waits for bucket creation,
without creating application Deployments, Ingress, or migration hooks. It then upgrades with
`bootstrapOnly=false`: pre-upgrade migrations must complete before any application is rolled out.
Subsequent invocations skip bootstrap and upgrade the application directly. Do not set
`bootstrapOnly=true` on an existing application release: it removes application resources.

Open `http://localhost:8088` in a browser; no hosts-file changes are required. The optional alias
`http://swiftdrop.local:8088` also works after adding `127.0.0.1 swiftdrop.local` to your hosts file.
The smoke test supplies the Host header and does not require the hosts entry. `-Workflow` creates
a synthetic local customer and checks login, pricing, booking idempotence, public tracking,
notifications, cancellation, assistant SSE, and persisted assistant history. For browser-accessible
signed proof URLs in local development, run `kubectl --context kind-swiftdrop -n swiftdrop
port-forward service/minio 9000:9000`. MinIO remains a private ClusterIP Service. Production must
configure `objectStorage` endpoints and pre-provision its private bucket; credentials come from
`MINIO_ACCESS_KEY` and `MINIO_SECRET_KEY` in the existing Secret.

## Image publishing and deployment

The release workflow builds every image once, publishes it under the immutable Git SHA, attaches
BuildKit provenance/SBOM data, scans it, and signs its digest through GitHub OIDC. Production
deployment runs only on an environment-protected runner labelled `kubernetes-deployer`. That
runner is expected to be a Kubernetes pod with a short-lived projected ServiceAccount token and
least-privilege Helm permissions; the workflow creates an ephemeral context from that token and
stores no long-lived kubeconfig credential.

`deployer-rbac.yaml` is the namespace-scoped bootstrap policy for that runner. A platform
administrator applies it once. The runner pod must explicitly mount a projected, short-lived
token for `github-deployer`; the ServiceAccount disables automatic token mounting.

Helm uses `--atomic --wait` (Helm 3) or `--rollback-on-failure --wait` (Helm 4), so failed Jobs or readiness checks roll the release back. GitHub's
production Environment must require human approval and restrict which tags can deploy.

## Continuous validation

The Kubernetes workflow lints and renders local and production values, checks required workload
controls, validates standard schemas with pinned kubeconform, scans configuration for high/critical
findings, and runs secret detection. Rendered manifests are temporary CI artifacts and must not
contain real Secret values.

Run the repository checks locally with:

```powershell
.\infra\kubernetes\tests\helm-render-tests.ps1
.\infra\kubernetes\tests\local-script-tests.ps1
python -m pip install pytest==8.4.2 PyYAML==6.0.3
python -m pytest infra/kubernetes/tests/test_chart.py -q
.\mvnw.cmd -B -f infra/kubernetes/migrations/pom.xml test
```

The Java harness uses H2 to verify process completion, idempotence, invalid SQL, missing
configuration, and redacted failures. It does not validate the services' PostgreSQL SQL dialects.
The script tests replace cluster commands with local mocks; full install and recovery checks
still require a running Kind cluster. Run the assistant checks from its service directory as
documented in `services/ai-assistant-service/README.md`.

Local validation on 16 November 2026 used Kind/Kubernetes 1.35.0, Docker 29.5.3, and Helm 4.1.4.
All ten applications became ready. Customer workflow tests passed through Ingress, including a
sent booking email, assistant SSE, and persisted history after an assistant rolling restart.
Database migration Jobs also succeeded when rerun against the existing schemas. The notification
consumer now uses Boot's Jackson 3 mapper so event timestamps and typed payloads decode correctly;
CI covers that regression and anonymous probe access without exposing other Actuator paths.

The recorded local deployment is Helm revision 7. Most application images use
`k8s-3aedc20-local`; gateway uses `k8s-3aedc20-probes1` and notification uses
`k8s-3aedc20-notifyfix1`. Future full builds should use a new common tag for the documented scripts.

This verifies the local deployment and the tested recovery paths. A complete final-source
fresh-cluster rerun, additional role workflows, production connectivity, fault/load tests,
enforced network isolation, and restore verification remain separate work. Review the frontend's
existing npm audit findings before any production release; local build success is not an image
vulnerability assessment. Build future changes under a new tag before loading and deploying them.

## Observability

When `monitoring.enabled=true`, the chart emits Prometheus Operator `ServiceMonitor` and
`PrometheusRule` resources, a Grafana dashboard ConfigMap, and a NetworkPolicy allowing the
monitoring namespace to scrape application ports. Alerts cover unavailable replicas, repeated
restarts, and RabbitMQ backlog. Application logs must remain structured and redacted; never add
JWTs, credentials, addresses, phone numbers, or raw assistant prompts to cluster log labels.

## Availability and scaling

Replicated application pods are spread across nodes and prefer not to colocate. Production values
enable PodDisruptionBudgets and CPU-based `autoscaling/v2` HPAs for web, gateway, identity,
parcel, and assistant workloads. HPA scale-down is intentionally slow, and the assistant maximum
must stay within Gemini and downstream service quotas. Metrics Server is required before enabling
autoscaling.

## Network isolation

NetworkPolicy starts with default-deny, permits cluster DNS, permits traffic among pods belonging
to the same Helm release, and permits the configured Ingress namespace to reach only web and
gateway. Explicit external egress ports cover HTTPS APIs, PostgreSQL, and authenticated SMTP.
Production must use a CNI that enforces NetworkPolicy and should additionally restrict external
destinations through an egress gateway/firewall because standard NetworkPolicy cannot select a
public service by DNS name.

## Ingress

Only `web` and `gateway` are externally routed. `/api` and `/ws` go to the gateway; all other
paths go to the SPA. NGINX buffering is disabled and one-hour stream timeouts preserve assistant
SSE and tracking WebSocket connections. Production values must enable TLS and provide a
certificate Secret through cert-manager or the cloud ingress integration.

## Database migrations

The Helm release runs bounded `pre-install,pre-upgrade` migration Jobs. Java images include a
small Flyway runner compiled against their own packaged dependencies; it exits without starting
Spring, RabbitMQ listeners, schedules, or HTTP servers. Database secret keys use the owning service
prefix, such as `IDENTITY_DB_URL`. Application replicas set `SPRING_FLYWAY_ENABLED=false`.
The AI assistant runs `python -m app.migrate` for Alembic and LangGraph checkpoints, and runtime
replicas set `CHECKPOINT_AUTO_SETUP=false`. Compose retains its existing migration behavior.

Migration Jobs have dedicated hook ServiceAccounts, registry pull-secret references, no dependency
on not-yet-created application ConfigMaps, and a hook NetworkPolicy permitting DNS and PostgreSQL
egress. PostgreSQL must be reachable on port 5432. Existing production database credentials and
image pull Secrets must be provisioned before the first install. The hooks run before workload
changes, including upgrades from releases that did not deploy a newly enabled service previously.
Jobs retry once, time out, and are removed after success. Failed Jobs remain for up to one day.
A failed Job fails an atomic Helm operation; inspect
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
`https://localhost:18443`. Delete the disposable cluster explicitly with:

```powershell
.\infra\kubernetes\scripts\destroy-local-cluster.ps1
```
