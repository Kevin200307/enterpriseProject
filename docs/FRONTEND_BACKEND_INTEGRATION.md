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

- [ ] Every visible UI action for every supported role reaches the owning service through the gateway.
- [ ] The browser request, gateway route, service authorization, DTO, database operation, and returned UI state agree.
- [ ] Each stateful service uses its assigned database branch and its own Flyway migrations; no service reads another
   service's database directly.
- [ ] Cross-service changes complete reliably through documented HTTP or RabbitMQ contracts, with retries,
   idempotency, dead-letter handling, and user-visible pending/error states where needed.
- [ ] Authentication, refresh, CORS, role checks, ownership checks, and privacy guarantees pass positive and negative
   tests.
- [ ] Customer, merchant, courier, dispatcher, and admin journeys pass using real browser requests and the configured
   local Compose stack, not only mocked frontend tests.
- [ ] Failures are diagnosable through request IDs, service logs, health checks, and actionable UI errors.

## 3. Execution status

Last verified: **2026-10-29** (local Compose stack).

Checklist rule: this document is the progress source of truth. Whenever work completes a checklist item, update its
checkbox and the relevant evidence/status in the same change. Mark an item `[x]` only when its stated acceptance
criteria have been verified; keep partial work `[ ]` and record the partial evidence without implying completion.

| Phase | Status | Evidence / remaining work |
|---|---|---|
| 0 — Baseline | **Partially verified** | Compose health, gateway/downstream health, live tracking/CORS, anonymous profile denial, and registration validation are verified. See Phase 0 boxes; not every configuration and traceability check has evidence yet. |
| 1 — Database foundation | **Partially verified** | Sanitized running-service DB identifiers match the documented six Neon branches and all six Flyway schemas were current at startup. Migration checksums, low-cost owner read/write checks, and complete storage smoke tests remain. |
| 2 — Identity and authorization | **Partially verified** | Identity registration/token-flow and RBAC tests passed (62 tests); internal user lookup authorization was aligned with its documented ADMIN access. All five supplied local role accounts authenticated with HTTP 200 and their primary role screens loaded read-only. Live refresh/role-change and full negative authorization matrices remain. |
| 3 — Pricing and customer parcel journey | **Partially verified** | Live zone listing and read-only customer estimates now work for all eight configured cities; representative small/medium/large estimates returned consistent LKR breakdowns. Focused unsupported-city error regressions pass in the UI and pricing service. Customer booking and the rest of the parcel lifecycle remain unverified; no live data was mutated. |
| 4 — Events and live tracking | **Partially verified** | Tracking listener, REST privacy/validation, SSE lifecycle, RabbitMQ duplicate-delivery/DLQ, and out-of-order projection tests passed. The shared browser fetched public tracking and established a native STOMP subscription. Newly booked parcel event delivery/status transitions and browser SSE/polling recovery remain. See Phase 4 boxes. |
| 5–9 — Role workflows | **Partially verified** | The supplied COURIER, DISPATCHER, MERCHANT, and ADMIN accounts loaded Today, Dispatch board, Orders, and Dashboard screens respectively. State-changing operations and downstream lifecycle effects remain unverified. |
| 10 — Regression and release | **Partially verified** | Frontend checks and a clean full Maven reactor run pass (550 tests; zero failures/errors; four skipped). Full lifecycle, isolated E2E runner, and release checks remain. |

Baseline fixes made while starting this plan:

- Raised gateway downstream health-probe timeout to accommodate measured healthy service probes (up to 2.6 seconds in this local stack); aggregate health now reports `UP`.
- Corrected the gateway's default notification-service target to port `8086`, matching the service listener.
- Aligned admin access for the internal user lookup endpoint with its documented authorization and adjusted the role test to send a valid request before asserting access denial.
- Corrected frontend tests to operate the custom accessible select controls, added the incidental authenticated `/users/me` test handler, and aligned customer sign-in expectations with the implemented `/home` landing route.
- Deduplicated gateway CORS response headers, covered the proxy behavior with a regression test, and rebuilt/recreated the gateway and web containers so the browser uses the current assets. The focused gateway health/CORS tests pass (2); live tracking returns `200` with one `Access-Control-Allow-Origin` header.
- Replaced the public tracking WebSocket's SockJS client with native STOMP WebSocket, avoiding SockJS iframe requests blocked by the gateway's frame-security policy. The existing SSE and polling fallbacks remain; frontend typecheck, tracking/realtime tests, and production build pass.
- Frontend production build, typecheck, and all 211 UI tests pass. Pricing tests (12), identity/auth tests (62), parcel API tests (19; one skipped), tracking listener tests (4), RabbitMQ duplicate/DLQ integration tests (2), and tracking end-to-end tests (5) pass. Build output includes upstream Zod annotation warnings only.
- Current verification round: all five supplied role accounts returned login `200`; customer New delivery, merchant Orders, courier Today, dispatcher Dispatch board, and admin Dashboard loaded without submitting changes. The admin Pricing rules page also rendered the current rule list read-only.
- Corrected the shared custom select's accessible combobox semantics and aligned UI tests with that role; frontend lint now has zero errors (three pre-existing warnings), and the complete frontend suite passes (213 tests across 20 files).
- Corrected gateway route tests to expect notification service port `8086`, aligned the circuit-breaker timeout test with a route that has no longer route-specific timeout, and corrected dispatch/merchant context tests that disabled database auto-configuration while requiring JPA repositories.
- Corrected the parcel state-machine test to include the supported `CREATED → CANCELLED` edge. A clean `bash ./mvnw clean test` run then passed across the complete reactor: 550 tests, zero failures/errors, four skipped, including all 23 shared contract tests.
- Fixed the isolated E2E workflow to use the access tokens returned by login and documented the raw-token format for optional dispatcher/courier runs. The PowerShell E2E runner is unavailable in this environment, so that black-box workflow has not yet run.

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

- [ ] Inventory frontend routes/navigation and backend endpoints; reconcile the inventory with source and OpenAPI.
- [x] Start the complete stack using the base Compose file plus the parcel override when MinIO is required.
- [x] Record container health and gateway/downstream health; gateway and all eight downstream indicators reported `UP`.
- [ ] Record image/build versions and environment variable names (never values).
- [ ] Check API base URL, runtime config, allowed browser origins, JWT issuer/audience/JWKS URL, Redis, RabbitMQ,
  MinIO, and each Neon branch without printing secrets.
- [x] Re-test the previously failing public tracking request; it now returns `200` with one allowed-origin CORS header.
- [x] Verify public tracking lookup, SockJS `/ws/info`, and CORS preflights for local ports 5173/5174.
- [x] Verify anonymous profile access returns `401` and invalid registration returns expected validation `400`.
- [ ] Capture remaining known failures as reproducible requests with request IDs; re-test prior pricing `503` and
  confirm aggregate health remains healthy under current runtime conditions.
- [x] Keep generated tracking-number format and local `5173`/`5174` CORS configuration covered by regression tests.
- [x] Rebuild/recreate gateway and web; verify the browser tracking request loads current assets without duplicate
  CORS headers.
- [x] Verify an authenticated customer can load the home page and an existing owned delivery detail in the browser.
- [ ] Test one complete authenticated request trace with request ID through the gateway to the owning service.

**Gate:** Every container either reports healthy or has a documented, isolated blocker; one anonymous and one
authenticated request can be traced through the gateway to the owning service.

### Phase 1 — Database and environment foundation

- [x] Confirm sanitized running-service DB host/database identifiers match the documented six Neon branches without
  exposing credentials.
- [x] Confirm Flyway startup reports current schemas for identity, dispatch, merchant, notification, parcel, and
  pricing (versions 2, 1, 3, 3, 4, and 2 respectively).
- [ ] Validate DB credentials, schema history, migration checksums, and database ownership for all six services
  without exposing secrets.
- [ ] Apply pending Flyway migrations only to the correct non-production branch and verify each service starts with
  schema validation enabled.
- [ ] Verify seed/reference data: pricing zones/rules, required roles, and any bootstrap/admin setup.
- [ ] Confirm DB connection/pool settings and a low-cost read/write smoke test per owner.
- [ ] Confirm Redis and RabbitMQ credentials and topology; initialize MinIO buckets and verify proof upload/download.

**Gate:** Each service can read/write its own intended store; no migration is pending; all data stores used by local
development are isolated from production/default.

### Phase 2 — Identity, sessions, and authorization

- [ ] Exercise register, login, refresh, logout, Google OAuth callback (if enabled), `/users/me`, and profile update
  in live browser journeys.
- [ ] Verify the access token has the expected issuer, audience, subject, and role claims; verify JWKS discovery and
  gateway/service JWT validation.
- [ ] Verify refresh-cookie path, HttpOnly/SameSite/Secure behavior, token rotation/revocation, and parallel 401 handling.
- [ ] Test every route/API as anonymous, correct role, wrong role, disabled user, and wrong resource owner.
- [ ] Test role changes (merchant approval, courier provisioning, admin role changes): refresh must provide the new role
  without requiring an unsafe client-side role override.
- [ ] Ensure user-facing failures distinguish expired session, forbidden action, suspended account, and service outage.
- [x] Verify anonymous `/users/me` is denied with `401`.
- [x] Run focused identity/auth/RBAC tests (62 passed), including the internal user lookup ADMIN authorization fix.
- [x] Authenticate each supplied local role account in the browser (all login responses `200`) and load its primary read-only screen: customer New delivery, merchant Orders, courier Today, dispatcher Dispatch board, admin Dashboard.

**Gate:** Authenticated UI journeys survive refresh and reload; unauthorized requests return consistent `401`/`403`
responses and do not mutate data.

### Phase 3 — Pricing and customer parcel journey

- [x] Verify active zone listing and city-to-zone mapping for every supported city in the live customer UI: Colombo/CMB,
  Gampaha/GAM, Kalutara/KAL, Kandy/KAN, Galle/GAL, Jaffna/JAF, Kurunegala/KUR, and Matara/MAT.
- [x] Verify representative live estimate request/response fields for small, medium, and large packages, express on/off,
  LKR currency, fee breakdown, and total arithmetic. Examples: SMALL 1.5kg express MAT→KAN = LKR 1032.13;
  MEDIUM 3.25kg non-express MAT→KAN = LKR 1282.50; LARGE 10kg express MAT→KAN = LKR 2673.75.
- [ ] Verify maximum weights per size, precision/rounding boundaries, and a route with no effective pricing rule
  against the live service; automated UI coverage confirms a missing-rule 404 is not mislabeled as an unsupported city.
- [x] Verify unsupported-city estimate errors identify the actual city and block booking: a live pickup-city miss
  returned `404`; backend and UI regressions now assert city-specific detail for unsupported pickup/drop-off and
  preserve the distinct no-price route message (pricing service: 6 tests; booking UI: 9 tests).
- [ ] Verify booking sends validated addresses, zones, package size/weight, express flag, and idempotency key.
- [ ] Verify booking persists once, returns a tracking number/status, writes initial history/outbox data, and retries with
  the same key without duplicate parcels.
- [ ] Verify customer list, filters, pagination, details, history, cancel eligibility, and receipt download against actual
  DTOs.
- [ ] Verify customer ownership isolation: another customer's IDs and tracking-number detail data do not leak.
- [ ] Check that UI timestamps are interpreted as UTC instants and rendered in Colombo local time.
- [x] Load the existing authenticated customer's home and owned delivery detail in the browser without mutation.
- [x] Run the complete frontend regression suite (213 passed across 20 test files), typecheck, production build, and
  lint (zero errors; three warnings).
- [x] Run pricing tests (12 passed) and parcel API integration tests (19 passed, one skipped); these remain
  component/service gates, not a live estimate/booking journey.

**Gate:** A customer can estimate → book → see the persisted parcel → view history → cancel where allowed → download
receipt, with accurate price/status/time and no duplicate on retry.

### Phase 4 — Event delivery and public/live tracking

- [ ] Verify parcel status changes write an outbox event in the same DB transaction as the state change.
- [ ] Verify V1 and V2 status events and tracking snapshot events each have the intended durable queue, routing binding,
  deduplication, retry, and DLQ behavior.
- [ ] Confirm tracking consumes the currently emitted event versions, updates the Redis projection monotonically, and
  can backfill a privacy-safe snapshot on a cold cache miss.
- [ ] Verify public tracking returns only allowed fields; malformed tracking numbers are `400`, unknown numbers are flat
  `404`, and rate limits return `429` with `Retry-After`.
- [ ] Verify STOMP `/ws/info`, WebSocket upgrade, SSE stream, and polling fallback from each configured browser
  origin (`localhost:5173`, Docker web origin, and deployed origins).
- [x] Verify public tracking for `SD-00000001` returns `200` and renders its status/timeline in the browser; response
  contains one `Access-Control-Allow-Origin` header.
- [x] Replace SockJS with native STOMP WebSocket and remove unused SockJS dependencies; focused frontend tracking/
  realtime tests (17), typecheck, and production build pass.
- [x] Verify local browser-origin WebSocket upgrade, STOMP `CONNECTED`, and subscription to the parcel topic; verify
  local SSE endpoint responds with `text/event-stream`.
- [x] Verify the public tracking response contains only approved public fields; tracking controller tests cover PII
  exclusion, malformed `400`, and unknown flat `404` responses.
- [x] Verify the local public SSE endpoint responds with `text/event-stream`; SSE lifecycle tests cover initial
  delivery, heartbeat, broadcast, and disconnect cleanup.
- [x] Verify tracking-number alphabet is aligned between shared Java contract and frontend regex tests.
- [x] Run tracking listener tests (4 passed), RabbitMQ duplicate-delivery/DLQ integration tests (2 passed), and
  tracking end-to-end projection/SSE/privacy tests (5 passed).
- [x] Verify duplicate event delivery is processed once and out-of-order status events do not regress the projection.
- [x] Verify a rejected public tracking request returns `429`, a `Retry-After` header, and the normalized error
  contract (gateway filter test, 5 passed).
- [ ] Verify the Redis-backed public tracking policy enforces its configured threshold under integration load.
- [ ] Verify durable bindings/retries/DLQ for every emitted event version, including status V2 and tracking snapshots.
- [ ] Verify cold-cache snapshot backfill and monotonic projection updates against parcel-service and Redis.
- [ ] Verify browser SSE fallback and polling outage/recovery behavior across configured browser origins.
- [ ] Verify live event delivery and status changes through RabbitMQ from a newly booked parcel to REST and browser.

**Gate:** A newly booked parcel is immediately trackable; each subsequent status change appears through REST and at
least one live transport, with polling fallback and no privacy leakage.

### Phase 5 — Courier and dispatcher operations

- [ ] Courier: verify `/couriers/me/assignments` ownership, ordering, completed-item behavior, and empty state.
- [ ] Dispatcher: verify queue, zone filters, suggestions, assignment, reassign/unassign, command polling, and retry after
  a failed asynchronous command.
- [ ] Verify courier registry/availability changes reach dispatch views and do not let couriers self-assign.
- [ ] Verify only the assigned courier or permitted dispatcher can perform each status transition.
- [ ] Verify proof file type/size validation, upload, proof metadata, expiring read URL, delivery completion requirements,
  and storage failure behavior.
- [ ] Verify workload and daily reports against persisted assignments/statuses and timezone/date boundaries.
- [ ] Resolve whether dispatchers need a safe courier-user lookup; do not require manual UUID entry if the product expects
  discovery.

**Gate:** Dispatcher assignment reaches the courier view; courier updates persist to parcel-service and propagate to
tracking, notification, merchant webhook, and dispatch projections as applicable.

### Phase 6 — Merchant lifecycle and merchant operations

- [ ] Verify merchant registration and one application per account, pending state, admin review, approval/rejection/
  suspension, and role refresh.
- [ ] Verify a normal `MERCHANT` user token can use the merchant console APIs intended for user sessions; keep API-key
  and client-credential authentication for external integrations distinct.
- [ ] Verify order creation/list/detail, idempotency, search/pagination, parcel creation, and tracking-number linkage.
- [ ] Verify API key creation reveals the raw key once, scopes are enforced, revocation is immediate, and secrets are
  never logged or re-rendered.
- [ ] Verify webhook URL validation/SSRF protections, secret rotation, signature verification, retry/backoff, delivery
  history, replay eligibility, and idempotency.
- [ ] Ensure merchant pages show accurate pending/approved state and useful empty/error states.

**Gate:** A merchant can apply, become approved, create and follow orders, manage API credentials/webhook, and view or
replay authorized delivery attempts without accessing another merchant's data.

### Phase 7 — Admin platform workflows

- [ ] Verify dashboard aggregation independently handles each upstream timeout and clearly labels unavailable sections.
- [ ] Verify user search/status/role changes and courier administration with auditability and confirmation for destructive
  actions.
- [ ] Verify merchant review actions and state consistency with identity role refresh.
- [ ] Verify zone and pricing-rule CRUD, validation, active/effective dates, conflicts, and that new rules affect estimates.
- [ ] Verify parcel search and detail pages use the correct snapshot vs full-detail API and authorization.
- [ ] Verify notification log filters, PII masking, and resend eligibility.
- [ ] Verify reports use correct time boundaries and match persisted service data.

**Gate:** Admin operations affect the owning service and are reflected in customer, merchant, courier, and dispatcher
surfaces without cross-service database access.

### Phase 8 — Notifications and operational messaging

- [ ] Verify preference read/update behavior for each role and channel; disabled channels/statuses must suppress sending.
- [ ] Verify parcel events map to notification templates/statuses, recipient lookup, retry, dead-letter, and audit records.
- [ ] Verify user history and admin logs have distinct authorization and pagination/filter contracts.
- [ ] Verify email against the configured provider/Mailpit and SMS against the configured adapter; label mock delivery
  clearly in development.
- [ ] Verify resend does not duplicate successful sends and reports pending/sent/failed/skipped states correctly.

**Gate:** A parcel lifecycle event creates the expected notification once, with preferences respected and visible
history; failures are retryable and diagnosable.

### Phase 9 — Assistant and non-core capabilities

- [ ] Verify assistant-enabled/disabled runtime behavior and OpenAI configuration without putting keys in frontend bundles.
- [ ] Verify SSE parsing, cancellation, tool/error/done events, conversation ownership, expiry, and server restart behavior.
- [ ] Verify tool authorization uses the caller's token and enforces the same owner/role rules as the corresponding UI/API.
- [ ] Decide whether conversations need durable storage; document retention, limits, and deletion before advertising history.
- [ ] Review backend endpoints with no UI action and explicitly mark them internal, deferred, or add a user workflow.

**Gate:** Assistant tools cannot access another user's parcel and remain honest about unavailable tools or truncated/
expired in-memory conversation history.

### Phase 10 — Full regression, release, and operations

- [x] Run frontend typecheck, complete tests (213), production build, and lint (zero errors; three warnings).
- [x] Run the complete Maven reactor suite after a clean build: 550 tests, zero failures/errors, four skipped.
- [ ] Run the isolated E2E workflow; its PowerShell runner is unavailable in this environment.
- [ ] Run browser-level smoke tests against the actual Compose stack for all five roles, including direct route reloads,
  logout/session expiry, and mobile layouts.
- [x] Authenticate all five provided role accounts and load each role's primary screen against the local Compose
  gateway; this was read-only and does not verify every screen action or role-specific write.
- [ ] Run a full parcel lifecycle: customer booking → dispatch assignment → courier proof/status → tracking live update →
  merchant webhook → notification history → admin reporting.
- [x] Validate gateway readiness and downstream health independently of the tracking business response; all eight
  downstream indicators reported `UP` in the local verification.
- [ ] Validate health/readiness separately from business endpoint success; identify the exact unhealthy downstream
  behind aggregate `DEGRADED`.
- [ ] Verify logs correlate by request ID/event ID without tokens, passwords, phone numbers, addresses, or raw API keys.
- [ ] Document rollback steps for application images and database migrations; back up production before any destructive
  migration.

**Gate:** All role journeys and critical negative/security cases pass against the same deployment configuration
intended for release; unresolved issues are listed with owner, severity, and a release decision.

## 7. Cross-cutting verification checklist

- [ ] **API contract:** OpenAPI and TypeScript DTOs agree on field names, nullable values, enum values, pagination, status
  codes, error bodies, and date formats.
- [ ] **Gateway:** Every browser-facing API has one intended route, correct timeout/circuit-breaker policy, normalized
  errors, explicit CORS origin, request ID, and rate-limit policy.
- [ ] **Security:** Route guards are UX only; services enforce roles and ownership. Public endpoints return minimum data.
- [ ] **Data:** Each service owns schema and migrations; writes are transactional/idempotent; database timestamps are UTC.
- [ ] **Events:** Producers use transactional outbox; consumers are idempotent and version-aware; failures reach DLQ with
  replay procedure.
- [ ] **UX:** Every screen has loading, empty, validation, permission, not-found, conflict, rate-limit, and upstream-error
  states where applicable.
- [ ] **Runtime:** API base URL and feature flags are runtime-configured; browser origins match gateway and realtime
  service allow-lists; no browser traffic targets Compose-only DNS names.
- [ ] **Privacy:** Do not expose raw credentials, recipient contact details, full addresses, internal IDs, or unmasked
  notification targets in public tracking, logs, or analytics.

## 8. Known gaps and decisions to resolve during execution

These are explicit investigation items, not assumptions that the implementation is absent:

- [ ] Reconcile all open questions in [FRONTEND_CONTRACTS.md](./FRONTEND_CONTRACTS.md), especially merchant console
   authentication, courier lookup/manifest semantics, webhook attempt-history fields, and assistant conversation
   persistence.
- [x] Recheck gateway downstream health under current runtime conditions; all eight indicators reported `UP`.
- [x] Recheck gateway actuator health under current runtime conditions (`UP`) and confirm all Compose service health
   indicators remain healthy; the authenticated admin pricing-rules screen rendered its existing rule list read-only.
- [x] Verify sanitized identifiers for all six configured Neon branches match the documented service mapping.
- [ ] Verify each migration is applied and recheck branch/schema state before database changes.
- [ ] Confirm tracking status event compatibility for all event versions emitted by parcel-service, plus snapshot
   backfill and cold-cache behavior.
- [ ] Review UI-visible backend capabilities with no direct screen: dispatch unassign/command polling, courier manifest
   by date, private tracking, merchant order detail, notification lookup by tracking number, and internal identity/
   service-token APIs. Keep internal-only features out of the web app.
- [ ] Confirm which service health indicators are included in gateway aggregate health and define a clear operational
   response for `DEGRADED`.

## 9. Suggested delivery order

1. [ ] Baseline and secret-safe environment/branch verification.
2. [ ] Identity/JWT/CORS and route authorization.
3. [ ] Pricing and customer booking/list/detail.
4. [ ] Outbox/event contract and tracking.
5. [ ] Dispatch and courier lifecycle/proof.
6. [ ] Merchant lifecycle and orders/webhooks.
7. [ ] Notification preferences/history/retries.
8. [ ] Admin workflows and dashboard/report reconciliation.
9. [ ] Assistant and deferred backend-only features.
10. [ ] Full role-based browser regression and release gate.

This order follows dependencies: roles and gateway first; parcel/pricing establish the core transaction; events make
downstream workflows reliable; role-specific operations follow; full regression closes the loop.
