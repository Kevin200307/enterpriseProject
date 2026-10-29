# SwiftDrop Role-Based End-to-End Integration Plan

## 1. Purpose

This is the execution plan for making every supported SwiftDrop role work end-to-end across the web UI, gateway,
backend services, messaging, and databases. It is intentionally a verification and delivery plan, not a claim that
every workflow is currently complete. Some contracts and service integrations have already been implemented; each
must still pass the real-stack acceptance checks below before it is considered done.

Companion references:

- [Frontend contracts](./FRONTEND_CONTRACTS.md) — request/response assumptions and known contract questions.
- [Frontend plan](./FRONTEND_PLAN.md) — UI structure, route behavior, and frontend testing conventions.
- [Service architecture](./SWIFTDROP.md) — service boundaries, security, and event flows.

## 2. Definition of done

The integration is complete only when:

1. Every visible UI action for every supported role reaches the owning service through the gateway.
2. The browser request, gateway route, service authorization, DTO, database operation, and returned UI state agree.
3. Each stateful service uses its assigned database branch and its own Flyway migrations; no service reads another
   service's database directly.
4. Cross-service changes complete reliably through documented HTTP or RabbitMQ contracts, with retries,
   idempotency, dead-letter handling, and user-visible pending/error states where needed.
5. Authentication, refresh, CORS, role checks, ownership checks, and privacy guarantees pass positive and negative
   tests.
6. Customer, merchant, courier, dispatcher, and admin journeys pass using real browser requests and the configured
   local Compose stack, not only mocked frontend tests.
7. Failures are diagnosable through request IDs, service logs, health checks, and actionable UI errors.

## 3. Execution status

Last verified: **2026-10-29** (local Compose stack).

| Phase | Status | Evidence / remaining work |
|---|---|---|
| 0 — Baseline | **Passed for local stack** | Full Compose stack running; gateway aggregate health and all eight downstream health indicators `UP`; web root, public tracking, SockJS info, and CORS preflights for ports 5173/5174 verified. Rebuilt gateway/web images serve current assets; the browser tracking fetch now succeeds with exactly one allowed-origin and credentials header. Anonymous profile access returns `401`; invalid registration returns expected validation `400`. |
| 1 — Database foundation | **Passed for current local configuration** | Sanitized running-service DB hosts match the documented six Neon service branches. Flyway startup logs for all six owners report current schemas (dispatch v1, identity v2, merchant v3, notification v3, parcel v4, pricing v2). No secrets or credentials are recorded here. |
| 2 — Identity and authorization | **Partially verified** | Identity registration/token-flow and RBAC tests passed (62 tests); internal user lookup authorization was aligned with its documented ADMIN access and focused tests pass. Need real browser sessions/tokens for each role and live refresh/role-change verification. |
| 3 — Pricing and customer parcel journey | **Partially verified** | Pricing authorization/engine/service tests passed (12); parcel API integration tests passed (19, one skipped); full frontend suite passed (211). In the shared authenticated customer session, the home page and an existing owned delivery detail loaded successfully. Estimate and booking have not been tested end-to-end; no live data was mutated. |
| 4 — Events and live tracking | **Partially verified** | Tracking listener tests passed (4); public lookup for `SD-00000001` returned `200`, and the shared browser rendered its tracking status and timeline after the CORS fix. Replaced the blocked SockJS iframe transport with native STOMP over WebSocket; in the shared browser, after simulating a visible tab because the shared page is backgrounded, the gateway returned STOMP `CONNECTED`, the UI subscribed to `/topic/parcels.SD-00000001`, and the indicator showed `Live`. Still verify event delivery and status transitions end-to-end through RabbitMQ, including a newly created parcel, and exercise SSE/polling fallback behavior. |
| 5–10 — Role workflows and release | **Not started** | Require role-by-role real-stack journeys, downstream side-effect checks, and operational/release regression. |

Baseline fixes made while starting this plan:

- Raised gateway downstream health-probe timeout to accommodate measured healthy service probes (up to 2.6 seconds in this local stack); aggregate health now reports `UP`.
- Corrected the gateway's default notification-service target to port `8086`, matching the service listener.
- Aligned admin access for the internal user lookup endpoint with its documented authorization and adjusted the role test to send a valid request before asserting access denial.
- Corrected frontend tests to operate the custom accessible select controls, added the incidental authenticated `/users/me` test handler, and aligned customer sign-in expectations with the implemented `/home` landing route.
- Deduplicated gateway CORS response headers, covered the proxy behavior with a regression test, and rebuilt/recreated the gateway and web containers so the browser uses the current assets. The focused gateway health/CORS tests pass (2); live tracking returns `200` with one `Access-Control-Allow-Origin` header.
- Replaced the public tracking WebSocket's SockJS client with native STOMP WebSocket, avoiding SockJS iframe requests blocked by the gateway's frame-security policy. The existing SSE and polling fallbacks remain; frontend typecheck, tracking/realtime tests, and production build pass.
- Frontend production build, typecheck, and all 211 UI tests pass. Pricing tests (12), identity/auth tests (62), parcel API tests (19; one skipped), and tracking listener tests (4) pass. Build output includes upstream Zod annotation warnings only.

These are local verification results, not release certification. In particular, passing mocked UI tests does not establish that all five role journeys work against live service data.

## 4. Architecture and data ownership

| Component | Owns | Persistence / integration rule |
|---|---|---|
| Web SPA | Role-aware screens, API clients, client-side validation, loading/error states | Calls the gateway URL supplied by runtime config; never connects directly to Neon or an internal service. |
| Gateway | Browser entry point, JWT validation, CORS, rate limits, routes, normalized errors, dashboard aggregation | Uses Redis for rate limiting; forwards to service APIs. |
| Identity service | Accounts, roles, refresh tokens, merchant/courier identity lifecycle, JWKS | Identity Neon branch; owns sign-in, token, profile, and admin user operations. |
| Parcel service | Bookings, parcel state, parcel history, proof metadata, receipts, outbox | Parcel Neon branch; owns parcel records and emits events after committed changes. MinIO stores proof objects. |
| Pricing service | Zones, city mappings, pricing rules, estimates | Pricing Neon branch; owns all pricing data and migrations. |
| Dispatch service | Courier registry/availability, queue projections, workloads, assignments and command state | Dispatch Neon branch; consumes parcel events and invokes authorized service APIs for assignment changes. |
| Merchant service | Merchant applications, approval state, API keys, order references, webhook configuration/delivery history | Merchant Neon branch; owns merchant data and delivery retries. |
| Notification service | Notification preferences, delivery records, retry state | Notification Neon branch; consumes events and sends through configured email/SMS adapters. |
| Tracking service | Public/role-scoped read models and real-time updates | Redis projection; consumes RabbitMQ events and can backfill a privacy-safe snapshot from parcel-service. No separate Neon branch is currently required. |
| AI assistant | Authenticated conversations and tool orchestration | Conversation storage is currently in memory; decide and document persistence/retention before promising history across restarts. Uses caller-authorized service APIs for tools. |
| RabbitMQ | Durable event delivery and dead-letter queues | Local/dev broker in Compose; routing keys and consumer queues must be versioned and tested. |
| MinIO | Proof-of-delivery file objects | Local/dev object storage; use short-lived URLs and never expose credentials to the browser. |

### 3.1 Neon branch allocation

Keep the branch mapping explicit in the deployment environment. The currently documented order is:

| Branch order | Service/data owner | Environment rule |
|---:|---|---|
| 1 | Default/production branch | Production only; local development and tests must not write here. |
| 2 | Dispatch | `DISPATCH_DB_URL`, `DISPATCH_DB_USERNAME`, `DISPATCH_DB_PASSWORD` |
| 3 | Identity | `IDENTITY_DB_URL`, `IDENTITY_DB_USERNAME`, `IDENTITY_DB_PASSWORD` |
| 4 | Merchant | `MERCHANT_DB_URL`, `MERCHANT_DB_USERNAME`, `MERCHANT_DB_PASSWORD` |
| 5 | Notification | `NOTIFICATION_DB_URL`, `NOTIFICATION_DB_USERNAME`, `NOTIFICATION_DB_PASSWORD` |
| 6 | Parcel | `PARCEL_DB_URL`, `PARCEL_DB_USERNAME`, `PARCEL_DB_PASSWORD` |
| 7 | Pricing | `PRICING_DB_URL`, `PRICING_DB_USERNAME`, `PRICING_DB_PASSWORD` |

Required safeguards:

- Use the direct endpoint for Flyway and application pools; keep credentials in ignored deployment env/secrets only.
- Validate that each configured endpoint maps to the intended branch before running migrations.
- Run and verify Flyway migrations independently per owning service. A migration failure blocks that service's release.
- Never share a service's credentials with another service. Never add cross-service database joins.
- Keep tests on Testcontainers/H2 or dedicated test branches; do not run destructive tests against production/default.
- Store instants in UTC (`TIMESTAMP WITH TIME ZONE` / Java `Instant`); convert to `Asia/Colombo` only for display.

## 5. Role-to-workflow integration matrix

Every action listed below must be checked at all layers: route guard, navigation visibility, API client, gateway route,
backend role/ownership authorization, database side effect, and resulting UI state.

| Role | Main workflows | Owning services |
|---|---|---|
| **CUSTOMER** | Register/login/refresh; view and edit profile; discover zones and estimate delivery; book standard/express parcel; view/filter deliveries; view details/history; cancel eligible parcel; download receipt; manage notification preferences/history; public tracking; customer-scoped assistant parcel lookup and estimate | Identity, Pricing, Parcel, Notification, Tracking, AI assistant |
| **MERCHANT** | Register merchant application; see pending/approved/rejected state; refresh token/roles after approval; create and list orders; inspect order details and tracking; create/revoke API keys; configure webhook; inspect delivery attempts and replay eligible deliveries; notification preferences | Identity, Merchant, Parcel, Pricing, Tracking, Notification |
| **COURIER** | View own assignments/manifest; inspect assigned parcel; advance only allowed statuses; upload proof; complete delivery only with valid proof; view workload/notification preferences; track a parcel publicly | Dispatch, Parcel, Tracking, Notification, Identity |
| **DISPATCHER** | View dispatch queue and zone filter; inspect suggestions; assign/reassign/unassign couriers; monitor async command state; manage courier availability/registry; view workload and daily dispatch report; search parcel status/history | Dispatch, Parcel, Identity, Tracking |
| **ADMIN** | Dashboard; search/manage users and roles/status; manage couriers; review merchant applications; manage parcels/assignments; maintain zones/pricing rules; review/resend notification failures; view reports; inspect webhooks and system health where authorized | Gateway, Identity, Merchant, Dispatch, Parcel, Pricing, Notification |

Public capabilities are limited to public tracking and the public entry/authentication flows. Internal service-token,
user-lookup, actuator, and other service-to-service endpoints are not UI features and must not be made public.

## 6. Execution phases and acceptance gates

Do not start the next phase until its gate passes. Track each endpoint and workflow in a test matrix with its role,
request, expected response, database/event effect, and test evidence.

### Phase 0 — Establish a trustworthy baseline

- Inventory frontend routes/navigation and backend endpoints; reconcile the inventory with source and OpenAPI.
- Start the complete stack using the base Compose file plus the parcel override when MinIO is required.
- Record image/build versions, env variable names (never values), container health, gateway health, and downstream
  health by service.
- Check API base URL, runtime config, allowed browser origins, JWT issuer/audience/JWKS URL, Redis, RabbitMQ,
  MinIO, and each Neon branch without printing secrets.
- Capture known failures as reproducible requests with request IDs. Re-test prior pricing `503` and aggregate
  `DEGRADED` reports instead of assuming previous timeout tuning solved them.
- Treat the recently corrected generated tracking-number format and local `5173`/`5174` CORS configuration as
  candidate fixes that still need to remain covered by regression tests.

**Gate:** Every container either reports healthy or has a documented, isolated blocker; one anonymous and one
authenticated request can be traced through the gateway to the owning service.

### Phase 1 — Database and environment foundation

- Validate all six service DB URLs against the expected Neon branches and confirm credentials without exposing them.
- Check schema history, migration checksums, and database ownership for identity, dispatch, merchant, notification,
  parcel, and pricing.
- Apply pending Flyway migrations to the correct non-production branch and verify each service starts with schema
  validation enabled.
- Verify seed/reference data: pricing zones/rules, required roles, and any bootstrap/admin setup.
- Confirm DB connection/pool settings and a low-cost read/write smoke test per owner.
- Confirm Redis and RabbitMQ credentials and topology; initialize MinIO buckets and verify proof upload/download.

**Gate:** Each service can read/write its own intended store; no migration is pending; all data stores used by local
development are isolated from production/default.

### Phase 2 — Identity, sessions, and authorization

- Exercise register, login, refresh, logout, Google OAuth callback (if enabled), `/users/me`, and profile update.
- Verify the access token has the expected issuer, audience, subject, and role claims; verify JWKS discovery and
  gateway/service JWT validation.
- Verify refresh-cookie path, HttpOnly/SameSite/Secure behavior, token rotation/revocation, and parallel 401 handling.
- Test every route/API as anonymous, correct role, wrong role, disabled user, and wrong resource owner.
- Test role changes (merchant approval, courier provisioning, admin role changes): refresh must provide the new role
  without requiring an unsafe client-side role override.
- Ensure user-facing failures distinguish expired session, forbidden action, suspended account, and service outage.

**Gate:** Authenticated UI journeys survive refresh and reload; unauthorized requests return consistent `401`/`403`
responses and do not mutate data.

### Phase 3 — Pricing and customer parcel journey

- Verify zone listing and city-to-zone resolution for every supported city in the UI.
- Verify estimate request/response fields, weight/size limits, express surcharge, currency, rounding, and error states.
- Verify booking sends validated addresses, zones, package size/weight, express flag, and idempotency key.
- Verify booking persists once, returns a tracking number/status, writes initial history/outbox data, and retries with
  the same key without duplicate parcels.
- Verify customer list, filters, pagination, details, history, cancel eligibility, and receipt download against actual
  DTOs.
- Verify customer ownership isolation: another customer's IDs and tracking-number detail data do not leak.
- Check that UI timestamps are interpreted as UTC instants and rendered in Colombo local time.

**Gate:** A customer can estimate → book → see the persisted parcel → view history → cancel where allowed → download
receipt, with accurate price/status/time and no duplicate on retry.

### Phase 4 — Event delivery and public/live tracking

- Verify parcel status changes write an outbox event in the same DB transaction as the state change.
- Verify V1 and V2 status events and tracking snapshot events each have the intended durable queue, routing binding,
  deduplication, retry, and DLQ behavior.
- Confirm tracking consumes the currently emitted event versions, updates the Redis projection monotonically, and
  can backfill a privacy-safe snapshot on a cold cache miss.
- Verify public tracking returns only allowed fields; malformed tracking numbers are `400`, unknown numbers are flat
  `404`, and rate limits return `429` with `Retry-After`.
- Verify STOMP/SockJS `/ws/info`, websocket upgrade, SSE stream, and polling fallback from each configured browser
  origin (`localhost:5173`, Docker web origin, and deployed origins).
- Verify all clients accept the exact tracking-number alphabet generated by parcel-service.
- Verify out-of-order/duplicate messages do not regress status or duplicate timeline events.

**Gate:** A newly booked parcel is immediately trackable; each subsequent status change appears through REST and at
least one live transport, with polling fallback and no privacy leakage.

### Phase 5 — Courier and dispatcher operations

- Courier: verify `/couriers/me/assignments` ownership, ordering, completed-item behavior, and empty state.
- Dispatcher: verify queue, zone filters, suggestions, assignment, reassign/unassign, command polling, and retry after
  a failed asynchronous command.
- Verify courier registry/availability changes reach dispatch views and do not let couriers self-assign.
- Verify only the assigned courier or permitted dispatcher can perform each status transition.
- Verify proof file type/size validation, upload, proof metadata, expiring read URL, delivery completion requirements,
  and storage failure behavior.
- Verify workload and daily reports against persisted assignments/statuses and timezone/date boundaries.
- Resolve whether dispatchers need a safe courier-user lookup; do not require manual UUID entry if the product expects
  discovery.

**Gate:** Dispatcher assignment reaches the courier view; courier updates persist to parcel-service and propagate to
tracking, notification, merchant webhook, and dispatch projections as applicable.

### Phase 6 — Merchant lifecycle and merchant operations

- Verify merchant registration and one application per account, pending state, admin review, approval/rejection/
  suspension, and role refresh.
- Verify a normal `MERCHANT` user token can use the merchant console APIs intended for user sessions; keep API-key
  and client-credential authentication for external integrations distinct.
- Verify order creation/list/detail, idempotency, search/pagination, parcel creation, and tracking-number linkage.
- Verify API key creation reveals the raw key once, scopes are enforced, revocation is immediate, and secrets are
  never logged or re-rendered.
- Verify webhook URL validation/SSRF protections, secret rotation, signature verification, retry/backoff, delivery
  history, replay eligibility, and idempotency.
- Ensure merchant pages show accurate pending/approved state and useful empty/error states.

**Gate:** A merchant can apply, become approved, create and follow orders, manage API credentials/webhook, and view or
replay authorized delivery attempts without accessing another merchant's data.

### Phase 7 — Admin platform workflows

- Verify dashboard aggregation independently handles each upstream timeout and clearly labels unavailable sections.
- Verify user search/status/role changes and courier administration with auditability and confirmation for destructive
  actions.
- Verify merchant review actions and state consistency with identity role refresh.
- Verify zone and pricing-rule CRUD, validation, active/effective dates, conflicts, and that new rules affect estimates.
- Verify parcel search and detail pages use the correct snapshot vs full-detail API and authorization.
- Verify notification log filters, PII masking, and resend eligibility.
- Verify reports use correct time boundaries and match persisted service data.

**Gate:** Admin operations affect the owning service and are reflected in customer, merchant, courier, and dispatcher
surfaces without cross-service database access.

### Phase 8 — Notifications and operational messaging

- Verify preference read/update behavior for each role and channel; disabled channels/statuses must suppress sending.
- Verify parcel events map to notification templates/statuses, recipient lookup, retry, dead-letter, and audit records.
- Verify user history and admin logs have distinct authorization and pagination/filter contracts.
- Verify email against the configured provider/Mailpit and SMS against the configured adapter; label mock delivery
  clearly in development.
- Verify resend does not duplicate successful sends and reports pending/sent/failed/skipped states correctly.

**Gate:** A parcel lifecycle event creates the expected notification once, with preferences respected and visible
history; failures are retryable and diagnosable.

### Phase 9 — Assistant and non-core capabilities

- Verify assistant-enabled/disabled runtime behavior and OpenAI configuration without putting keys in frontend bundles.
- Verify SSE parsing, cancellation, tool/error/done events, conversation ownership, expiry, and server restart behavior.
- Verify tool authorization uses the caller's token and enforces the same owner/role rules as the corresponding UI/API.
- Decide whether conversations need durable storage; document retention, limits, and deletion before advertising history.
- Review backend endpoints with no UI action and explicitly mark them internal, deferred, or add a user workflow.

**Gate:** Assistant tools cannot access another user's parcel and remain honest about unavailable tools or truncated/
expired in-memory conversation history.

### Phase 10 — Full regression, release, and operations

- Run frontend typecheck/lint/tests/build, shared contract tests, focused service integration tests, then the
  repository-level verification suite.
- Run browser-level smoke tests against the actual Compose stack for all five roles, including direct route reloads,
  logout/session expiry, and mobile layouts.
- Run a full parcel lifecycle: customer booking → dispatch assignment → courier proof/status → tracking live update →
  merchant webhook → notification history → admin reporting.
- Validate health/readiness separately from business endpoint success; identify the exact unhealthy downstream
  behind aggregate `DEGRADED`.
- Verify logs correlate by request ID/event ID without tokens, passwords, phone numbers, addresses, or raw API keys.
- Document rollback steps for application images and database migrations; back up production before any destructive
  migration.

**Gate:** All role journeys and critical negative/security cases pass against the same deployment configuration
intended for release; unresolved issues are listed with owner, severity, and a release decision.

## 7. Cross-cutting verification checklist

- **API contract:** OpenAPI and TypeScript DTOs agree on field names, nullable values, enum values, pagination, status
  codes, error bodies, and date formats.
- **Gateway:** Every browser-facing API has one intended route, correct timeout/circuit-breaker policy, normalized
  errors, explicit CORS origin, request ID, and rate-limit policy.
- **Security:** Route guards are UX only; services enforce roles and ownership. Public endpoints return minimum data.
- **Data:** Each service owns schema and migrations; writes are transactional/idempotent; database timestamps are UTC.
- **Events:** Producers use transactional outbox; consumers are idempotent and version-aware; failures reach DLQ with
  replay procedure.
- **UX:** Every screen has loading, empty, validation, permission, not-found, conflict, rate-limit, and upstream-error
  states where applicable.
- **Runtime:** API base URL and feature flags are runtime-configured; browser origins match gateway and realtime
  service allow-lists; no browser traffic targets Compose-only DNS names.
- **Privacy:** Do not expose raw credentials, recipient contact details, full addresses, internal IDs, or unmasked
  notification targets in public tracking, logs, or analytics.

## 8. Known gaps and decisions to resolve during execution

These are explicit investigation items, not assumptions that the implementation is absent:

1. Reconcile all open questions in [FRONTEND_CONTRACTS.md](./FRONTEND_CONTRACTS.md), especially merchant console
   authentication, courier lookup/manifest semantics, webhook attempt-history fields, and assistant conversation
   persistence.
2. Recheck pricing estimate/zones and the gateway's downstream health under current runtime conditions; prior work
   adjusted timeouts, but a timeout increase alone is not proof of service health.
3. Verify that every locally configured Neon branch is the intended branch and that each migration is applied.
4. Confirm tracking status event compatibility for all event versions emitted by parcel-service, plus snapshot
   backfill and cold-cache behavior.
5. Review UI-visible backend capabilities with no direct screen: dispatch unassign/command polling, courier manifest
   by date, private tracking, merchant order detail, notification lookup by tracking number, and internal identity/
   service-token APIs. Keep internal-only features out of the web app.
6. Confirm which service health indicators are included in gateway aggregate health and define a clear operational
   response for `DEGRADED`.

## 9. Suggested delivery order

1. Baseline and secret-safe environment/branch verification.
2. Identity/JWT/CORS and route authorization.
3. Pricing and customer booking/list/detail.
4. Outbox/event contract and tracking.
5. Dispatch and courier lifecycle/proof.
6. Merchant lifecycle and orders/webhooks.
7. Notification preferences/history/retries.
8. Admin workflows and dashboard/report reconciliation.
9. Assistant and deferred backend-only features.
10. Full role-based browser regression and release gate.

This order follows dependencies: roles and gateway first; parcel/pricing establish the core transaction; events make
downstream workflows reliable; role-specific operations follow; full regression closes the loop.
