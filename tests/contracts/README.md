# Non-AI contract suite

Run `./tests/contracts/run-non-ai.ps1` from the repository root. The suite deliberately excludes the AI Assistant and exercises the public/service contracts for Identity, Parcel, Dispatch, Merchant, Tracking, Notification, Pricing, and Gateway.

The required matrix is covered by the module suites:

| Boundary | Required assertions |
|---|---|
| Identity | user/admin RBAC, named service client and exact scope, additive merchant grant |
| Parcel | customer/merchant ownership, courier assignment, idempotent booking, status V2 |
| Dispatch | dispatcher/courier roles, assignment ownership, duplicate projection events |
| Merchant | approval reconciliation, key scopes, order ownership, webhook duplicate/replay |
| Tracking | public/private representations, duplicate and out-of-order projections |
| Notification | customer recipient, preferences, event/channel/recipient idempotency |
| Pricing | authenticated estimates and admin-only mutations |
| Gateway | JWT claims, public route table, internal denial, CORS and trusted headers |

Tests must use isolated fixtures and must not require a shared external database unless explicitly marked as a live integration test.

Docker Desktop (or another Testcontainers-compatible Docker daemon) must be running.
Several service suites provision isolated PostgreSQL and RabbitMQ containers and stop
during test initialization when no Docker environment is available.
