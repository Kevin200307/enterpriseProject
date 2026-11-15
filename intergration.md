# SwiftDrop Final Integration and Workflow Implementation Plan

## 1. Purpose

This document is the final integration plan for connecting all currently implemented SwiftDrop modules through the API Gateway and verifying every important business workflow.

The plan covers:

- Identity Service
- Pricing Service
- Parcel Service
- Dispatch Service
- Courier functions in Dispatch Service
- Tracking Service
- Notification Service
- Merchant Service
- Spring Cloud Gateway
- React web application

The following work is intentionally excluded:

- AI Assistant Service implementation and testing
- New database connection setup
- Production database provisioning
- Database performance tuning
- Kubernetes and production deployment

Existing repositories and in-memory, mocked, H2, or containerized test dependencies may be used to verify application behavior without connecting to a shared external database.

## 2. Integration Goal

The final system must support this complete workflow through one public API Gateway endpoint:

```text
Customer registers or signs in
  -> requests a price estimate
  -> creates a parcel
  -> parcel appears in the dispatch queue
  -> dispatcher assigns a courier
  -> assignment appears in the courier's work list
  -> courier collects and transports the parcel
  -> courier records each parcel status
  -> tracking is updated after every status change
  -> the correct customer or recipient is notified
  -> the merchant receives a webhook when applicable
  -> proof of delivery is stored
  -> parcel becomes DELIVERED
  -> customer can download the receipt
```

All browser and partner requests must use the Gateway. Internal service-to-service calls and RabbitMQ events must not be exposed as public Gateway routes.

## 3. Current Architecture

```text
React Web Application
        |
        v
Spring Cloud Gateway
        |
        +--> Identity Service
        +--> Pricing Service
        +--> Parcel Service
        +--> Dispatch Service
        +--> Merchant Service
        +--> Tracking Service
        +--> Notification Service

Parcel Service --RabbitMQ--> Dispatch Service
Parcel Service --RabbitMQ--> Tracking Service
Parcel Service --RabbitMQ--> Notification Service
Parcel Service --RabbitMQ--> Merchant Webhook Consumer
```

The repository already contains Gateway routes for the main service path groups. The remaining work is primarily contract correction, missing endpoint implementation, event recipient correction, merchant workflow completion, and system-level tests.

### 3.1 Identity Service baseline after PR #69

The Identity Service was extended after the first version of this plan. Treat the following as implemented baseline, not new integration work:

- `GET /api/v1/users` provides ADMIN-only pagination with optional `role` and `status` filters.
- `PATCH /api/v1/users/{id}/status` provides ADMIN-only activation/suspension, revokes refresh tokens on suspension, and prevents self-suspension.
- `PATCH /api/v1/users/{id}/roles` replaces a user's roles and prevents an administrator from removing their own `ADMIN` role.
- `GET /api/v1/internal/users/lookup?id={uuid}` resolves one user for an internal caller.
- `POST /api/v1/internal/users/lookup` resolves multiple users from `{ "userIds": [...] }`; the alias `ids` is also accepted.
- Runtime admin bootstrap is idempotent and configurable under `app.security.admin` / `ADMIN_BOOTSTRAP_*`.
- The Identity Service contains an RBAC/authorization integration-test suite that can be copied as a pattern by other resource servers.

These features still require Gateway contract checks and cross-service acceptance tests. The internal lookup authorization must also be narrowed to explicit trusted service scopes before production use; the current expression accepts any JWT containing a `client_id` or `scope` claim.

## 4. Confirmed Missing or Incorrect Implementations

### 4.1 Notification recipient is incorrect

The Notification Service currently selects `actorId` as the notification recipient and uses `parcelId` as a fallback. During a courier status update, `actorId` is normally the courier, not the customer. A parcel ID is also not an Identity Service user ID.

This means status notifications can be sent to the wrong user or fail during identity lookup.

Required implementation:

1. Add the actual customer user ID to the private parcel status event.
2. Add recipient information or a secure way to resolve the recipient.
3. Use `changedByUserId` only for auditing.
4. Resolve notification preferences using the customer user ID.
5. Keep private names, email addresses, phone numbers, and addresses out of public tracking events.

Recommended event contract:

```java
ParcelStatusChangedV2(
    UUID parcelId,
    String trackingNumber,
    UUID customerUserId,
    UUID merchantId,
    UUID assigneeUserId,
    ParcelStatus fromStatus,
    ParcelStatus toStatus,
    UUID changedByUserId,
    String failureReason,
    UUID proofId
)
```

If recipient email or phone must be transported in an event, publish a separate private event such as `parcel.notification.requested.v1`. Do not add private contact data to the public tracking snapshot.

### 4.2 Merchant functionality is incomplete

The Merchant Service currently implements merchant registration, listing, approval, rejection, suspension, and merchant lookup. The frontend expects several operations that have no matching controller implementation.

Missing functions:

- Merchant order creation and listing
- API key creation, listing, and revocation
- API key authentication and scope validation
- Webhook URL configuration
- Webhook signing-secret generation and rotation
- Webhook event consumer
- Webhook delivery worker
- Exponential retry and dead-letter handling
- Webhook delivery history
- Manual webhook replay
- OAuth client-credentials support, if retained in the final scope

Some entities and repositories exist, but they do not form a working merchant workflow.

### 4.3 Merchant approval still needs a dedicated internal Identity endpoint

Merchant approval calls this internal operation:

```text
POST /api/v1/internal/users/{userId}/roles
```

Identity Service now has the public ADMIN-only `PATCH /api/v1/users/{id}/roles` endpoint, but it still does not expose the internal merchant-specific operation expected by Merchant Service. Merchant approval can therefore still fail while attempting to grant the `MERCHANT` role. Do not call the public admin endpoint with a service credential because it replaces the entire role set and is intended for a human administrator.

Required implementation:

- Add a secured internal Identity Service endpoint for assigning the merchant role and merchant ID.
- Require a service token with an internal audience or scope.
- Make the operation idempotent.
- Preserve all existing roles while adding `MERCHANT`; do not reuse the replace-all public role operation.
- Ensure a user receives refreshed JWT claims after approval.
- Add rollback or reconciliation behavior when merchant approval succeeds but role assignment temporarily fails.

### 4.4 Frontend and backend endpoint mismatches

| Function | Frontend contract | Current backend contract | Required decision |
|---|---|---|---|
| Dashboard summary | `GET /api/v1/dashboard/summary` | `GET /api/v1/dashboard` | Standardize on `/dashboard/summary` |
| Upload proof | `POST /api/v1/parcels/{id}/proof` | `POST /api/v1/parcels/{id}/proofs` | Standardize on `/proofs` and update frontend |
| Cancel parcel | `POST /api/v1/parcels/{id}/cancel` | Missing | Implement endpoint and transition |
| Admin users | `GET /api/v1/users` | Implemented with `role`, `status`, and pageable parameters | Connect frontend and add Gateway acceptance test |
| User status | `PATCH /api/v1/users/{id}/status` | Implemented; ADMIN only, self-suspension guarded | Connect frontend and verify refresh-token revocation |
| User roles | `PATCH /api/v1/users/{id}/roles` | Implemented; ADMIN only, replaces role set | Connect frontend; keep separate from internal merchant role grant |
| Merchant orders | `/api/v1/merchants/orders` | Missing | Implement |
| Merchant API keys | `/api/v1/merchants/me/api-keys` | Missing | Implement |
| Merchant webhook | `/api/v1/merchants/me/webhook` | Missing | Implement |
| Webhook deliveries | `/api/v1/merchants/me/webhook/deliveries` | Missing | Implement |

### 4.5 Parcel cancellation is missing

The frontend exposes parcel cancellation, but Parcel Service has no cancellation endpoint.

Required rules:

- Only a parcel owner, authorized merchant, or administrator may cancel.
- Cancellation is allowed only before pickup.
- Cancellation must write parcel history.
- Cancellation must create outbox events in the same transaction.
- Dispatch, tracking, notifications, and merchant webhooks must receive the cancellation event.
- Repeating the same cancellation request must be idempotent.

Add `CANCELLED` to the shared parcel status model if it is not already present.

### 4.6 Proof-of-delivery contract is inconsistent

The frontend uses singular `/proof`, while Parcel Service exposes plural `/proofs` and proof retrieval operations.

Use this contract:

```text
POST /api/v1/parcels/{parcelId}/proofs
GET  /api/v1/parcels/{parcelId}/proofs/{proofId}
GET  /api/v1/parcels/{parcelId}/proofs/{proofId}/url
```

Rules:

- Only the assigned courier may upload proof.
- Validate file content, type, and size.
- Do not trust only the filename or `Content-Type` header.
- Define whether proof is required before `DELIVERED`.
- Store the proof ID in history and the status event.
- Use multipart forwarding tests through the Gateway.

### 4.7 Receipt link is inconsistent

Notification rendering constructs a receipt URL using the tracking number, but the Parcel API receipt operation expects a parcel ID.

Choose one public contract:

```text
GET /api/v1/parcels/{parcelId}/receipt
```

or:

```text
GET /api/v1/parcels/by-tracking/{trackingNumber}/receipt
```

The first option is preferred for authenticated customer pages. Notification links may point to the frontend delivery page instead of directly embedding an authenticated API URL.

### 4.8 No complete end-to-end acceptance workflow exists

Individual services have unit and integration tests, and the frontend uses mocked API tests. These do not prove that all modules work together through the Gateway.

A dedicated workflow test suite must be created after the contracts are corrected.

## 5. Final Parcel Lifecycle

Use one state machine across common contracts, Parcel Service, Dispatch Service, frontend display logic, Tracking Service, notifications, and merchant webhooks.

```text
CREATED
  -> PICKED_UP
  -> IN_TRANSIT
  -> OUT_FOR_DELIVERY
  -> DELIVERED

OUT_FOR_DELIVERY
  -> FAILED
  -> OUT_FOR_DELIVERY    (reattempt)

CREATED
  -> CANCELLED
```

Rules:

- No service may invent a different status name.
- Illegal transitions return HTTP `409 Conflict` or `422 Unprocessable Entity`, consistently.
- `FAILED` requires a failure reason.
- `DELIVERED` requires proof if proof is mandatory.
- Only the assigned courier can execute courier transitions.
- A dispatcher or administrator may reassign according to an explicit reassign rule.
- Status, history, and outbox writes occur in one transaction.
- Consumers are idempotent by event ID.

## 6. Service-by-Service Implementation Plan

### 6.1 Common contracts

1. Finalize `ParcelStatus` including `CANCELLED` if required.
2. Introduce `ParcelStatusChangedV2` without modifying V1 incompatibly.
3. Define a private notification event if required.
4. Define merchant webhook payload version 1.
5. Add JSON serialization and backward-compatibility tests.
6. Add routing-key and queue constants.
7. Document which service owns each event.

Completion criteria:

- Every producer and consumer uses the same shared contract.
- Duplicate and unknown event versions have defined handling.
- Public events contain no private contact information.

### 6.2 Identity Service

Already implemented:

- Admin user listing with filters and pagination
- User status update
- User role update
- Internal user/contact lookup for trusted services
- Idempotent runtime bootstrap of the initial administrator
- RBAC and authorization test template

Remaining integration work:

- Internal merchant-role assignment that adds `MERCHANT` without deleting existing roles
- Service-token validation
- Merchant ID claim support
- Restrict internal lookup to named clients and exact scopes; remove the generic `client_id != null` / `scope != null` allowance
- Disable bootstrap by default outside local development and require secrets supplied by the environment; never retain the documented fallback password in a deployed environment
- Tests for expired, invalid, wrong-audience, and insufficient-role tokens

Public routes:

```text
POST  /api/v1/auth/register
POST  /api/v1/auth/login
POST  /api/v1/auth/refresh
POST  /api/v1/auth/logout
GET   /api/v1/auth/oauth2/google/authorize
GET   /api/v1/auth/oauth2/callback/google
GET   /api/v1/users/me
PATCH /api/v1/users/me
GET   /api/v1/users
PATCH /api/v1/users/{id}/status
PATCH /api/v1/users/{id}/roles
```

Internal routes must not be exposed through the Gateway:

```text
GET  /api/v1/internal/users/lookup?id={userId}
POST /api/v1/internal/users/lookup
POST /api/v1/internal/users/{id}/merchant-role
```

### 6.3 Pricing Service

Verify:

- Active-zone listing
- Zone resolution
- Estimate calculation
- Weight and size validation
- Missing-route behavior
- Admin zone CRUD
- Admin pricing-rule CRUD
- Consistent currency and decimal rounding

Required tests:

- Same input always produces the same estimate.
- Parcel creation saves a fee snapshot.
- Later pricing-rule changes do not alter an existing parcel fee.
- Unauthorized customers cannot modify pricing rules.

### 6.4 Parcel Service

Implement or verify:

- Customer parcel creation
- Merchant parcel creation with merchant ownership
- Idempotent booking
- Customer and merchant isolation
- Price estimate integration
- Status state machine
- Cancellation
- Assignment application from Dispatch Service
- History
- Proof upload and access
- Receipt generation
- Status and dispatch outbox events
- Tracking snapshot backfill endpoint

Every successful mutation must return a stable DTO rather than exposing JPA entities.

### 6.5 Dispatch Service

Verify and complete:

- Parcel-created projection
- Unassigned queue
- Courier CRUD
- Courier suggestions
- Workload counts
- Assignment and reassignment
- Unassignment
- Command status
- Daily statistics
- Courier assignment list
- Assignment concurrency protection

Assignment workflow:

```text
Dispatcher submits assignment
  -> Dispatch validates courier and parcel projection
  -> Dispatch calls Parcel internal assignment endpoint with service token
  -> Parcel updates assignee and writes outbox event
  -> Dispatch projection consumes resulting event
  -> Courier assignment list reflects the new assignee
```

The operation must not report success until Parcel Service confirms the assignment. Pending/retry state must be visible when Parcel Service is temporarily unavailable.

### 6.6 Tracking Service

Verify:

- Status-event consumption
- Event deduplication
- Cold-cache backfill from Parcel Service
- No regression from out-of-order events
- Public privacy-safe response
- Authenticated full tracking response
- SSE publication
- WebSocket publication
- Redis failure behavior
- DLQ handling

Public tracking must never expose phone numbers, email addresses, full street addresses, merchant secrets, or internal actor IDs.

### 6.7 Notification Service

Implement:

- Correct recipient resolution
- Customer preference lookup using customer ID
- Email notification
- Mock SMS notification
- Idempotency by event, channel, and recipient
- Retry scheduling
- Maximum-attempt handling
- DLQ behavior
- Admin notification log
- Customer notification list
- Manual resend

Recommended recipients:

| Event | Customer | Recipient | Merchant |
|---|---:|---:|---:|
| CREATED | Yes | Optional | Webhook |
| PICKED_UP | Yes | Optional | Webhook |
| IN_TRANSIT | Yes | Optional | Webhook |
| OUT_FOR_DELIVERY | Yes | Yes | Webhook |
| DELIVERED | Yes | Yes | Webhook |
| FAILED | Yes | Yes | Webhook |
| CANCELLED | Yes | Optional | Webhook |

### 6.8 Merchant Service

Implement in this order:

1. Merchant approval integration with Identity Service.
2. API-key generation, hashing, listing, scoping, and revocation.
3. Merchant order creation and listing.
4. Webhook configuration and secret rotation.
5. Parcel-event consumer.
6. Webhook delivery worker with HMAC signature.
7. Exponential retry and dead-letter state.
8. Delivery history and replay APIs.
9. OAuth client credentials only if required by the final assignment scope.

Webhook headers:

```text
X-SwiftDrop-Event-Id
X-SwiftDrop-Event-Type
X-SwiftDrop-Timestamp
X-SwiftDrop-Signature
```

Use an HMAC SHA-256 signature over a documented canonical timestamp and raw request body. A repeated event must not create multiple logical deliveries.

### 6.9 Gateway

The Gateway remains the only public backend entry point.

Responsibilities:

- Route external requests
- Validate access JWTs
- Apply CORS
- Apply rate limits
- Generate or preserve request IDs
- Return consistent Gateway errors
- Proxy SSE and WebSocket connections
- Aggregate dashboard data
- Expose service OpenAPI documents

The Gateway must not contain parcel, merchant, notification, or dispatch business rules.

## 7. Final Gateway Route Map

```text
/api/v1/auth/**           -> identity-service
/api/v1/users/**          -> identity-service
/api/v1/parcels/**        -> parcel-service
/api/v1/pricing/**        -> pricing-service
/api/v1/dispatch/**       -> dispatch-service
/api/v1/couriers/**       -> dispatch-service
/api/v1/merchants/**      -> merchant-service
/api/v1/tracking/**       -> tracking-service
/api/v1/notifications/**  -> notification-service
/api/v1/dashboard/**      -> gateway dashboard BFF
/ws                       -> tracking-service
/ws/**                    -> tracking-service
```

Do not add a required AI Assistant route until that module is implemented.

Never expose these publicly:

```text
/api/v1/internal/**
/actuator/** except the intended public health endpoint
service database ports
RabbitMQ management in production
Redis
object-storage administration endpoints
```

## 8. Steps to Connect Services to Spring Cloud Gateway

### Step 1: Finalize contracts

For every frontend API call, record:

- HTTP method
- Path
- Required role
- Query parameters
- Request body
- Response body
- Success status
- Error responses

Update either the frontend or controller until each operation matches exactly.

### Step 2: Configure service addresses

Configure these Gateway environment variables:

```text
IDENTITY_SERVICE_URL
PARCEL_SERVICE_URL
PRICING_SERVICE_URL
DISPATCH_SERVICE_URL
MERCHANT_SERVICE_URL
TRACKING_SERVICE_URL
TRACKING_WEBSOCKET_URL
NOTIFICATION_SERVICE_URL
IDENTITY_JWKS_URI
IDENTITY_ISSUER_URI
JWT_AUDIENCE
WEB_ORIGIN
```

Use container DNS names in Docker Compose and environment-specific addresses elsewhere. Do not hardcode localhost inside service containers.

### Step 3: Register routes

Keep one route per bounded service path. Place the special SSE and WebSocket routes before the general tracking route.

### Step 4: Configure JWT validation

The Gateway must validate:

- Signature
- Issuer
- Audience
- Expiration
- Not-before time

Downstream services must also validate the token and enforce roles. Gateway authentication does not replace service-level authorization.

### Step 5: Protect internal calls

- Services should call each other using internal container addresses.
- Use service tokens for privileged internal endpoints.
- Do not route `/api/v1/internal/**` through the public Gateway.
- Validate the internal token audience and required service scope.

### Step 6: Configure trusted headers

- Remove client-supplied trusted identity headers.
- Gateway may add canonical authenticated-user headers after JWT validation.
- Downstream authorization should rely on the validated JWT unless a clearly documented trusted-header design is used.
- Preserve or generate `X-Request-ID` and return it in the response.

### Step 7: Configure CORS

Allow only the exact frontend origin in credentialed environments.

Required methods:

```text
GET, POST, PUT, PATCH, DELETE, OPTIONS
```

Required headers include:

```text
Authorization
Content-Type
Accept
X-Request-ID
If-Match
Idempotency-Key
```

Expose at least:

```text
X-Request-ID
Location
Retry-After
```

### Step 8: Verify uploads

Test multipart proof upload through the Gateway. Confirm request-size limits at the browser, Gateway, Parcel Service, and storage adapter.

### Step 9: Verify realtime routes

- SSE routes must not use the normal short response timeout.
- Disable proxy buffering for SSE.
- Verify WebSocket upgrade headers.
- Test SockJS fallback if the frontend uses it.
- Test reconnect and event deduplication.

### Step 10: Configure failure behavior

- Connection timeout: short and explicit.
- Normal API response timeout: explicit.
- SSE timeout: disabled or separately configured.
- Circuit-breaker response: RFC 7807 with `503 Service Unavailable`.
- Include request ID in all Gateway-generated errors.
- Do not convert authorization failures into upstream failures.

## 9. Optional AWS API Gateway Deployment

If “API Gateway” refers to AWS API Gateway rather than the existing Spring Cloud Gateway, do not connect AWS API Gateway separately to every Spring service.

Recommended architecture:

```text
Browser or Partner
  -> AWS API Gateway HTTP API
  -> VPC Link
  -> private Application Load Balancer
  -> Spring Cloud Gateway
  -> internal services
```

Use AWS API Gateway HTTP API by default because it is simpler and lower-cost. Use REST API only when the system requires features such as usage plans, per-client API keys, built-in request validation, caching, request transformation, or canary stages.

AWS connection steps:

1. Deploy Spring services in private ECS or EKS networking.
2. Expose only Spring Cloud Gateway through a private ALB.
3. Create an API Gateway VPC Link to the ALB.
4. Add an `ANY /{proxy+}` integration.
5. Configure a custom domain and TLS certificate.
6. Configure JWT authorization at AWS or retain Spring Gateway as the authoritative validator.
7. Configure exact CORS origins.
8. Enable access logs and request IDs.
9. Configure throttling.
10. Forward authorization and tracing headers.
11. Handle WebSocket connectivity through an AWS WebSocket API or expose the chosen realtime endpoint separately.
12. Run the full workflow suite through the AWS public URL.

Avoid placing both AWS API Gateway and Spring Cloud Gateway business authorization rules in different forms. Choose one source of truth and keep downstream role authorization in the domain services.

## 10. Workflow Test Plan

### 10.1 Customer booking workflow

1. Register or sign in as customer.
2. Request price estimate.
3. Create parcel.
4. Verify saved fee matches the estimate snapshot.
5. Verify parcel history contains `CREATED`.
6. Verify status and dispatch events are written.
7. Verify parcel appears in dispatch queue.
8. Verify tracking endpoint returns a privacy-safe `CREATED` view.
9. Verify booking notification targets the customer.

### 10.2 Dispatcher assignment workflow

1. Sign in as dispatcher.
2. List unassigned queue.
3. List or create active courier.
4. Request courier suggestions.
5. Assign parcel.
6. Verify Parcel Service stores the courier identity user ID.
7. Verify Dispatch projection updates.
8. Verify courier sees the assignment.
9. Repeat the same command and verify idempotency.
10. Attempt a competing assignment and verify conflict handling.

### 10.3 Courier delivery workflow

1. Sign in as the assigned courier.
2. List own assignments.
3. Change `CREATED -> PICKED_UP`.
4. Change `PICKED_UP -> IN_TRANSIT`.
5. Change `IN_TRANSIT -> OUT_FOR_DELIVERY`.
6. Upload proof.
7. Change `OUT_FOR_DELIVERY -> DELIVERED`.
8. Verify every transition appears in history.
9. Verify tracking updates after every event.
10. Verify the customer, not the courier, receives notifications.
11. Verify merchant webhook delivery when the parcel belongs to a merchant.
12. Verify receipt download.

### 10.4 Failed delivery and reattempt workflow

1. Move parcel to `OUT_FOR_DELIVERY`.
2. Attempt `FAILED` without a reason and expect validation failure.
3. Record `FAILED` with a reason.
4. Verify history, tracking, notification, and webhook.
5. Reassign if required.
6. Move `FAILED -> OUT_FOR_DELIVERY`.
7. Complete delivery.

### 10.5 Cancellation workflow

1. Customer creates a parcel.
2. Customer cancels before pickup.
3. Verify `CANCELLED` in parcel history and tracking.
4. Verify removal from the active dispatch queue.
5. Verify customer notification and merchant webhook if applicable.
6. Repeat cancellation and verify idempotent behavior.
7. Attempt cancellation after pickup and expect rejection.

### 10.6 Merchant workflow

1. Customer applies as merchant.
2. Admin approves the merchant.
3. Identity Service grants merchant role and merchant claim.
4. Merchant signs in again or refreshes token.
5. Merchant generates API key.
6. Merchant creates an order using the API key.
7. Duplicate idempotency key does not create a second parcel.
8. Merchant configures webhook.
9. Parcel status changes create signed webhook deliveries.
10. Failed webhook retries with backoff.
11. Exhausted webhook moves to dead-letter state.
12. Merchant can view and replay the failed delivery.

### 10.7 Authorization workflow

Verify:

- Customer cannot assign parcels.
- Customer cannot update delivery status.
- Courier cannot update another courier's parcel.
- Courier cannot access another courier's manifest.
- Dispatcher cannot edit admin-only pricing unless explicitly allowed.
- Merchant cannot access another merchant's orders, keys, or webhook history.
- Anonymous users can access only public tracking and authentication endpoints.
- Internal service endpoints reject browser/user tokens without internal scope.

### 10.8 Resilience workflow

Verify:

- Duplicate RabbitMQ event is processed once logically.
- Out-of-order event does not regress tracking.
- Notification failure is retried.
- Webhook failure is retried and dead-lettered.
- Parcel outbox retains events when RabbitMQ is unavailable.
- Gateway returns `503` with RFC 7807 when a service is unavailable.
- Tracking cold-cache miss backfills from Parcel Service.
- Request IDs appear across Gateway and service logs.

## 11. Required Automated Test Layers

### Layer 1: Unit tests

- State transitions
- Pricing calculation
- Notification recipient selection
- Webhook signing
- API-key hashing and verification
- Retry schedule
- Authorization helper logic

### Layer 2: Controller and security tests

- Every public endpoint
- Success and validation responses
- Role-based access
- Ownership checks
- RFC 7807 errors

### Layer 3: Event contract tests

- Serialization
- Version fields
- Required fields
- Routing keys
- Idempotency IDs
- Privacy constraints

### Layer 4: Service integration tests

- Parcel outbox publishing
- Dispatch projection
- Tracking projection
- Notification consumption
- Merchant webhook consumption

Use H2, mocks, fakes, or test containers as appropriate. These tests must not require a developer to connect to a shared external database.

### Layer 5: Gateway acceptance tests

Test through Gateway routes rather than calling controllers directly:

- Public routes
- Authenticated routes
- JWT issuer/audience validation
- Role failures
- CORS preflight
- Multipart upload
- SSE
- WebSocket upgrade
- Rate limiting
- Circuit breaker
- Request IDs

### Layer 6: Full workflow tests

Build at least these automated scenarios:

- Customer happy path
- Failed delivery and reattempt
- Customer cancellation
- Merchant order and webhook
- Authorization isolation
- Duplicate/out-of-order event handling
- Service outage behavior

## 12. Commit-wise Development Plan for a Lower-Cost Model

Execute these commits in order. Give the model only this document, the files named by the current commit, and the immediately preceding diff. Do not ask it to redesign later commits. Each commit must be independently reviewable and must leave existing tests green.

For every commit, use this instruction: **inspect first, change only the stated scope, preserve existing public contracts unless this step explicitly changes one, add focused tests, run the narrowest relevant test command, and report changed files plus test results.**

### Commit 1 - `docs(contracts): freeze endpoint and event inventory`

Scope: documentation and shared contract tests only. Inventory every frontend call and backend controller; choose the dashboard, proof, receipt, cancellation, merchant, and internal Identity paths specified in this plan. Record method, role, request, response, and error status. Define `ParcelStatusChangedV2` and the merchant webhook v1 payload without changing runtime consumers.

Verify: shared-contract serialization tests and a search proving every frontend API path appears in the inventory.

### Commit 2 - `feat(identity): add scoped idempotent merchant role grant`

Scope: Identity Service only. Add `POST /api/v1/internal/users/{id}/merchant-role`; require the exact Merchant Service client/scope, add `MERCHANT` without replacing other roles, persist merchant ID if the domain supports it, and return a stable DTO. Do not modify the public ADMIN role endpoint.

Verify: tests for success, repeated request, preserved roles, missing user, wrong client, wrong scope, and ordinary user token.

### Commit 3 - `fix(identity): harden internal lookup and bootstrap configuration`

Scope: Identity Service security/configuration only. Replace permissive internal-lookup authorization with explicit allowed service scopes. Make deployed profiles fail safely when bootstrap credentials are absent and disable bootstrap by default outside local development. Preserve local developer configuration through an explicit local profile.

Verify: authorization tests for each allowed/denied caller and bootstrap enabled/disabled/idempotent tests. Never print secrets.

### Commit 4 - `feat(contracts): publish parcel status v2 contract`

Scope: common contracts plus producer/consumer compatibility tests. Add `customerUserId`, `merchantId`, `assigneeUserId`, `changedByUserId`, failure reason, and proof ID as defined in section 4.1. Keep V1 compatibility; do not switch business logic yet.

Verify: JSON round-trip, V1 compatibility, unknown-field, routing-key, and privacy tests.

### Commit 5 - `fix(parcel): emit status v2 and implement cancellation`

Scope: Parcel Service only. Emit V2 transactionally through the outbox and implement owner/admin/authorized-merchant cancellation before pickup. Add history and idempotency. Do not modify downstream consumers in this commit.

Verify: legal/illegal transition tests, ownership tests, repeated cancellation, and atomic parcel/history/outbox persistence.

### Commit 6 - `fix(notification): resolve the actual notification recipient`

Scope: Notification Service only. Consume status V2, use `customerUserId` for preferences and customer delivery, keep `changedByUserId` for audit only, and make `(event, channel, recipient)` idempotent. Use the secured Identity lookup contract.

Verify: courier actor never becomes recipient, duplicate event suppression, preference handling, retry, and missing-user behavior.

### Commit 7 - `fix(parcel-web): align proof receipt and dashboard contracts`

Scope: Parcel Service, Gateway dashboard adapter, and the smallest corresponding frontend client changes. Standardize plural `/proofs`, the selected receipt URL, `/dashboard/summary`, multipart limits, and RFC 7807 errors. Avoid unrelated UI work.

Verify: controller tests, multipart Gateway test, receipt authorization test, and frontend API-client tests.

### Commit 8 - `feat(merchant): integrate approval and API key lifecycle`

Scope: Merchant Service only. Call the new scoped Identity merchant-role grant with retry/reconciliation. Implement API-key create/list/revoke with one-time plaintext display, hashed storage, scopes, and constant-time verification.

Verify: approval retry/idempotency, role-grant failure state, key hashing, scope rejection, revocation, and cross-merchant isolation.

### Commit 9 - `feat(merchant): implement merchant parcel orders`

Scope: Merchant Service and its Parcel Service client only. Implement create/list/get with merchant ownership, idempotency key, stable DTOs, and fee snapshot behavior. Do not add webhooks yet.

Verify: duplicate booking, ownership isolation, validation, upstream failure mapping, and pagination.

### Commit 10 - `feat(merchant): add webhook configuration and signed delivery`

Scope: Merchant Service only. Add webhook URL and secret rotation, consume parcel events, create one logical delivery per event, sign the timestamp plus raw body with HMAC-SHA256, and persist attempts.

Verify: signature fixture, duplicate event, disabled endpoint, secret rotation, private-address/unsafe URL rejection, and successful delivery.

### Commit 11 - `feat(merchant): add webhook retry history and replay`

Scope: Merchant Service only. Add bounded exponential retry, terminal/DLQ state, delivery history, and authorized manual replay. Replay must create an auditable attempt without duplicating the logical event.

Verify: retry schedule, max attempts, restart recovery, replay authorization, and cross-merchant isolation.

### Commit 12 - `fix(gateway): finalize public routes and security boundaries`

Scope: Gateway only. Register final public routes, ensure `/api/v1/internal/**` is unreachable, validate issuer/audience/time claims, remove spoofed trusted headers, set exact CORS, preserve request IDs, and return RFC 7807 errors. Configure multipart, SSE, and WebSocket behavior separately.

Verify: route-table tests; 401 versus 403 versus 503 tests; internal-route denial; CORS preflight; spoofed-header removal; upload, SSE, and WebSocket proxy tests.

### Commit 13 - `test(integration): add non-AI service contract suites`

Scope: tests and test fixtures only. Apply the Identity RBAC suite pattern to Parcel, Dispatch, Merchant, Tracking, Notification, and Pricing. Add event compatibility, duplicate/out-of-order, service-token, and ownership matrices.

Verify: run the non-AI Maven test suite; no production behavior changes are allowed in this commit.

### Commit 14 - `test(e2e): add gateway customer-to-delivery workflows`

Scope: end-to-end harness only. Cover customer booking, dispatch assignment, courier delivery with proof, tracking, notification, merchant webhook, cancellation, failed delivery/reattempt, and controlled dependency outage through the public Gateway.

Verify: one command starts isolated dependencies and runs all workflows twice to expose idempotency defects. No shared external database is required.

### Commit 15 - `feat(web): remove mocks and complete connected smoke flows`

Scope: React API wiring and smoke tests only. Remove obsolete mocks for completed endpoints, use Gateway URLs exclusively, render RFC 7807 errors, refresh claims after role changes, and verify role-specific navigation.

Verify: frontend unit tests plus browser smoke tests for customer, dispatcher, courier, merchant, and admin.

### Deferred commit - AI Assistant

Implement the AI Assistant only after commits 1-15 pass. Its tools must call existing Gateway/domain contracts rather than bypassing authorization or reading service databases directly.

### Token-saving execution rules

- Start a fresh lower-model session per commit; provide only relevant source directories and failing test output.
- Ask for one commit only. Do not include future implementation details beyond the dependency it consumes.
- Prefer targeted commands such as the affected Maven module with `-am` or the affected frontend test file before running the full suite.
- If a test fails, provide only the failure, the changed diff, and the directly involved files on the repair turn.
- Require the model to stop when scope expansion is needed and record the dependency instead of editing another service opportunistically.
- Review and commit each green step before starting the next so rollback and context remain small.

## 13. Definition of Done

Integration is complete only when all of the following are true:

- Every non-AI frontend API call has a matching backend endpoint.
- Every public endpoint is reachable through the Gateway.
- No internal endpoint is publicly routed.
- JWT issuer, audience, expiration, and roles are validated.
- Customer parcel creation reaches the dispatch queue.
- Dispatcher assignment reaches Parcel Service and the courier work list.
- Courier transitions update parcel history and tracking.
- Notifications go to the correct customer or recipient.
- Merchant status changes create signed webhook deliveries.
- Proof upload works through the Gateway.
- Receipt download works through the Gateway.
- Duplicate commands and events do not create duplicate logical results.
- Ownership and role isolation tests pass.
- Service outages produce controlled errors or retries.
- The complete customer-to-delivery workflow passes automatically.
- AI Assistant failures or absence do not affect the rest of the application.

## 14. Final Acceptance Checklist

| Area | Acceptance result |
|---|---|
| Authentication | Register, login, refresh, logout, and Google callback work through Gateway |
| Customer | Estimate, create, list, view, cancel, history, and receipt work |
| Pricing | Estimate and admin management work with correct authorization |
| Dispatch | Queue, suggestions, assignment, reassignment, workload, and stats work |
| Courier | Own assignments, legal status transitions, failure, reattempt, and proof work |
| Tracking | Public, private, SSE, WebSocket, cold backfill, and privacy checks work |
| Notifications | Correct recipient, preferences, idempotency, retry, resend, and logs work |
| Merchant | Approval, role grant, API keys, orders, webhook delivery, DLQ, and replay work |
| Gateway | Routing, JWT, CORS, rate limiting, fallbacks, uploads, and realtime work |
| Workflow | Customer-to-delivered happy path passes through the public Gateway |

## 15. Verification Note

An attempt to run the current non-AI Maven test suite could not proceed because the Spring Boot `4.1.1` parent dependency was not available in the local Maven cache and network access was restricted. This is a dependency-resolution/environment limitation and does not prove that the current tests pass or fail.

Before final acceptance, restore Maven dependency access or provide an approved repository mirror, then run:

```bash
mvn test -pl '!services/ai-assistant-service' -am
```

After service-level tests pass, run the Gateway acceptance suite and the new full workflow suite without connecting to a shared external database.
