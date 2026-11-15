# Dispatch and couriers

Owner: Methsara (@Dimeshmethsara). Java 21, Spring Boot 4, PostgreSQL, Flyway, JPA and RabbitMQ.

The API matches `apps/web/src/api/endpoints/{couriers,dispatch}.ts` and its DTOs. Gateway already routes `/api/v1/dispatch/**` and `/api/v1/couriers/**` here. OpenAPI with request examples is served at `/v3/api-docs` and through `/docs/services/dispatch/v3/api-docs` on the gateway.

## Run and verify

Set `DISPATCH_DB_URL`, `DISPATCH_DB_USERNAME`, `DISPATCH_DB_PASSWORD`, `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `IDENTITY_ISSUER_URI`, `IDENTITY_JWKS_URI`, `JWT_AUDIENCE`, `IDENTITY_SERVICE_URL`, `PARCEL_SERVICE_URL` and `DISPATCH_CLIENT_SECRET`. Compose supplies service DNS names. The secret must be random, at least 32 characters, and identical in dispatch and identity. Never commit actual credentials.

```powershell
mvn -pl services/dispatch-service -am verify
```

Docker must be running. Verification starts real PostgreSQL 16 and RabbitMQ 3.13 containers. It runs migration constraints, signed JWT authorization, HTTP integration with identity/parcel stubs, all dispatch/courier API routes, assignment concurrency, projection ordering/deduplication, retry/DLQ, and event-to-manifest tests. Tests fail if Docker is unavailable; they are not silently skipped. JaCoCo gates: 80% lines and 65% branches; report: `target/site/jacoco/index.html`.

Also verify changed upstream modules: `mvn -pl services/identity-service,services/parcel-service -am verify`. The dispatch API tests stub their upstream HTTP services; the live gateway journey is a separate smoke check.

For an isolated live gateway journey, package gateway, identity, parcel and dispatch, then run `node services/dispatch-service/scripts/verify-local-stack.mjs`. It starts temporary containers and the four actual service jars, registers test accounts, grants roles only in its disposable identity database, logs in and exercises dispatch through gateway. Pricing is an HTTP stub. A durable legacy status subscriber supplies the broker route normally owned by tracking/notification. Containers/processes are cleaned up; the report and logs are in `target/live-smoke/`.

## APIs and authorization

| Method | Path | Access |
| --- | --- | --- |
| GET, POST | `/api/v1/couriers` | DISPATCHER, ADMIN |
| PATCH | `/api/v1/couriers/{id}` | DISPATCHER, ADMIN |
| GET | `/api/v1/couriers/me/assignments` | COURIER, DISPATCHER, ADMIN; own open assignments |
| GET | `/api/v1/couriers/{id}/manifest?date=YYYY-MM-DD` | DISPATCHER, ADMIN; COURIER only own profile |
| GET | `/api/v1/dispatch/queue?zone=COLOMBO` | DISPATCHER, ADMIN |
| POST | `/api/v1/dispatch/assignments?reassign=false` | DISPATCHER, ADMIN |
| DELETE | `/api/v1/dispatch/assignments/{id}` | DISPATCHER, ADMIN; JSON `{ "reason": "..." }` |
| GET | `/api/v1/dispatch/commands/{id}` | DISPATCHER, ADMIN |
| GET | `/api/v1/dispatch/workload` | DISPATCHER, ADMIN |
| GET | `/api/v1/dispatch/suggest?parcelId=UUID` | DISPATCHER, ADMIN |
| GET | `/api/v1/dispatch/stats/daily?from=YYYY-MM-DD&to=YYYY-MM-DD` | DISPATCHER, ADMIN |

Only health and OpenAPI are public. JWT validation checks RSA signature through JWKS, issuer, audience, expiry, UUID subject and known roles. Headers and query parameters cannot override the JWT identity. Another courier's manifest returns 404. Errors use `application/problem+json`, stable `errorCode`, and field `violations` where applicable. Deactivation with open or pending assignments returns 409 with `trackingNumbers`.

Courier list supports `zone`, `vehicle=BIKE|THREEWHEEL|VAN`, `active=true|false`, `page` and `size` (1–100). Create requires an existing active COURIER identity user, phone `0XXXXXXXXX` or `+94XXXXXXXXX`, uppercase zone code and load 1–10000. Zone code validation is syntactic; pricing owns the zone catalogue. A profile's `userId` is immutable. `name` comes from identity at registration.

## Events, ordering and privacy

Existing `ParcelStatusChangedV1` is unchanged. It cannot populate dispatch's addresses, zone, size or weight. Dispatch therefore consumes the separate full snapshot `ParcelDispatchChangedV1`:

- Exchange `parcel.events` (durable topic), routing key `parcel.dispatch.changed.v1`.
- Queue `dispatch.parcel.dispatch.changed.v1` (durable), DLQ `dispatch.parcel.dispatch.changed.v1.dlq`.
- DLX `swiftdrop.dlx` (durable direct), dead-letter routing key is the source queue name.
- Three listener attempts with 100/200 ms backoff, then reject without requeue. Poison messages do not block subsequent events.
- Envelope `eventId` is inserted with `ON CONFLICT DO NOTHING` in the same DB transaction as the projection. Failures roll back both. Unique event IDs deduplicate broker redelivery.
- Snapshot `parcelVersion` is the persisted parcel optimistic version; older/equal snapshots cannot overwrite newer data. Wall clock arrival order is never used for ordering.
- Parcel-service writes snapshots on creation, assignment/unassignment and status change in its existing transactional outbox. Publish polling is 500 ms. Broker confirmation is required before marking published.
- Queue contains only CREATED parcels without an assignee or pending command, ordered by original creation time then UUID. FAILED/DELIVERED parcels leave the queue. The current shared status enum has no CANCELLED state; adding cancellation needs a coordinated contract change.

Snapshots contain private addresses; restrict RabbitMQ access to internal services. Dispatch never logs payloads, credentials, addresses or phone numbers. The courier manifest masks the recipient phone before pickup (CREATED); it is visible after pickup. Workload and manifests each use one SQL query. Stats count assignment creation/completion/failure, use Asia/Colombo midnight boundaries, include empty days and allow at most 366 days.

`Queues.DISPATCH_PARCEL_STATUS_CHANGED` is also defined for naming compatibility, but dispatch does not bind its projection to the insufficient legacy payload. Golden JSON for the new event is in `packages/common/src/test/resources/golden/parcel.dispatch.changed.v1.json`.

## Assignment consistency and recovery

`courierId` in the dispatch API means the **courier profile UUID**. Parcel-service receives that courier's **identity user UUID** for its own courier authorization.

1. Lock the parcel projection and reserve target capacity under a courier row lock. Save a PENDING command with a unique pending parcel key. PostgreSQL checks `0 <= open_load <= max_daily_load` and only one active assignment/pending command per parcel. Pending reservations count toward capacity.
2. Call parcel-service with the command UUID, expected previous assignee, target user UUID, actor and reason. Propagate `X-Request-ID`. No database lock is held during HTTP.
3. Parcel-service locks its parcel, compares expected assignee, writes the change, event and command receipt atomically. Retrying the same command returns its original receipt, even if the first response was lost.
4. Confirm locally: close the previous assignment, preserve its actor/time/reason, release old capacity and append the new assignment. A definitive 400/404/409 rejection marks the command REJECTED and releases target capacity.
5. On network/5xx failure return **202 Accepted** with `Location: /api/v1/dispatch/commands/{id}`. Keep the reservation. The scheduled worker retries pending commands every second, including after process restart. Clients can poll the command's state. Normal confirmed and same-courier idempotent requests return 200.

There is no distributed transaction. A crash after parcel success is recovered using the idempotent command receipt. Never manually delete pending commands or release their reservations while remote outcome is unknown. Restore credentials/connectivity and allow retry. Investigate repeated REJECTED conflicts using the request ID, reconcile the upstream projection, then submit a new dispatch request. Replay repaired DLQ messages with their original event IDs; failed transactions did not retain those IDs.

Different-courier assignment returns 409 unless `reassign=true`. Inactive/full couriers are rejected. Assignment changes are allowed before pickup or after FAILED. Same active courier is a no-op. Completed/failed assignments release capacity. Here `maxDailyLoad` bounds simultaneous open plus reserved work; deliveries free a slot during the day. Historical manifests include assignments created on the requested Colombo date, including closed records.

The ranking strategy is replaceable by supplying a `CourierRankingStrategy` bean. Default ranks active matching-zone couriers with remaining capacity by current reserved/open load and UUID, returns at most three and explains each suggestion. Suggestions are advisory; assignment rechecks capacity under lock.

## Service authentication and handoff

Identity provides `POST /api/v1/auth/service-token`, Basic client ID `dispatch-service`, configured secret and form `grant_type=client_credentials`. Tokens expire after five minutes and carry `client_id=dispatch-service`, scope `dispatch.internal`, and no human roles. Dispatch caches them until 30 seconds before expiry. Only that signed service identity may use `GET /api/v1/internal/users/{id}` and `PUT /api/v1/internal/parcels/{id}/assignment`. These internal routes are not routed by gateway.

Contract review is still required from Praveena (snapshot and idempotent parcel command), Kevin (service grant and Compose secret), Maheesha (topology/version ordering) and Thanoj (existing frontend DTO compatibility and 202 behavior). The repository implementation and tests provide a concrete proposal; they do not establish team agreement.

Live journey: create a parcel through gateway as CUSTOMER; wait for it in dispatch queue as DISPATCHER; register an active COURIER identity user; assign using the courier profile UUID; verify `GET /api/v1/parcels/{id}` has the courier user UUID; log in as that courier and read `/api/v1/couriers/me/assignments`; unassign with a reason and verify both stores and queue. Run `scripts/Smoke-Test.ps1` against a configured stack using test accounts.
