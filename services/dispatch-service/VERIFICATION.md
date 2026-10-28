# Dispatch verification

Verified on 2026-10-27 with Java 21, Maven 3.9.12 and Docker Desktop. No dispatch integration tests are skipped when Docker is unavailable: they fail so a push cannot mistake missing infrastructure for success.

## Automated checks

- `mvn -pl services/dispatch-service -am verify`: shared contracts and the full dispatch suite passed. The suite covers real PostgreSQL migration/uniqueness/capacity constraints, real RabbitMQ, signed JWTs, HTTP upstream interactions, concurrency, crash recovery, ordering, privacy and API compatibility.
- Dispatch JaCoCo gates passed: 80% lines and 65% branches required; measured coverage exceeded both (approximately 96% lines and 79% branches). Generated report: `target/site/jacoco/index.html`.
- `CourierRankingStrategyTest` also verifies that a custom Spring bean replaces the default strategy.
- `mvn -pl services/identity-service,services/parcel-service -am verify`: passed. Existing environment-dependent identity/parcel tests remain skipped (one identity context test and three parcel tests for live broker/storage). Dispatch's own PostgreSQL/RabbitMQ tests ran, and the separate live journey exercised the real parcel publisher and identity database.
- Existing gateway suite: 68 of 69 tests passed in the combined run; the remaining tracking event-stream test timed out. The entire `SecurityConfigTests` class (14 tests), including that test, passed on a focused rerun. Gateway source was unchanged.
- `git diff --check`, Node syntax check and PowerShell parser check passed.

## API coverage

| API | Evidence |
| --- | --- |
| GET `/api/v1/couriers` | Pagination, zone/vehicle/active filters, invalid filters; live gateway request |
| POST `/api/v1/couriers` | Creation, duplicate user, validation, missing/wrong-role/suspended identity, identity outage; live authenticated internal lookup |
| PATCH `/api/v1/couriers/{id}` | Fields, immutable identity, missing profile, inactive state, capacity/deactivation conflicts with tracking numbers; live update |
| GET `/api/v1/couriers/me/assignments` | Own open work, empty work, spoofed headers/query ignored, recipient phone masking and unmasking; live login and manifest |
| GET `/api/v1/couriers/{id}/manifest` | Dated manifest, owner access, another courier gets 404, dispatcher access; live owner and cross-courier requests |
| GET `/api/v1/dispatch/queue` | Created projection, one-second broker delivery, optional zone, empty queue after assignment, return after unassignment, duplicates and old events; live create through gateway |
| POST `/api/v1/dispatch/assignments` | Success, same-courier idempotency, explicit reassignment, inactive/full/missing courier, missing parcel, pending/rejected remote command, double-assign/overbook races; live parcel confirmation |
| DELETE `/api/v1/dispatch/assignments/{id}` | Unassignment/reason, missing ID, stale assignment conflict, pending retry, capacity release; live upstream unassignment |
| GET `/api/v1/dispatch/commands/{id}` | Pending command polling and missing ID; recovery and terminal states |
| GET `/api/v1/dispatch/workload` | Active/reserved capacity, delivered/failed today, release on terminal events; live aggregate |
| GET `/api/v1/dispatch/suggest` | Matching zone, active status, capacity, top three, deterministic tie and replaceable bean; live suggestion |
| GET `/api/v1/dispatch/stats/daily` | Counts, empty days, invalid ranges, Colombo midnight boundary; live report |
| GET `/v3/api-docs`, `/actuator/health` | Public success; protected actuator surfaces reject anonymous |

Authorization tests use actual signed JWTs through the normal security filter and JWKS lookup. They cover issuer, audience, expiry, UUID subject, known role arrays, malformed roles, anonymous 401, CUSTOMER 403, courier isolation and prevention of courier self-assignment.

## Live gateway journey

`node services/dispatch-service/scripts/verify-local-stack.mjs` passed 31 HTTP checks. It ran the actual gateway, identity, parcel and dispatch jars against disposable PostgreSQL, Redis and RabbitMQ. It registered accounts, granted test roles in its isolated database, logged in, created a courier, patched/listed it, booked a parcel through gateway, observed its real outbox event in dispatch, suggested and assigned a courier, verified the parcel assignee UUID, read the courier manifest, checked authorization/privacy and unassigned in both services.

Pricing was the only HTTP stub. A durable status-event subscriber stood in for legacy tracking/notification consumers so both outbox event types had broker routes. Delivery proofs and pricing rule calculation are outside this journey. Test processes and containers were removed after verification. Machine-readable evidence and logs: `target/live-smoke/report.json` and `target/live-smoke/*.log` (ignored by Git).

The journey found and verified a fix for identity's pre-existing PostgreSQL `citext`/JPA email type mismatch. The new dispatch event, identity service grant, parcel command and 202 pending behavior still require the cross-team review listed in README before merge.
