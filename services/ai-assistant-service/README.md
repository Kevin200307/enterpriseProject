# SwiftDrop AI Assistant Service

Python 3.12 service that preserves the SwiftDrop assistant HTTP/SSE contract while using
FastAPI, LangGraph, and Gemini. The service remains reachable as `ai-assistant-service:8080`.

## Runtime design

```mermaid
flowchart LR
    Web[Chat widget] -->|JWT + SSE| API[FastAPI]
    API --> Guard[JWT and role guards]
    Guard --> Graph[LangGraph router]
    Graph --> Agents[Role-scoped agents]
    Agents --> Tools[Typed tools]
    Tools --> Java[Existing Java services]
    Graph --> Gemini[Gemini Flash Lite]
    API --> SQL[(PostgreSQL)]
    Graph --> CP[(LangGraph checkpoints)]
    API --> Redis[(Redis limits)]
```

The graph routes to Support, Tracking, Customer, Merchant, Courier, Dispatcher, or Admin.
Deterministic code validates the JWT and chooses the allowed agent/tool set before Gemini is
called. Gemini receives no database connection and no unrestricted HTTP client. Every
downstream call forwards the caller's bearer token, so the owning Java service remains the
final authorization boundary.

Read tools cover tracking, estimates, customer parcels, merchant orders/webhooks, courier
assignments, dispatch queues/workloads/recommendations, users, merchants, pricing, and failed
notifications. Write tools prepare a server-side operation first. Customer booking and
cancellation, dispatch assignment changes, and administrator mutations require an unexpired
confirmation, matching owner and current role, immutable payload hash, and one-time claim.

## Local development

From this directory, with Python 3.12 installed:

```powershell
python -m venv .venv
.venv\Scripts\python -m pip install -e ".[dev]"
$env:ASSISTANT_ENABLED = "true"
$env:GOOGLE_API_KEY = "your-development-key"
.venv\Scripts\python -m uvicorn app.main:app --reload --port 8080
```

The assistant starts without a Gemini key; deterministic capabilities remain available and
model-backed responses degrade safely. Set `ASSISTANT_ENABLED=false` to make assistant routes
return the disabled response without affecting other SwiftDrop services. The frontend widget
can independently be hidden with its assistant feature flag.

Run checks with:

```powershell
.venv\Scripts\python -m ruff check app tests migrations
.venv\Scripts\python -m mypy app migrations
.venv\Scripts\python -m pytest
```

## Configuration

Important variables are `GOOGLE_API_KEY`, `GEMINI_MODEL`, `ASSISTANT_DATABASE_URL`,
`CHECKPOINT_DATABASE_URL`, `REDIS_URL`, `IDENTITY_JWKS_URI`, `IDENTITY_ISSUER_URI`, and
`JWT_AUDIENCE`. Service URLs for identity, parcel, pricing, merchant, dispatch, and notification
must be trusted deployment configuration. Never put credentials in frontend variables or logs.

Defaults target `gemini-2.5-flash-lite`, 500 output tokens, two model calls, three tool calls,
six recent messages, and 20 requests per user per hour. Change a model only after running unit,
contract, SSE, role-authorization, and optional `live_gemini` tests. Pin and review dependency
updates in `pyproject.toml`; do not perform unbounded automatic upgrades.

## Persistence and migrations

Set `ASSISTANT_DATABASE_URL` to use durable SQLAlchemy conversation/audit storage and
`CHECKPOINT_DATABASE_URL` to resume LangGraph threads by conversation UUID. Both use async
PostgreSQL: the application URL uses `postgresql+asyncpg://user:password@host/database`, while
the checkpoint URL uses the psycopg form `postgresql://user:password@host/database`. Run application
migrations before deployment:

```powershell
.venv\Scripts\alembic upgrade head
```

Supply `ASSISTANT_DATABASE_URL` through the deployment secret manager; never commit it.
LangGraph creates its checkpoint schema through the controlled application startup
path by default for Compose and standalone development. Kubernetes sets `CHECKPOINT_AUTO_SETUP=false`
and runs both schemas once through a bounded Helm migration Job before starting replicas:

```powershell
.venv\Scripts\python -m app.migrate
```

This command requires both database URLs, runs Alembic before LangGraph checkpoint setup, and
exits nonzero if either fails. Runtime replicas connect to the existing checkpoint schema without
performing schema changes. Conversation retention defaults to 30 days; checkpoint and audit pruning should use the
same environment retention policy. Back up application tables independently of checkpoint
tables.

## Operations and incident response

- Health: `GET /actuator/health`; metrics: `GET /metrics`.
- For model quota or latency incidents, unset `GOOGLE_API_KEY` or disable the assistant. Core
  delivery APIs remain available.
- For suspicious tool activity, disable the assistant, preserve redacted audit records, revoke
  affected user sessions in identity-service, and inspect downstream service audit logs.
- Never log chat secrets, JWTs, addresses, phone numbers, API keys, or hidden model reasoning.
- A failed write is not automatically retried with a new payload. Re-read target state, prepare
  a new operation, and ask the user to confirm it.
- Debug with request ID, conversation ID, selected agent, tool name, downstream status, and
  operation ID. Do not copy bearer tokens into tickets.

Production deployment order is database migration, Redis/PostgreSQL readiness, assistant
rollout, health verification, gateway SSE smoke test, then frontend enablement. Rollback by
setting `ASSISTANT_ENABLED=false` or restoring the previous image; schema changes are isolated
to assistant-owned tables.
