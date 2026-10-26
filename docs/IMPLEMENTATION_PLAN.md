# SwiftDrop — Team Implementation Plan

Ten members, nine backend services and one frontend. Each member owns one area end to end:
code, database migrations, tests, Docker image and the docs section for it.

This plan reflects the repository as it is today. Where it differs from the original
[README](../README.md) (Spring Boot version, database hosting), this document wins.

---

## 1. Team and ownership

| # | Member area | Path | Java | Backing services | Secondary responsibility |
|---|---|---|---|---|---|
| M1 | API Gateway | `services/gateway` | 21 | Redis | Docker Compose, Kubernetes/Helm, contracts library reviews |
| M2 | Identity & Auth | `services/identity-service` | 21 | Neon, Redis | JWT/JWKS setup that every other service depends on |
| M3 | Pricing & Zones | `services/pricing-service` | 21 | Neon | Design document (`docs/SWIFTDROP.md`) coordination |
| M4 | Parcel & Booking | `services/parcel-service` | 21 | Neon, RabbitMQ, MinIO | Owner of the parcel state machine and `ParcelStatusChangedV1` |
| M5 | Dispatch & Couriers | `services/dispatch-service` | 21 | Neon, RabbitMQ | Daily reports |
| M6 | Merchant & Webhooks | `services/merchant-service` | 21 | Neon, RabbitMQ | Security review checklist |
| M7 | Tracking (real-time) | `services/tracking-service` | 25 | Redis, RabbitMQ | Load/demo data script |
| M8 | Notifications | `services/notification-service` | 25 | Neon, RabbitMQ, Mailpit | Observability (Prometheus/Grafana) |
| M9 | AI Assistant & MCP | `services/ai-assistant-service` | 25 | OpenAI API | GitHub Actions CI pipeline |
| M10 | Frontend (React SPA) | `apps/web` | — | All via gateway | Playwright end-to-end smoke tests |

**Workload note for M10:** one person building every screen for five roles is the heaviest load
in the team. Recommended: M10 builds the app shell, design system, auth, public tracking and the
customer flows; from Phase 3 each backend owner builds the screen(s) for their own role area
(courier, dispatcher, merchant, admin) using M10's components, with M10 reviewing.

---

## 2. Current state (already done)

| Item | Status |
|---|---|
| Multi-stage Dockerfile for all 9 services (build from repo root) | Done, verified healthy |
| `infra/docker-compose.yml` with Redis, RabbitMQ and all 9 services | Done, verified |
| `infra/.env.example` with one Neon database per service | Done |
| Shared contracts library `packages/common` (`swiftdrop-contracts`) | Done: `EventEnvelope`, `ParcelStatusChangedV1`, messaging names, `ParcelStatus`/`Role`/`PackageSize`, `ErrorCode` |
| Root aggregator `pom.xml` + root Maven wrapper | Done |
| Business code in any service | **Not started** — every service is still the generated skeleton |
| Gateway routes | **Not started** |
| Neon databases provisioned | **Not started** |
| Frontend (`apps/web`) | **Not started** — folder is empty |
| MinIO and Mailpit in compose | **Not started** (M4 and M8 add them) |

---

## 3. Shared conventions (everyone must follow)

### 3.1 API style
- Every public endpoint lives under `/api/v1/...`. The gateway forwards the path unchanged, so
  controllers map the full path (e.g. `@RequestMapping("/api/v1/parcels")`).
- Service-to-service endpoints live under `/internal/...`. **The gateway never routes `/internal/**`**,
  so they are unreachable from outside. They also require the `X-Internal-Token` header
  (value from the `INTERNAL_API_TOKEN` env var) as a second safeguard.
- Request/response bodies are Java `record` DTOs with Bean Validation annotations. Never expose JPA entities.
- Pagination: `?page=0&size=20&sort=createdAt,desc` using Spring Data `Pageable`.
- Every service exposes OpenAPI at `/v3/api-docs` using springdoc-openapi (pick the release that supports Spring Boot 4).

### 3.2 Error responses
- Every error is an RFC 7807 `ProblemDetail` built from the shared `ErrorCode` enum in
  `packages/common`. Use the field names in `ProblemFields`: `type`, `title`, `status`, `detail`,
  `instance`, plus `errorCode`, `traceId`, `timestamp`, and `violations` for validation errors.
- Each service has one `@RestControllerAdvice` that maps exceptions to `ErrorCode`. M1 writes the
  reference implementation in week 1; everyone copies it. Never return stack traces, SQL or PII.

### 3.3 Authentication and roles
- **identity-service issues JWTs** signed with RS256 and publishes its public keys at
  `/.well-known/jwks.json`.
- Every other service is an OAuth2 resource server pointing at that JWKS:
  `spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://identity-service:8080/.well-known/jwks.json`.
- JWT claims: `sub` (user UUID), `email`, `roles` (values from the shared `Role` enum), and
  `merchantId` for merchant users.
- Authorization uses `@PreAuthorize` on service methods. Ownership checks (e.g. "a courier can only
  update their own parcels") are done in the service layer and **must have a test**.
- When a service calls another service on behalf of a user, it forwards the user's JWT (token relay).

### 3.4 Events (RabbitMQ)
- All event payloads, exchange names, routing keys and queue names live in `packages/common`.
  **No service hardcodes an event name or queue string.**
- Every event is wrapped in `EventEnvelope`. Consumers skip duplicates using `eventId`
  (store processed IDs with a unique constraint).
- Never change the fields of an existing event version. Breaking change ⇒ new `XxxV2` record and new routing key.
- Every consumer queue has a dead-letter queue (`Queues.deadLetterOf(...)`) bound to `Exchanges.DEAD_LETTER`.
- Producers use the **transactional outbox** pattern (write the event to an `outbox` table in the
  same transaction as the state change; a scheduler publishes it). This is mandatory for parcel-service, recommended elsewhere.
- Events never contain addresses, phone numbers or emails. Consumers that need contact details fetch them through an `/internal` endpoint.

**Event catalogue**

| Event | Producer | Consumers | State |
|---|---|---|---|
| `ParcelStatusChangedV1` | parcel (M4) | tracking, notification, merchant webhooks, dispatch | Exists in contracts |
| `ParcelAssignedV1` `{parcelId, courierId, assignedBy}` | dispatch (M5) | parcel, tracking, notification | To add (M5) |
| `UserRegisteredV1` `{userId, roles}` | identity (M2) | notification | To add (M2) |
| `MerchantStatusChangedV1` `{merchantId, status}` | merchant (M6) | notification | To add (M6) |

### 3.5 Synchronous calls between services

| Caller → Callee | Purpose |
|---|---|
| parcel → pricing | Validate a quote and read its fee when a booking is confirmed |
| merchant → parcel | Create a parcel for a merchant order |
| merchant → identity | Register an OAuth2 client when a merchant is approved |
| gateway → merchant | Verify a merchant API key (result cached in Redis) |
| notification → parcel | Fetch recipient contact details for a parcel |
| tracking → parcel | Backfill the tracking snapshot on a cache miss |
| ai-assistant → parcel, pricing, tracking | Tool calls on behalf of the user |

Use Spring's `RestClient` with timeouts (connect 2 s, read 5 s). Callers must handle the callee
being down and return `UPSTREAM_UNAVAILABLE`, never a raw 500.

### 3.6 Database
- One Neon database per service (see `infra/.env.example`). No service ever reads another service's database.
- Schema changes only through Flyway migrations in `src/main/resources/db/migration`:
  `V1__init.sql`, `V2__add_xxx.sql`, … **Never edit a migration that has been merged.**
- Use UUID primary keys (`gen_random_uuid()`), `created_at` / `updated_at` `timestamptz` columns.
- Set `spring.jpa.hibernate.ddl-auto=validate` so Hibernate never changes the schema.
- Tests use Testcontainers Postgres, not Neon.

### 3.7 Testing standard (applies to every service)
- **Unit tests** (JUnit + Mockito) for all business rules.
- **Integration tests** with Testcontainers (Postgres / RabbitMQ / Redis) for repositories, consumers and publishers.
- **Authorization tests** for every protected endpoint: allowed role succeeds, other roles get 403, anonymous gets 401.
- **Contract tests**: every consumer deserializes the golden JSON of each event it consumes.
- **Coverage**: JaCoCo, minimum 70% line coverage per service, enforced in CI.
- External HTTP dependencies (Google, OpenAI, merchant webhooks, other services) are stubbed with WireMock.

### 3.8 Git workflow
- `main` is always deployable. Work on `feat/<service>/<short-name>` branches.
- Every PR needs one approving review and a green CI build. Changes to `packages/common` need a review from M1 **and** one consuming service's owner.
- Commit messages: `feat(parcel): add booking endpoint`, `fix(gateway): ...`, `test(dispatch): ...`.

### 3.9 Definition of Done (per feature)
1. Code merged with tests (unit + integration + authz where relevant).
2. Flyway migration included if the schema changed.
3. Endpoint visible in the service's OpenAPI.
4. Works through the gateway with `docker compose up` (not just locally).
5. Errors use the shared `ErrorCode` contract.
6. Short section added to `docs/SWIFTDROP.md` (endpoints, events, decisions).

---

## 4. Timeline

Seven-week template — shift the weeks to fit the real deadline.

| Phase | Weeks | Goal | Exit criterion |
|---|---|---|---|
| 0 — Foundation | 1 | Everyone can build, run and authenticate | Neon DBs provisioned; JWT login works through the gateway; error handler template merged; CI runs on every PR |
| 1 — Core booking | 2–3 | A customer can sign up, estimate a fee, book a parcel | Demo: signup → estimate → book → see it in "My deliveries" |
| 2 — Operations & events | 4–5 | Parcels move through their lifecycle with live updates | Demo: dispatcher assigns → courier updates status → public tracking page updates live → email arrives in Mailpit |
| 3 — Partners, admin, AI | 6 | Merchants, admin tools and the assistant | Demo: merchant API order with webhook; admin approves merchant; chatbot answers "where is my parcel" |
| 4 — Hardening & delivery | 7 | Tests, Kubernetes, docs, rehearsed demo | Coverage ≥70% everywhere; Helm install works; design doc complete |

**Critical path:** M2's JWT/JWKS (week 1) blocks every service's auth → M3's quote API blocks M4's
booking → M4's `ParcelStatusChangedV1` publishing blocks M5, M7, M8 and M6's webhooks.
To avoid waiting: consumers build against the golden JSON in `packages/common` and WireMock stubs from day one.

---

## 5. Per-member plans

### M1 — API Gateway (`services/gateway`)

**Purpose:** single entry point. Authenticates requests, routes them to services, rate-limits and
returns consistent errors. Built on Spring Cloud Gateway (WebFlux, reactive).

**Functionalities**

| Feature | Details |
|---|---|
| Routing | `/api/v1/auth/**`, `/api/v1/users/**`, `/oauth2/**`, `/login/oauth2/**`, `/.well-known/**` → identity · `/api/v1/zones/**`, `/api/v1/pricing/**` → pricing · `/api/v1/parcels/**` → parcel · `/api/v1/dispatch/**` → dispatch · `/api/v1/merchants/**`, `/api/v1/merchant-api/**` → merchant · `/api/v1/tracking/**`, `/ws/**` → tracking · `/api/v1/notifications/**` → notification · `/api/v1/assistant/**`, `/mcp/**` → ai-assistant |
| Block internal paths | `/internal/**` returns 404 at the gateway |
| JWT validation | Reactive resource server using identity's JWKS. Public paths: login, signup, refresh, Google OAuth, `/api/v1/tracking/public/**`, `/api/v1/pricing/estimate` |
| Merchant API keys | Filter on `/api/v1/merchant-api/**`: read `X-API-Key`, verify via merchant-service `/internal/api-keys/verify` (cache result in Redis 5 min), set `X-Merchant-Id`. **Always strip any client-supplied `X-Merchant-Id`.** |
| Rate limiting | Redis `RequestRateLimiter`: per user (by `sub`) or per IP when anonymous. Stricter limits on login (5/min) and public tracking |
| CORS | Allow the SPA origin(s) from config only |
| Correlation ID | Generate `X-Request-Id` if missing, pass it downstream, include it as `traceId` in errors |
| Resilience | Circuit breaker (Resilience4j) per route, fallback returns `UPSTREAM_UNAVAILABLE` (503) |
| Error mapping | 401/403/404/429/503 produced by the gateway use the shared `ErrorCode` ProblemDetail shape |
| WebSocket proxy | Upgrade `/ws/**` to tracking-service |
| API docs | Swagger UI at `/docs` listing every service's `/v3/api-docs` |

**Implementation plan**
1. Week 1: write the reference `@RestControllerAdvice` (servlet version for other services) and the gateway's reactive error handler; share both.
2. Week 1: add routes in `application.yaml` with service names from compose; block `/internal/**`.
3. Week 1: JWT validation with the public-path whitelist (needs M2's JWKS — use a local test key pair until it is ready).
4. Week 2: correlation-ID filter, CORS, rate limiter (Redis already wired in compose).
5. Week 3: circuit breakers + fallback (add `spring-cloud-starter-circuitbreaker-reactor-resilience4j`).
6. Week 4: WebSocket route; API-key filter (needs M6's verify endpoint — stub it first).
7. Week 5: OpenAPI aggregation at `/docs`.
8. Secondary: keep `infra/docker-compose.yml` current as services add dependencies (MinIO, Mailpit, frontend); Helm umbrella chart in Phase 4; review every PR touching `packages/common`.

**Tests**
- Route tests: `WebTestClient` + WireMock downstreams — each prefix reaches the right service, `/internal/**` is 404.
- JWT: valid token passes; expired, wrong signature, missing token → 401 in the ProblemDetail shape.
- Public paths work without a token.
- API-key filter: valid key adds `X-Merchant-Id`; invalid → 401 `INVALID_API_KEY`; a spoofed `X-Merchant-Id` header is removed.
- Rate limiting with Testcontainers Redis: the 6th login within a minute → 429 `RATE_LIMITED`.
- CORS preflight from an allowed vs. a disallowed origin.
- Circuit breaker: downstream timing out → 503 `UPSTREAM_UNAVAILABLE`.

**Done when:** every route works through `docker compose up`, all security filters have tests, and the Helm chart installs on Minikube/Kind.

---

### M2 — Identity & Auth (`services/identity-service`)

**Purpose:** user accounts, login, tokens, Google sign-in, roles, and OAuth2 client credentials for merchant systems.

**Functionalities**

| Feature | Endpoint | Who |
|---|---|---|
| Customer signup | `POST /api/v1/auth/signup` | Public |
| Email/password login | `POST /api/v1/auth/login` → access token (15 min) in body + refresh token in HttpOnly cookie | Public |
| Refresh (rotating) | `POST /api/v1/auth/refresh` | Holder of refresh cookie |
| Logout | `POST /api/v1/auth/logout` (revokes refresh token) | Authenticated |
| Google sign-in | `/oauth2/authorization/google` → Google → callback → redirect to SPA `/oauth/callback?code=<one-time code>` → `POST /api/v1/auth/google/exchange` returns tokens | Public |
| JWKS | `GET /.well-known/jwks.json` | Public |
| Current user | `GET/PATCH /api/v1/users/me` | Authenticated |
| User admin | `GET /api/v1/users`, `POST /api/v1/users` (create COURIER/DISPATCHER/ADMIN), `PATCH /api/v1/users/{id}/roles`, `PATCH /api/v1/users/{id}/status` | ADMIN |
| Client-credentials token | `POST /oauth2/token` (`grant_type=client_credentials`) → JWT with `merchantId` | Merchant systems |
| Register OAuth client | `POST /internal/oauth-clients` (returns secret once) | merchant-service |
| User contact lookup | `GET /internal/users/{id}/contact` | notification-service |
| Brute-force protection | Lock login for 15 min after 5 failures (counter in Redis) | — |

**Data model:** `users` (id, email unique, password_hash, name, phone, google_sub unique, status),
`user_roles` (user_id, role), `refresh_tokens` (id, user_id, token_hash, family_id, expires_at, revoked_at),
`oauth_clients` (client_id, secret_hash, merchant_id, scopes), `outbox`.

**Events:** publishes `UserRegisteredV1` (add `spring-boot-starter-amqp` to the POM and the record to `packages/common`).

**Implementation plan**
1. Week 1 (**blocks everyone**): RSA key pair from env (`JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY`), `NimbusJwtEncoder`, JWKS endpoint, login + signup with BCrypt. Publish a sample token and the JWKS URL for the team.
2. Week 2: refresh-token rotation with reuse detection (reusing an old token revokes the whole family), logout.
3. Week 2: `/users/me`, admin user management, seed one ADMIN account via Flyway (password from env).
4. Week 3: Google sign-in (the `oauth2-client` dependency is already there). Note: the OAuth redirect needs session state — fine for one replica; for Kubernetes use a cookie-based authorization request repository.
5. Week 3: login lockout in Redis. Consider switching `spring-boot-starter-data-redis-reactive` to the blocking `spring-boot-starter-data-redis`, since this is a servlet app.
6. Week 5: client-credentials token endpoint and `/internal/oauth-clients` for M6.
7. Week 5: `UserRegisteredV1` via outbox.

**Tests**
- Unit: password hashing; JWT claims and expiry; refresh rotation and reuse detection.
- Integration (Testcontainers Postgres + Redis): signup → login → refresh → logout flow; lockout after 5 failures.
- Google sign-in with WireMock standing in for Google's token and user-info endpoints.
- Authz: customer calling admin endpoints → 403; disabled user cannot log in.
- Duplicate email signup → 409 `CONFLICT`; bad credentials → 401 `INVALID_CREDENTIALS` (same message for unknown email and wrong password).
- JWKS endpoint returns a key that verifies issued tokens.

**Done when:** every service validates M2's tokens, the SPA can log in with email and with Google, and refresh rotation is tested.

---

### M3 — Pricing & Zones (`services/pricing-service`)

**Purpose:** delivery zones, pricing rules and fee calculation. Booking uses a quote from this service so the fee can't be tampered with.

**Functionalities**

| Feature | Endpoint | Who |
|---|---|---|
| Zone CRUD | `GET/POST/PUT/DELETE /api/v1/zones` (a zone = name + list of cities, active flag) | ADMIN (GET public) |
| Pricing rule CRUD | `GET/POST/PUT/DELETE /api/v1/pricing/rules` (fromZone, toZone, size, baseFee, perKgFee) | ADMIN |
| Fee estimate | `POST /api/v1/pricing/estimate` `{pickupCity, dropoffCity, size, weightKg}` → fee breakdown | Public |
| Create quote | `POST /api/v1/pricing/quotes` → `{quoteId, fee, expiresAt}` valid 15 min | Authenticated |
| Read quote | `GET /internal/quotes/{id}` | parcel-service |

**Design:** Strategy pattern — `PricingStrategy` interface with `ZoneBandStrategy` (base fee
between zones) and `WeightSurchargeStrategy` (per-kg above a threshold), combined by a
`FeeCalculator`. Money as `BigDecimal` with 2 decimals, currency LKR.

**Data model:** `zones`, `zone_cities`, `pricing_rules`, `quotes` (id, inputs, fee, expires_at, used_at).
Seed data (Flyway): Colombo, Gampaha, Kalutara, Kandy, Galle and a default rule set.

**Implementation plan**
1. Week 1: schema + seed migration; zone and rule entities/repositories.
2. Week 2: strategies + `FeeCalculator` + estimate endpoint (**M4 and M10 need this first**).
3. Week 2: quotes with expiry; internal read endpoint; mark quote used when the parcel is booked.
4. Week 3: admin CRUD with validation (no overlapping city assignments, no negative fees).
5. Secondary: coordinate `docs/SWIFTDROP.md` — collect each member's section and keep the diagrams consistent.

**Tests**
- Unit (table-driven): fee for each zone pair/size/weight; weight boundaries (exactly at threshold, just above); rounding.
- Unknown city → 400 `VALIDATION_FAILED` with a violation; missing rule → 404 `RESOURCE_NOT_FOUND`.
- Quote expiry: expired quote → 409 `CONFLICT`; a quote can be used only once.
- Integration (Testcontainers Postgres): Flyway migrations apply cleanly; seed data produces expected fees.
- Authz: non-admin cannot change zones or rules.

**Done when:** the SPA's estimate screen and parcel booking both use real fees from this service.

---

### M4 — Parcel & Booking (`services/parcel-service`)

**Purpose:** the heart of the system. Owns parcels, tracking numbers, the status state machine,
proof of delivery, receipts, and publishes `ParcelStatusChangedV1`.

**Functionalities**

| Feature | Endpoint | Who |
|---|---|---|
| Book a parcel | `POST /api/v1/parcels` `{quoteId, sender, receiver, pickupAddress, dropoffAddress, size, weightKg}` | CUSTOMER |
| My parcels | `GET /api/v1/parcels?status=` | CUSTOMER (own), DISPATCHER/ADMIN (all) |
| Parcel detail + history | `GET /api/v1/parcels/{id}`, `GET /api/v1/parcels/{id}/history` | Owner, assigned courier, DISPATCHER, ADMIN |
| Update status | `PATCH /api/v1/parcels/{id}/status` `{toStatus, note, failureReason}` | Assigned COURIER, DISPATCHER |
| Proof of delivery | `POST /api/v1/parcels/{id}/proof` (photo, jpeg/png, ≤5 MB) → stored in MinIO | Assigned COURIER |
| Receipt PDF | `GET /api/v1/parcels/{id}/receipt` | Owner, ADMIN |
| Merchant parcel create | `POST /internal/parcels` (with merchantId) | merchant-service |
| Lookups | `GET /internal/parcels/by-tracking/{trackingNumber}`, `GET /internal/parcels/{id}/contact` | tracking, notification, ai-assistant |

**State machine** (enforced only here): `CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED`;
`OUT_FOR_DELIVERY → FAILED`; `FAILED → OUT_FOR_DELIVERY` (reattempt). Any other transition →
409 `INVALID_STATUS_TRANSITION`. `DELIVERED` requires a proof photo; `FAILED` requires a failure reason.

**Tracking numbers:** `SD-` + 8 characters of Crockford base32 from a secure random source; retry on unique-constraint collision.

**Data model:** `parcels` (id, tracking_number unique, customer_id, merchant_id, structured sender/receiver/address columns,
size, weight_kg, fee, quote_id, status, assigned_courier_id), `parcel_status_history`
(parcel_id, from_status, to_status, actor_id, note, proof_object_key, failure_reason, occurred_at), `outbox`, `processed_events`.

**Events:** publishes `ParcelStatusChangedV1` on every change including creation (`fromStatus = null`), through the outbox.
Consumes `ParcelAssignedV1` from dispatch to set `assigned_courier_id`.

**Implementation plan**
1. Week 1: schema, entities, state machine class with exhaustive unit tests (it is pure logic, no dependencies).
2. Week 2: booking endpoint — validate quote with pricing (`/internal/quotes/{id}`), generate tracking number, save, write outbox row.
3. Week 2: outbox publisher (scheduled, publishes to `Exchanges.PARCEL_EVENTS` with `RoutingKeys.PARCEL_STATUS_CHANGED_V1`, marks rows sent). **M5, M6, M7, M8 depend on this.**
4. Week 3: list/detail/history endpoints with ownership checks; internal lookup endpoints.
5. Week 4: status update endpoint + `ParcelAssignedV1` consumer; add MinIO to compose and the proof upload.
6. Week 5: receipt PDF (OpenPDF); merchant `/internal/parcels` endpoint for M6.

**Tests**
- Unit: every allowed and disallowed transition in the state machine (full matrix); tracking-number format and collision retry.
- Integration (Testcontainers Postgres + RabbitMQ): booking writes the parcel and publishes an event matching the golden JSON in `packages/common`.
- Outbox: if publishing fails the row stays unsent and is retried; it is never lost.
- Pricing client with WireMock: expired quote → 409, pricing down → 503 `UPSTREAM_UNAVAILABLE`.
- Authz: courier B cannot update courier A's parcel (403); customer cannot see another customer's parcel (404, to avoid revealing existence).
- Upload: wrong file type or oversized file → 400.
- Duplicate `ParcelAssignedV1` (same eventId) processed once.

**Done when:** a booked parcel can be moved to DELIVERED with proof, every change produces exactly one event, and the receipt downloads.

---

### M5 — Dispatch & Couriers (`services/dispatch-service`)

**Purpose:** courier profiles, assigning parcels to couriers, courier workload and daily lists.

**Functionalities**

| Feature | Endpoint | Who |
|---|---|---|
| Courier profiles | `GET/POST/PUT /api/v1/dispatch/couriers` (userId, vehicle, home zone, active, max daily load) | DISPATCHER, ADMIN |
| Unassigned queue | `GET /api/v1/dispatch/unassigned?zone=` | DISPATCHER |
| Assign / reassign | `POST /api/v1/dispatch/assignments` `{parcelId, courierId}` | DISPATCHER |
| Workload | `GET /api/v1/dispatch/couriers/workload` (active parcels per courier) | DISPATCHER, ADMIN |
| Courier's day | `GET /api/v1/dispatch/couriers/me/assignments?date=` | COURIER |
| Suggest courier (stretch) | `GET /api/v1/dispatch/unassigned/{parcelId}/suggestion` — least-loaded active courier in the zone | DISPATCHER |
| Daily report | `GET /api/v1/dispatch/reports/daily?date=` (assigned, delivered, failed per courier) | ADMIN |

**Design:** no synchronous calls to parcel-service. Dispatch keeps its own read model of parcels,
built from `ParcelStatusChangedV1` (new parcels enter the unassigned queue; `DELIVERED`/`FAILED`
reduce the courier's workload).

**Data model:** `couriers`, `dispatch_parcels` (read model: parcel_id, tracking_number, zone, status, assigned_courier_id),
`assignments` (parcel_id, courier_id, assigned_by, assigned_at), `processed_events`, `outbox`.

**Events:** consumes `ParcelStatusChangedV1` (add a `DISPATCH_PARCEL_STATUS_CHANGED` queue to `Queues`);
publishes `ParcelAssignedV1` (add the record to `packages/common`).

**Implementation plan**
1. Week 2: add `ParcelAssignedV1` + queue constant to contracts (PR reviewed by M1 and M4).
2. Week 3: courier profile CRUD; consumer building the read model (build against the golden JSON before M4 is ready).
3. Week 4: assignment endpoint + outbox publishing; workload counts.
4. Week 4: courier "my assignments" endpoint (**M10 needs this for the courier screen**).
5. Week 5: daily report; suggestion endpoint (stretch).

**Tests**
- Unit: workload calculation; cannot assign to an inactive courier or one at max load (409).
- Consumer integration (Testcontainers Postgres + RabbitMQ): CREATED event adds to the queue; DELIVERED reduces workload; duplicate eventId ignored; out-of-order events don't corrupt state.
- Publisher: assigning produces a `ParcelAssignedV1` matching its golden JSON.
- Authz: only DISPATCHER/ADMIN can assign; a courier sees only their own assignments.

**Done when:** a dispatcher can assign from the unassigned board and the courier sees it in their day list.

---

### M6 — Merchant & Webhooks (`services/merchant-service`)

**Purpose:** onboarding business partners, their API credentials, the merchant order API, and
reliable webhooks for parcel status changes.

**Functionalities**

| Feature | Endpoint | Who |
|---|---|---|
| Apply as merchant | `POST /api/v1/merchants` (org name, contact, webhook URL) → PENDING | MERCHANT user |
| Approve / suspend | `PATCH /api/v1/merchants/{id}/status` | ADMIN |
| API keys | `POST /api/v1/merchants/me/api-keys` (key shown once), `GET` (masked), `DELETE /{keyId}` (revoke) | MERCHANT |
| OAuth client | Created on approval via identity `/internal/oauth-clients`; secret shown once | — |
| Verify key | `POST /internal/api-keys/verify` → merchantId + scopes | gateway |
| Merchant order API | `POST /api/v1/merchant-api/orders`, `GET /api/v1/merchant-api/orders/{trackingNumber}` | API key or client-credentials JWT |
| Webhook settings | `PUT /api/v1/merchants/me/webhook` (URL + rotate signing secret) | MERCHANT |
| Webhook deliveries | `GET /api/v1/merchants/me/webhook-deliveries`, `POST .../{id}/replay` | MERCHANT |

**Webhooks:** consume `ParcelStatusChangedV1` (queue `Queues.MERCHANT_WEBHOOK_PARCEL_STATUS_CHANGED`) where `merchantId` is set.
POST the payload to the merchant URL with header `X-SwiftDrop-Signature: sha256=<HMAC of body>`.
Retry with backoff 1 min → 5 min → 30 min → 2 h, then mark FAILED (visible and replayable).
Only `https` URLs in production; block private IP ranges (SSRF protection).

**Data model:** `merchants`, `api_keys` (key_prefix, key_hash SHA-256, merchant_id, scopes, last_used_at, revoked_at),
`webhook_endpoints`, `webhook_deliveries` (event_id, attempt, status, next_attempt_at, last_response_code), `processed_events`, `outbox`.

**Events:** consumes `ParcelStatusChangedV1`; publishes `MerchantStatusChangedV1`.

**Implementation plan**
1. Week 3: merchant application + admin approval workflow.
2. Week 4: API keys (generate, hash, verify, revoke) + internal verify endpoint (**M1 needs this for the API-key filter**).
3. Week 5: merchant order API calling parcel-service `/internal/parcels`; OAuth client registration via identity.
4. Week 5–6: webhook consumer, signing, retry scheduler, delivery log, replay.
5. Secondary: maintain the security checklist (secret handling, SSRF, input validation) and run it against every service before Phase 4.

**Tests**
- Unit: API key generation/hash/verify; HMAC signature matches a known test vector; retry schedule calculation.
- Webhook integration with WireMock: 500 then 200 → delivered on 2nd attempt; always 500 → FAILED after the last retry; replay works.
- SSRF: `http://127.0.0.1`, `http://10.x`, `http://169.254.169.254` webhook URLs rejected.
- Consumer: events without `merchantId` are ignored; duplicates processed once.
- Authz: pending or suspended merchant cannot create orders (403 `MERCHANT_NOT_APPROVED`); a merchant can't see another merchant's keys or deliveries.

**Done when:** an approved merchant can create an order with an API key and receives signed webhooks for every status change.

---

### M7 — Tracking (`services/tracking-service`)

**Purpose:** live parcel tracking — a fast public lookup and real-time push updates to browsers.

**Functionalities**

| Feature | Endpoint | Who |
|---|---|---|
| Public tracking | `GET /api/v1/tracking/public/{trackingNumber}` → status, timeline, masked receiver name (`K*** W***`), city only | Public |
| Live updates (WebSocket) | STOMP over SockJS at `/ws`, subscribe to `/topic/parcels.{trackingNumber}` | Public |
| Live updates (fallback) | `GET /api/v1/tracking/public/{trackingNumber}/stream` (Server-Sent Events) | Public |
| Courier location (stretch) | `POST /api/v1/tracking/couriers/me/location`, stored in Redis GEO, pushed to subscribers | COURIER |

**Design:** consume `ParcelStatusChangedV1` (queue `Queues.TRACKING_PARCEL_STATUS_CHANGED`) and `ParcelAssignedV1`.
Keep a snapshot per tracking number in Redis (current status + timeline, TTL 30 days after delivery).
On a cache miss, backfill from parcel-service `/internal/parcels/by-tracking/{tn}`.
Ignore events older than the snapshot's latest `occurredAt` (events can arrive out of order).
**Never expose PII** — this endpoint is public.

**Implementation plan**
1. Week 2: snapshot model in Redis, consumer (build against the golden JSON before M4 is ready). Consider the blocking `spring-boot-starter-data-redis` instead of the reactive starter, since this is a servlet app.
2. Week 3: public REST endpoint with PII masking + backfill.
3. Week 4: STOMP WebSocket broadcasting on every consumed event (**M10 needs this for the live tracking page**); SSE fallback.
4. Week 5: courier location (stretch).
5. Secondary: demo-data script that books, assigns and moves several parcels through their lifecycle for the final demo.

**Tests**
- Unit: PII masking (names, addresses never present in the response).
- Consumer integration (Testcontainers Redis + RabbitMQ): event updates snapshot; duplicate eventId ignored; older event doesn't overwrite newer status.
- WebSocket integration: `WebSocketStompClient` subscribes, an event is consumed, the client receives the update.
- Backfill: cache miss calls parcel-service (WireMock); unknown tracking number → 404.
- Public endpoint works without a token; location update requires COURIER.

**Done when:** opening the public tracking page in a browser shows status changes live without refreshing.

---

### M8 — Notifications (`services/notification-service`)

**Purpose:** sends email (and mock SMS) to customers and couriers when things happen, and keeps a log.

**Functionalities**

| Feature | Endpoint / trigger | Who |
|---|---|---|
| Parcel status emails | On `ParcelStatusChangedV1`: booked, picked up, out for delivery, delivered, failed | Receiver/sender |
| Welcome email | On `UserRegisteredV1` | New user |
| Merchant decision email | On `MerchantStatusChangedV1` | Merchant |
| Courier assignment SMS (mock) | On `ParcelAssignedV1` | Courier |
| Notification log | `GET /api/v1/notifications?parcelId=` | ADMIN |
| Resend | `POST /api/v1/notifications/{id}/resend` | ADMIN |

**Design:** contact details are **not** in events — fetch them from parcel-service
`/internal/parcels/{id}/contact` or identity `/internal/users/{id}/contact`. Templates with
Thymeleaf. Channel adapters behind an interface: `EmailSender` (Spring Mail → Mailpit locally) and
`SmsSender` (mock that logs a masked number). Never log full emails, phones or addresses.

**Data model:** `notifications` (id, event_id, channel, recipient_masked, template, status SENT/FAILED, attempts, error, created_at),
unique `(event_id, channel)` for idempotency.

**Implementation plan**
1. Week 2: add `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server` and `spring-boot-starter-thymeleaf` to the POM (security is currently missing); add Mailpit to compose.
2. Week 3: `ParcelStatusChangedV1` consumer → contact lookup → template → email; notification log.
3. Week 4: other event consumers (as M2, M5, M6 add their events).
4. Week 5: admin log + resend endpoints.
5. Secondary: Prometheus + Grafana in compose, one dashboard showing request rate, error rate and RabbitMQ queue depth per service.

**Tests**
- Unit: template renders correctly for every parcel status; masking helper.
- Idempotency: the same event delivered twice → exactly one email.
- SMTP integration: GreenMail (or Testcontainers Mailpit) receives the email with the correct subject.
- Failure path: SMTP down → status FAILED, message goes to the DLQ after retries, not lost.
- Contact lookup with WireMock: parcel-service down → retried, not crashed.
- Log check: no full email/phone appears in captured logs.
- Authz: only ADMIN can read the log or resend.

**Done when:** every status change produces the right email in Mailpit and appears in the admin log.

---

### M9 — AI Assistant & MCP (`services/ai-assistant-service`)

**Purpose:** a chatbot that answers parcel questions using real data, and an MCP server exposing the same tools to external AI clients.

**Functionalities**

| Feature | Endpoint | Who |
|---|---|---|
| Chat | `POST /api/v1/assistant/chat` `{conversationId, message}` → reply | Authenticated |
| Tool: track parcel | `trackParcel(trackingNumber)` → tracking-service public API | — |
| Tool: estimate fee | `estimateFee(pickupCity, dropoffCity, size, weightKg)` → pricing-service | — |
| Tool: my parcels | `listMyParcels()` → parcel-service with the **caller's JWT forwarded** | — |
| MCP server | `/mcp` (already wired: `spring-ai-starter-mcp-server-webmvc`) exposing the same tools | MCP clients |

**Design:** Spring AI `ChatClient` with `@Tool`-annotated methods. A system prompt limits the
assistant to SwiftDrop topics and forbids revealing other users' data. Conversation memory: in
memory (last 20 messages per conversation) to start. The OpenAI key comes from `SPRING_AI_OPENAI_API_KEY` (already in compose).

**Implementation plan**
1. Week 1 (secondary, **important for everyone**): GitHub Actions CI — build the contracts library and all services on every PR, run tests, publish JaCoCo reports, fail under 70% coverage, build Docker images.
2. Week 3: chat endpoint with a stubbed tool returning fixed data (works before other services are ready).
3. Week 4: real tool implementations calling pricing and tracking.
4. Week 5: `listMyParcels` with token relay; conversation memory.
5. Week 6: expose the tools through MCP; guardrails (topic restriction, PII rules); chat widget API contract for M10.

**Tests**
- Unit: each tool method with mocked HTTP clients (inputs mapped correctly, errors turned into a friendly message).
- Chat flow with a **stubbed `ChatModel`** — CI never calls the real OpenAI API.
- Security: `listMyParcels` always uses the caller's token; one user can never get another user's parcels through the assistant.
- MCP: listing tools returns the three tools with correct schemas.
- Missing/invalid API key: service fails fast at startup with a clear message (current behaviour — keep it tested).

**Done when:** the chatbot answers "where is SD-XXXXXXXX?" and "how much to send 2 kg from Colombo to Kandy?" with real data, and CI is enforced on every PR.

---

### M10 — Frontend (`apps/web`)

**Purpose:** one responsive React SPA for customers, merchants, couriers, dispatchers and admins, plus the public tracking page.

**Stack:** React 18 + TypeScript + Vite, Tailwind + shadcn/ui, React Query, React Router, React Hook Form + Zod.
API types generated from each service's OpenAPI (`openapi-typescript`) — no hand-written API types.

**Functionalities (pages)**

| Area | Pages |
|---|---|
| Public | Login, signup, Google callback (`/oauth/callback`), **public tracking `/track/:trackingNumber` (mobile-first, live updates)** |
| Customer | New delivery wizard (addresses → size/weight → live estimate → confirm), my deliveries, delivery detail with timeline, receipt download |
| Courier | Today's assignments, update status (with photo upload for delivered, reason for failed) — designed for phones |
| Dispatcher | Unassigned board with assign dialog, courier workload view |
| Merchant | Apply form, API keys (show once + copy), webhook settings and delivery log with replay, orders list |
| Admin | Users and roles, merchant approvals, zones and pricing rules, daily reports |
| All | AI chat widget |

**Cross-cutting:** auth context with access token in memory and silent refresh using the HttpOnly
refresh cookie; one API client that maps `ErrorCode` values to friendly messages and shows field
`violations` on forms; route guards by role; loading/empty/error states on every page.

**Implementation plan**
1. Week 1: Vite project, Tailwind + shadcn, routing, layout, API client, multi-stage Dockerfile (build → nginx) and add it to compose (with M1).
2. Week 2: login/signup/refresh, Google callback, role-based route guards.
3. Week 3: delivery wizard with estimate, my deliveries, detail page.
4. Week 4: public tracking page with WebSocket (SockJS + STOMP) and SSE fallback.
5. Week 5: courier and dispatcher screens (or support their owners — see the workload note in section 1).
6. Week 6: merchant and admin screens; chat widget.
7. Week 7: accessibility pass (keyboard, contrast, labels), responsive pass on a real phone, Playwright smoke tests.

**Tests**
- Unit/component (Vitest + React Testing Library + MSW for API mocks): login form, delivery wizard validation and estimate display, status update form, error display for `VALIDATION_FAILED`.
- Auth: expired access token triggers one refresh and retries the request; failed refresh redirects to login.
- Route guards: a customer cannot open admin pages.
- Playwright end-to-end against `docker compose up`: signup → book parcel → open public tracking page → (dispatcher assigns, courier delivers via API) → tracking page shows DELIVERED live.

**Done when:** the full demo can be performed in the browser on both desktop and a phone.

---

## 6. Week-1 checklist (Phase 0)

| Task | Owner |
|---|---|
| Provision 6 Neon databases + roles; share credentials securely (not in git, not in chat) | M3 (with M1) |
| JWT signing keys, login, JWKS endpoint, sample token for the team | M2 |
| Gateway routes + JWT validation + reference error handler | M1 |
| GitHub Actions CI building and testing everything | M9 |
| Parcel state machine with full unit tests | M4 |
| Pricing schema + seed data | M3 |
| Frontend skeleton + Dockerfile + compose entry | M10 |
| Each member: read section 3, create their first branch, add one passing integration test with Testcontainers | Everyone |

---

## 7. Final demo script (what must work at the end)

1. Customer signs up with Google, estimates a fee, books a parcel, downloads the receipt.
2. The public tracking page is opened on a phone.
3. Dispatcher assigns the parcel; courier sees it on their phone and moves it to DELIVERED with a photo.
4. Tracking page updates live at every step; emails appear in Mailpit.
5. A merchant creates an order via the API with an API key; webhooks arrive (with one retry shown).
6. Admin approves a merchant and changes a pricing rule; the next estimate reflects it.
7. The chatbot answers "where is my parcel?" with live data.
8. `docker compose ps` shows everything healthy; the Helm chart is shown running on Kubernetes; CI and coverage reports are shown.
