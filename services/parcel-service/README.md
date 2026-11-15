# Parcel service

Java 21 / Spring Boot 4.1.1 implementation of the parcel lifecycle. The root README is the original project plan; this guide describes the implemented service.

## Implementation steps

1. **PAR-1 / #14 — foundation:** Flyway migration for parcels, structured pickup/dropoff addresses, status history, proof records and an event outbox. Tracking numbers use `SD-` plus eight uppercase base-36 characters. A shared database sequence and a uniqueness constraint prevent collisions across service instances. The sequence is never reset by application code; gaps after rollbacks are expected. Hibernate validates the schema instead of modifying it. Optimistic locking rejects concurrent changes.
2. **PAR-2 / #15 — booking:** validated customer/merchant bookings, server-side pricing, immutable fee snapshots, booking-user-scoped idempotency keys, paginated lists and authorized details. A reused key with a changed request returns 409. A concurrent duplicate can return 409; retry the same key after the first transaction commits.
3. **PAR-3 / #16 — lifecycle:** explicit state transitions, dispatcher assignment, assigned-courier authorization, transactional history and status events. RabbitMQ publisher confirms and mandatory returns determine whether the outbox row is marked published. Broker failures leave events pending.
4. **PAR-4 / #17 — evidence:** authenticated MinIO JPEG/PNG proof upload/download and five-minute presigned URLs, delivery evidence checks, failure reasons, paginated history and PDF booking receipts.

The user supplied the acceptance criteria for #14–#17 during implementation. Tests map to them below; the live acceptance checks below cover storage expiration and broker/PostgreSQL behavior. Team identity/pricing integration still needs confirmation before closing issues. #45 (complete the whole Compose stack and cross-service seed data) remains a separate integration task.

## API

All parcel routes require a bearer token. Only health checks are public. IDs in paths are parcel UUIDs, not tracking numbers; the public tracking API belongs to tracking-service.

| Method | Route | Access / behavior |
|---|---|---|
| POST | `/api/v1/parcels` | CUSTOMER, MERCHANT or ADMIN; requires `Idempotency-Key` |
| GET | `/api/v1/parcels?page=0&size=20` | Customer's bookings, merchant's orders, courier's assignments, or all for dispatcher/admin |
| GET | `/api/v1/parcels/{id}` | Owner, owning merchant, assigned courier, dispatcher/admin |
| PUT | `/api/v1/parcels/{id}/assignment` | Dispatcher/admin; `{ "courierId": "UUID" }` |
| PATCH | `/api/v1/parcels/{id}/status` | Assigned courier or dispatcher/admin |
| GET | `/api/v1/parcels/{id}/history` | Same read access as details; paginated |
| POST | `/api/v1/parcels/{id}/proofs` | Assigned courier or dispatcher/admin; multipart field `file` |
| GET | `/api/v1/parcels/{id}/proofs/{proofId}` | Same read access as details; attachment |
| GET | `/api/v1/parcels/{id}/proofs/{proofId}/url` | Authorized read; signed GET URL and `expiresAt` |
| GET | `/api/v1/parcels/{id}/receipt` | Same read access as details; PDF attachment |

Pagination size is 1–100. Unauthorized reads of another customer's parcel return 404. Errors use ProblemDetail and the shared `errorCode` contract. Responses with parcel data should not be cached by clients.

Example booking body:

```json
{
  "pickup": {
    "contactName": "Sender", "phone": "+94771234567", "line1": "1 Main Street",
    "city": "Colombo", "postalCode": "00100", "zone": "COLOMBO"
  },
  "dropoff": {
    "contactName": "Recipient", "phone": "+94777654321", "line1": "2 Lake Road",
    "city": "Kandy", "postalCode": "20000", "zone": "KANDY"
  },
  "size": "SMALL",
  "weightKg": 1.250
}
```

```sh
curl -X POST http://localhost:8080/api/v1/parcels \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H 'Idempotency-Key: booking-demo-001' \
  -H 'Content-Type: application/json' \
  --data-binary @booking.json
```

The authenticated subject supplies the booking owner ID (`customerId`); merchant bookings also store `merchantId` from the signed JWT. A caller cannot supply a booking price or another customer's ID. Successful creation and replay both return 201 with the same resource location.

## Lifecycle

```text
CREATED → PICKED_UP → IN_TRANSIT → OUT_FOR_DELIVERY → DELIVERED
                                      ↓                  (terminal)
                                    FAILED
                                      ↓
                               OUT_FOR_DELIVERY
```

Assignment is allowed in CREATED or FAILED, and is required before the first status transition. Status changes use `{"status":"PICKED_UP"}`. FAILED requires `failedReason`; DELIVERED requires the UUID of a previously uploaded proof for the same parcel. `note` is optional. Repeated or skipped transitions return 409 and produce no extra history/event.

Proofs can be uploaded only in OUT_FOR_DELIVERY. Allowed file extensions are .jpg, .jpeg and .png. Maximum size is 5 MiB; content type, file signature, readability, and dimensions (at most 16 million pixels) are checked. Proof bytes are stored in a private MinIO bucket; PostgreSQL stores metadata and an opaque object key. File names are never used as object keys. A rolled-back upload attempts to delete its object; a process crash or cleanup failure can leave an orphan for operational reconciliation. Authorized users can request signed GET URLs valid for 300 seconds. Keep the bucket private: anonymous/public access would bypass expiry. A shared link acts as a bearer credential until expiration; a user can request a new link while still authorized. Receipts report the confirmed booking fee, not payment settlement.

## Integration contracts to confirm with teammates

- **Identity:** JWT `sub` is a user UUID, `roles` is an array such as `["CUSTOMER"]` (without `ROLE_`). RS256 signing keys come from the configured JWK endpoint; issuer, audience and expiry are validated by Spring Security. Merchant tokens additionally need a signed UUID `merchant_id` claim and MERCHANT role. The identity/merchant team must issue these only for approved merchants. Defaults are placeholders for the identity team's actual endpoints. No unverified identity headers or development auth bypass is provided.
- **Pricing:** POST to configurable `PRICING_SERVICE_URL` + `PRICING_ESTIMATE_PATH`; request is `{"zoneFrom":"COLOMBO","zoneTo":"KANDY","size":"SMALL","weightKg":1.250}`, response is `{"fee":450.00,"currency":"LKR"}`. The caller's bearer token is forwarded. Connection/read timeouts are 3/5 seconds. Failed, malformed, negative or overprecise quotes return 503 without a booking. This endpoint/schema is provisional because pricing-service has no implementation yet.
- **Dispatch:** authenticated dispatcher/admin calls the assignment endpoint. Courier existence/active status must be validated by dispatch before assigning; parcel-service stores only its UUID. Merchant bookings accept JWTs with MERCHANT role and `merchant_id`; raw API keys must first be exchanged/validated by identity/gateway. Client-credentials tokens currently need a UUID subject as well. Confirm these claims with the identity/merchant teams.
- **Events:** the existing shared `ParcelStatusChangedV1` inside `EventEnvelope`, exchange `parcel.events`, routing key `parcel.status.changed.v1`. Initial booking has `fromStatus=null`; customer bookings have `merchantId=null`, while merchant bookings include their signed merchant identity. No changes were made to shared contracts. Consumers own their durable queues/bindings and must deduplicate `eventId`.
- **Delivery semantics:** at least once. A crash after confirmation but before database commit can cause a duplicate. No bound consumer queue means the mandatory publication is returned and stays pending. Pending rows are retried every three seconds. Published outbox retention/cleanup is an operational follow-up; no automatic deletion is performed.

References: [Spring Security JWT resource server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [Spring AMQP confirms and returns](https://docs.spring.io/spring-amqp/reference/amqp/template.html), [MinIO Java API](https://docs.min.io/aistor/developers/sdk/java/api/).

## Configuration and running

Copy `.env.example` values into your own environment (the application does not automatically read `.env` files). Supply a dedicated parcel database and working identity/pricing services. Never use a production database for tests.

| Variable | Purpose / default |
|---|---|
| `PARCEL_DB_URL` | Required PostgreSQL JDBC URL; Neon URLs should require SSL |
| `PARCEL_DB_USERNAME`, `PARCEL_DB_PASSWORD` | Required database credentials |
| `JWT_ISSUER_URI` | `http://identity-service:8080` |
| `JWT_JWK_SET_URI` | `http://identity-service:8080/.well-known/jwks.json` |
| `JWT_AUDIENCE` | `swiftdrop` |
| `PRICING_SERVICE_URL` | `http://pricing-service:8080` |
| `PRICING_ESTIMATE_PATH` | `/api/v1/pricing/estimate` |
| `MINIO_ENDPOINT` | Internal storage URL; `http://localhost:9000` |
| `MINIO_PUBLIC_ENDPOINT` | Browser-reachable signing URL; `http://localhost:9000` |
| `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` | Required; keys must allow proof object read/write/delete |
| `MINIO_PROOF_BUCKET`, `MINIO_REGION` | `parcel-proofs`, `us-east-1`; bucket must already exist and be private |
| `RABBITMQ_HOST`, `RABBITMQ_PORT` | `localhost`, `5672` |
| `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD` | `guest` for local use only |

From the repository root, use Java 21:

```sh
sh mvnw -pl services/parcel-service -am clean verify
sh mvnw -pl services/parcel-service -am install -DskipTests
sh mvnw -f services/parcel-service/pom.xml spring-boot:run
```

On this Mac, Java 21 is at `/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home`; set `JAVA_HOME` to it if the shell defaults to another Java version. The wrapper can be invoked with `sh` because its executable bit is not currently set.

The existing root-context Dockerfile builds this service with shared contracts. The service uses port 8080. Existing `infra/docker-compose.yml` supplies datasource and RabbitMQ settings. Use the parcel overlay to add MinIO, initialize its private bucket, and pass storage/JWT/pricing settings:

```sh
docker compose --env-file infra/.env \
  -f infra/docker-compose.yml -f infra/docker-compose.parcel.yml \
  up --build parcel-service
```

Fill in `infra/.env` from `infra/.env.example` first. The local overlay uses local MinIO administrator credentials for setup and uploads; deployments should provision the bucket separately and supply scoped application credentials. Keep the signed URL public endpoint reachable by the browser; changing a URL's hostname after signing invalidates its signature. Running only parcel-service does not start identity/pricing automatically. Gateway routing and full-stack seed data remain part of #45; this command only prepares parcel and its infrastructure dependencies.

## Validation

The default tests use H2 in PostgreSQL compatibility mode, run the actual Flyway migration, validate JPA mappings, and exercise the HTTP/security filters with MockMvc. Pricing, object storage, and the JWT decoder are mocked in API tests; the actual MinIO SDK signs URLs offline in a separate unit test. Broker confirm/return behavior has focused unit tests. They verify 10,000 generated tracking numbers without collisions and matching `SD-[A-Z0-9]{8}`, merchant isolation, fee snapshots surviving later pricing changes, booking retries, assignment restrictions, lifecycle/failure reattempts, zero events/history after rollback, optimistic locking, executable bytes disguised as JPG, receipts, input validation, and pricing failures. They do not prove PostgreSQL-specific behavior, real token signing/JWK integration, RabbitMQ persistence, or full-stack operation.

Before integration sign-off, run against a disposable PostgreSQL database and RabbitMQ with the actual identity/pricing services, then test broker disconnect/reconnect and consumer event deduplication. See the live acceptance instructions below for disposable infrastructure checks.

### Optional live URL-expiry acceptance test

With a **disposable local MinIO** running, export `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, and `MINIO_SECRET_KEY`, then run:

```sh
PARCEL_LIVE_STORAGE_TESTS=true sh mvnw -pl services/parcel-service -am test
```

`MinioLiveIntegrationTest` creates a fresh private test bucket, uploads an object, confirms a two-second signed URL initially returns 200, then confirms it returns 403 after expiry, and removes the test object/bucket. It is skipped by default. Default tests verify the production URL uses a 300-second signed lifetime; they cannot demonstrate a live server enforcing expiry.

### Disposable PostgreSQL / RabbitMQ / MinIO checks

`compose.test.yaml` creates loopback-only test services on dynamically assigned host ports; it never uses Neon credentials. The MinIO server and CLI images are built from tagged upstream sources in `infra/minio/Dockerfile` because the previously published image registry returned 401 during implementation. The first source build can take several minutes.

```sh
docker compose -p swiftdrop-parcel-acceptance -f services/parcel-service/compose.test.yaml up -d --build --wait
docker compose -p swiftdrop-parcel-acceptance -f services/parcel-service/compose.test.yaml port postgres 5432
docker compose -p swiftdrop-parcel-acceptance -f services/parcel-service/compose.test.yaml port rabbitmq 5672
docker compose -p swiftdrop-parcel-acceptance -f services/parcel-service/compose.test.yaml port minio 9000
```

Use the printed ports to set `SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:<postgres-port>/parcel_test`, `SPRING_DATASOURCE_USERNAME=parcel_test`, `SPRING_DATASOURCE_PASSWORD=parcel-test-only-password`, `RABBITMQ_HOST=127.0.0.1`, `RABBITMQ_PORT=<rabbit-port>`, `RABBITMQ_USERNAME=parcel_test`, `RABBITMQ_PASSWORD=parcel-test-only-password`, `MINIO_ENDPOINT=http://127.0.0.1:<minio-port>`, `MINIO_ACCESS_KEY=parcel_test`, and `MINIO_SECRET_KEY=parcel-test-only-password`.

Run `sh mvnw -pl services/parcel-service -am test` with `PARCEL_LIVE_STORAGE_TESTS=true` and `PARCEL_LIVE_BROKER_TESTS=true`. This runs the API suite against PostgreSQL, exercises real broker confirms/mandatory returns, and checks live presigned URL expiration. Ensure MinIO is ready before starting the tests. The live test classes and database-to-broker API scenario skip unless explicitly enabled. Remove only these disposable fixtures afterward with `docker compose -p swiftdrop-parcel-acceptance -f services/parcel-service/compose.test.yaml down --volumes`.

## Acceptance evidence

| Issue | Check | Current result |
|---|---|---|
| #14 | 10,000 sequence-generated tracking numbers match `SD-[A-Z0-9]{8}` with no collisions | Passed against H2 and disposable PostgreSQL |
| #15 | Customer/merchant ownership returns 404; saved price survives a changed pricing response | Passed against PostgreSQL; pricing and JWT decoding mocked |
| #16 | Legal transition delivers one message; rollback and illegal transitions deliver none | Passed with PostgreSQL and RabbitMQ; confirms and mandatory returns checked |
| #17 | Disguised executable rejected; PDF contains the saved fee | Passed; storage mocked for HTTP tests |
| #17 | Presigned GET includes a signed 300-second expiry | Passed with the real MinIO SDK offline |
| #17 | Storage rejects an expired URL | Passed against a source-built disposable MinIO: URL returns 200 before expiry and 403 afterward |

Identity signing/JWK discovery, the real pricing endpoint, and full-stack #45 seed flows still need team integration checks.

Verification completed on 2026-10-15 with Java 21: the full shared-contract/parcel verification run against PostgreSQL and RabbitMQ passed (28 tests passed, the separate live storage check skipped), then the live MinIO test passed separately. Across those runs, all 29 distinct tests passed. Compose configuration and the MinIO initialization script syntax also validated. The source-built MinIO server was exercised; the full application Compose stack, CLI initialization image and cross-service seed flows remain #45 integration work.
