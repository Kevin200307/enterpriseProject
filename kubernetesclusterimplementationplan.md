# SwiftDrop Kubernetes Cluster Implementation Plan

## Objective

Add a secure, repeatable Kubernetes deployment for the complete SwiftDrop platform while
keeping Docker Compose as the default local-development environment. Use one Helm chart to
deploy the web application, gateway, Java domain services, Python AI assistant, Redis, and
RabbitMQ. Use external managed PostgreSQL and secret storage in production.

The Kubernetes implementation must preserve the existing service DNS names, ports, gateway
routes, JWT contract, AI Assistant SSE streaming, health endpoints, and database-per-service
boundaries.

## Scope

Applications deployed as stateless Kubernetes Deployments:

- `web`
- `gateway`
- `identity-service`
- `parcel-service`
- `pricing-service`
- `dispatch-service`
- `merchant-service`
- `tracking-service`
- `notification-service`
- `ai-assistant-service`

Supporting workloads:

- Redis
- RabbitMQ
- Optional Mailpit for local clusters only
- One migration Job per service that owns database migrations
- Ingress controller and TLS integration
- Metrics Server for HorizontalPodAutoscaler

Production PostgreSQL should be managed outside the cluster. Local Kubernetes may use a
development PostgreSQL chart, but it must not be presented as the production architecture.

## Non-negotiable rules

1. Do not remove or break `infra/docker-compose.yml`.
2. Do not commit credentials, rendered Secrets, kubeconfig files, private keys, or registry
   tokens.
3. Use immutable image tags based on Git commit SHA or release version; do not deploy `latest`.
4. Keep internal Kubernetes Service names equal to existing Compose service names.
5. Expose only `web` and `gateway` through Ingress.
6. Keep all domain services and data infrastructure on `ClusterIP` services.
7. Preserve SSE by disabling proxy buffering and configuring suitable timeouts.
8. Run containers as non-root and define CPU/memory requests and limits.
9. Apply database migrations as Jobs before application rollout, never concurrently from every
   replica.
10. Use readiness, liveness, and startup probes appropriate to each runtime.
11. Enforce authorization in the applications; Kubernetes networking is an additional boundary,
    not a replacement for JWT/RBAC validation.
12. Production secrets must come from a secret manager through External Secrets or an equivalent
    controlled mechanism.

## Target repository structure

```text
infra/
  kubernetes/
    README.md
    kind/
      cluster.yaml
    helm/
      swiftdrop/
        Chart.yaml
        values.yaml
        values.schema.json
        values-local.yaml
        values-production.example.yaml
        .helmignore
        templates/
          _helpers.tpl
          serviceaccounts.yaml
          configmap.yaml
          deployments.yaml
          services.yaml
          migrations.yaml
          ingress.yaml
          networkpolicies.yaml
          poddisruptionbudgets.yaml
          horizontalpodautoscalers.yaml
          redis.yaml
          rabbitmq.yaml
          mailpit.yaml
          NOTES.txt
    scripts/
      create-local-cluster.ps1
      load-images.ps1
      verify-cluster.ps1
      destroy-local-cluster.ps1
    tests/
      helm-render-tests.ps1
      smoke-tests.ps1
```

## Environment strategy

### Local cluster

- Kind or Docker Desktop Kubernetes.
- Images built locally and loaded into Kind.
- Local Redis, RabbitMQ, Mailpit, and development PostgreSQL.
- HTTP Ingress using a local hostname or port-forwarding.
- One replica by default to reduce resource usage.
- Fake/test Gemini configuration unless the developer explicitly supplies a key.

### Production cluster

- Managed Kubernetes such as EKS, AKS, or GKE.
- Images pulled from a private registry.
- Managed PostgreSQL and preferably managed Redis/message infrastructure.
- External Secrets connected to the cloud secret manager.
- TLS through cert-manager or the cloud load balancer certificate integration.
- At least two replicas for gateway, web, identity, parcel, and AI assistant where capacity
  permits.
- PodDisruptionBudgets, topology spread, autoscaling, monitoring, alerting, and backups.

## Commit-by-commit implementation

### Commit 1 — Establish Kubernetes documentation and validation tooling

Commit message:

`docs(k8s): define SwiftDrop cluster architecture and prerequisites`

Changes:

- Add `infra/kubernetes/README.md`.
- Document kubectl, Helm, Kind, Docker, ingress controller, and Metrics Server prerequisites.
- Document local versus production responsibilities.
- Document service ports and health endpoints based on the current applications.

Verification:

- Every service in Compose appears in the Kubernetes inventory.
- Documentation contains no real credentials or kubeconfig content.

### Commit 2 — Create the base Helm chart

Commit message:

`build(k8s): add base SwiftDrop Helm chart`

Changes:

- Add `Chart.yaml`, `.helmignore`, `values.yaml`, helpers, standard labels, selectors, and naming.
- Add `values.schema.json` to reject missing image repositories/tags and invalid replica counts.
- Keep environment-specific values outside shared templates.

Verification:

- `helm lint infra/kubernetes/helm/swiftdrop` passes.
- `helm template` produces valid YAML.

### Commit 3 — Add local Kind cluster configuration

Commit message:

`build(k8s): add reproducible local Kind cluster`

Changes:

- Add a pinned Kind cluster configuration.
- Map host HTTP/HTTPS ports for Ingress.
- Add scripts to create, inspect, and destroy only the named SwiftDrop cluster.
- Do not automatically delete unrelated clusters.

Verification:

- Cluster creation is repeatable.
- `kubectl cluster-info` and node readiness checks pass.

### Commit 4 — Add shared ConfigMaps and secret interfaces

Commit message:

`feat(k8s): add application configuration and secret references`

Changes:

- Convert non-sensitive Compose environment values into ConfigMaps/Helm values.
- Reference an existing Kubernetes Secret for database URLs, JWT keys, OAuth, RabbitMQ, Redis,
  email, and Gemini credentials.
- Add an example secret key list containing placeholders only.
- Add checksum annotations so configuration updates roll pods safely.

Verification:

- Rendered manifests contain Secret references but no credential values.
- A repository secret scan passes.

### Commit 5 — Add Redis and RabbitMQ for local clusters

Commit message:

`feat(k8s): deploy local Redis and RabbitMQ dependencies`

Changes:

- Add local-only Redis and RabbitMQ StatefulSets or pinned chart dependencies.
- Add PersistentVolumeClaims, Services, probes, resource limits, and password Secret references.
- Keep production values able to disable in-cluster data services and use external endpoints.

Verification:

- Redis PING succeeds from a temporary in-cluster pod.
- RabbitMQ reports ready and retains data across pod recreation in the local cluster.

### Commit 6 — Add service accounts and pod security defaults

Commit message:

`security(k8s): add least-privilege workload identities`

Changes:

- Use dedicated ServiceAccounts without automatic API token mounting unless required.
- Set non-root user, seccomp profile, dropped capabilities, and no privilege escalation.
- Use read-only root filesystems where each image supports them.
- Add writable temporary volumes only where required.

Verification:

- Policy checks reject privileged/root workloads.
- Services start with the declared security contexts.

### Commit 7 — Deploy identity and gateway

Commit message:

`feat(k8s): deploy identity service and gateway`

Changes:

- Add Deployments and ClusterIP Services.
- Configure startup, readiness, and liveness probes.
- Preserve identity issuer/JWKS/audience configuration.
- Preserve trusted-header and Redis gateway configuration.

Verification:

- JWKS and gateway health checks pass inside the cluster.
- Gateway cannot become ready before required configuration is available.

### Commit 8 — Deploy parcel, pricing, and tracking services

Commit message:

`feat(k8s): deploy parcel pricing and tracking services`

Changes:

- Add Deployments, Services, probes, resources, and configuration.
- Preserve database-per-service URLs and RabbitMQ integration.
- Use rolling updates without changing service DNS names.

Verification:

- Internal health checks pass.
- Pricing estimate and public tracking smoke tests pass through the gateway.

### Commit 9 — Deploy dispatch, merchant, and notification services

Commit message:

`feat(k8s): deploy dispatch merchant and notification services`

Changes:

- Add Deployments, Services, probes, resources, and configuration.
- Preserve RabbitMQ, email, identity, parcel, and webhook dependencies.
- Make Mailpit available only when local values enable it.

Verification:

- Services resolve each other through Kubernetes DNS.
- Notification and dispatch integration smoke tests pass.

### Commit 10 — Deploy the Python AI assistant

Commit message:

`feat(k8s): deploy LangGraph AI assistant service`

Changes:

- Add Deployment and ClusterIP Service on port 8080.
- Configure Gemini, PostgreSQL, checkpoint PostgreSQL, Redis, JWKS, and downstream URLs.
- Use `/actuator/health` for probes.
- Preserve the `ai-assistant-service` DNS name.
- Add a conservative replica count and resource limits suitable for the Gemini free tier.

Verification:

- Health endpoint passes.
- JWT-protected SSE stream works through the gateway.
- No Gemini or JWT secret appears in manifests or logs.

### Commit 11 — Add database migration Jobs

Commit message:

`feat(k8s): run service database migrations as controlled jobs`

Changes:

- Add migration Jobs or Helm hooks for Flyway/Liquibase services and Alembic assistant tables.
- Use distinct ServiceAccounts and database secrets.
- Set timeouts, retry limits, and cleanup policy.
- Block application rollout when required migrations fail.

Verification:

- Fresh database upgrade succeeds.
- Re-running migrations is safe.
- A deliberately failing migration prevents rollout.

### Commit 12 — Deploy the frontend and Ingress

Commit message:

`feat(k8s): expose SwiftDrop through web and gateway ingress`

Changes:

- Add web Deployment and Service.
- Add Ingress routes for frontend and `/api` gateway traffic only.
- Configure SSE proxy buffering off and suitable read/send timeouts.
- Add configurable TLS and host names.

Verification:

- Browser application loads through Ingress.
- Login, ordinary APIs, and assistant token streaming work without exposing internal services.

### Commit 13 — Add NetworkPolicies

Commit message:

`security(k8s): restrict SwiftDrop network communication`

Changes:

- Default-deny ingress and egress in the application namespace.
- Permit DNS.
- Permit Ingress to web/gateway and explicitly required service-to-service flows.
- Permit the assistant to reach only Gemini HTTPS, Redis, its databases, JWKS, and approved
  downstream services.
- Permit database/message access only from owning services.

Verification:

- Required smoke tests pass.
- Unauthorized pod-to-service and direct external access tests fail.

### Commit 14 — Add availability and autoscaling policies

Commit message:

`feat(k8s): add disruption budgets and autoscaling`

Changes:

- Add PodDisruptionBudgets for replicated critical workloads.
- Add topology spread constraints and anti-affinity.
- Add `autoscaling/v2` HPAs after resource requests are proven.
- Set conservative assistant scaling so Gemini quota and downstream services remain protected.

Verification:

- HPA reads Metrics Server data.
- Controlled load changes replica count within configured boundaries.
- Node drain preserves available gateway and identity replicas.

### Commit 15 — Add observability

Commit message:

`chore(k8s): add cluster monitoring and operational dashboards`

Changes:

- Add Prometheus scrape configuration/ServiceMonitors where supported.
- Add dashboards and alerts for availability, latency, errors, restarts, queue depth, database
  pool exhaustion, assistant rate limits, and Gemini failures.
- Centralize structured logs without collecting credentials or sensitive chat content.

Verification:

- Metrics targets are healthy.
- Test alerts fire and resolve.
- Log review confirms redaction.

### Commit 16 — Add Kubernetes CI validation

Commit message:

`ci(k8s): validate Helm manifests and policies`

Changes:

- Run Helm lint and render for local and production example values.
- Validate manifests with kubeconform or equivalent pinned tooling.
- Run policy/security scanning and secret scanning.
- Detect mutable tags, missing probes, missing resources, privileged containers, and public
  internal services.

Verification:

- CI rejects intentionally invalid manifests.
- CI requires no live cluster or production secrets for render validation.

### Commit 17 — Add image publishing and deployment pipeline

Commit message:

`ci(k8s): publish immutable images and deploy Helm releases`

Changes:

- Build each changed service image and push it using Git SHA tags.
- Generate provenance/SBOM and scan images.
- Authenticate to the cluster using workload identity/OIDC, not long-lived kubeconfig secrets.
- Deploy development automatically and production through an approval gate.
- Use `helm upgrade --install --atomic --wait` with an explicit timeout.

Verification:

- A failed readiness check rolls back the release.
- Git SHA in deployed image matches the release record.

### Commit 18 — Add full cluster smoke and recovery tests

Commit message:

`test(k8s): verify cluster workflows and recovery behavior`

Changes:

- Add smoke tests for registration/login, pricing, parcel creation, tracking, merchant, dispatch,
  notification, and AI assistant SSE.
- Test pod restart, rolling update, Redis/RabbitMQ restart, failed migration, and assistant disable.
- Document database restore, secret rotation, certificate renewal, rollback, and cluster incident
  response.

Verification:

- Full local cluster test passes from a clean Kind cluster.
- Production manifests render and pass policy validation.
- Assistant can be disabled without affecting core SwiftDrop APIs.

## Image and release rules

- Build images once and promote the exact digest between environments.
- Set both a readable version tag and an image digest in production values.
- Record chart version, application version, Git SHA, migration versions, and image digests for
  every release.
- Do not rebuild an existing release tag.
- Use rolling updates with `maxUnavailable: 0` for critical replicated HTTP services.

## Secrets required by Kubernetes

The implementation should define references for at least:

- service-specific PostgreSQL URLs/usernames/passwords
- Redis password or managed Redis URL
- RabbitMQ username/password or managed broker URL
- identity JWT private/public key material
- Google OAuth client ID and client secret
- Gemini `GOOGLE_API_KEY`
- email credentials
- webhook signing/encryption material if configured
- private registry pull credential only when workload identity cannot be used

The example values file must include key names but no usable values.

## Verification commands

Expected commands after implementation:

```powershell
helm lint infra/kubernetes/helm/swiftdrop
helm template swiftdrop infra/kubernetes/helm/swiftdrop `
  --values infra/kubernetes/helm/swiftdrop/values-local.yaml
kubectl apply --dry-run=server -f rendered.yaml
helm upgrade --install swiftdrop infra/kubernetes/helm/swiftdrop `
  --namespace swiftdrop --create-namespace `
  --values infra/kubernetes/helm/swiftdrop/values-local.yaml `
  --atomic --wait
kubectl get pods,services,ingress,jobs -n swiftdrop
kubectl rollout status deployment/gateway -n swiftdrop
kubectl rollout status deployment/ai-assistant-service -n swiftdrop
```

## Definition of done

- A clean local cluster can be created and the complete application installed using documented
  commands.
- All application images are versioned and deployable from a registry.
- All services become ready and communicate through stable Kubernetes DNS names.
- Only web and gateway are publicly exposed.
- Login, parcel, pricing, tracking, merchant, dispatch, notification, and assistant SSE workflows
  pass through Ingress.
- Database migrations run exactly once per release and block unsafe rollout.
- Secrets are never committed or rendered into CI artifacts.
- NetworkPolicies restrict traffic to documented paths.
- Probes, resources, PodDisruptionBudgets, and appropriate autoscaling are configured.
- Production values use managed stateful services and external secret management.
- CI validates manifests, scans images, and deploys immutable versions with rollback protection.
- Docker Compose continues to work for local development.
