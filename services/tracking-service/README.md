# SwiftDrop Live Tracking Service

The Live Tracking service maintains an idempotent, high-performance Redis projection of parcel status events, exposing privacy-safe public tracking, role-authenticated private parcel inspection, and real-time push transports (STOMP over WebSocket and Server-Sent Events) via the gateway without client polling.

---

## Key Features

1. **Idempotent Event Consumption:**
   - Listens on RabbitMQ queue `tracking.parcel-status-changed` bound to exchange `parcel.events`.
   - Failure-safe deduplication with Redis `tracking:event:{eventId}` (24h retention).
   - Poison messages automatically routed to dead-letter exchange `swiftdrop.dlx` and DLQ after 3 failed attempts.

2. **Ordered Read-Model Projection:**
   - Current state stored in `tracking:parcel:{trackingNumber}`.
   - Event timeline stored in sorted set / list `tracking:timeline:{trackingNumber}` with 90-day TTL.
   - Out-of-order tie-breaking preserves latest status even during broker reordering.
   - Automatic cold-miss backfill from `parcel-service`.
   - Graceful degradation to upstream read during Redis outage (no 500s).

3. **Strict Privacy Model:**
   - **Public REST (`GET /api/v1/tracking/{trackingNumber}`):** Unauthenticated; returns only tracking number, status, high-level origin/destination city, courier first name, ETA, updated time, and timeline note. NEVER exposes customer/recipient names, phone numbers, full street addresses, UUIDs, or internal IDs.
   - **Private REST (`GET /api/v1/tracking/{trackingNumber}/full`):** Authenticated with JWT. Validates caller ownership (`ADMIN`, `DISPATCHER`, assigned `COURIER`, owned `MERCHANT`, or ordered `CUSTOMER`). Returns flat 404 rather than 403 to prevent tracking-number enumeration attacks. Includes audit logging for PII access.

4. **Real-Time Push Updates:**
   - **WebSocket / STOMP (`/ws`):** Publishes masked `PublicTracking` updates to `/topic/parcels.{trackingNumber}`. Origin-restricted with session subscription cap (max 10).
   - **Server-Sent Events (`/api/v1/tracking/public/{trackingNumber}/stream`):** Pushes current state immediately on connect, streams future updates, sends 20-second heartbeat comments (`: ping`), and cleans up on client disconnect. Includes `X-Accel-Buffering: no` header for gateway compatibility.

5. **Observability & Metrics:**
   - Micrometer counters and gauges for event processing, duplicates, out-of-order events, consumer errors, DLQ count, Redis fallback, active WebSockets, and active SSE streams.
   - OpenAPI 3 documentation via springdoc available at `/v3/api-docs` and `/swagger-ui.html`.

---

## API Summary

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/tracking/{trackingNumber}` | Public | Privacy-safe public parcel view (`max-age=10`) |
| `GET` | `/api/v1/tracking/public/{trackingNumber}/stream` | Public | Real-time SSE stream with 20s heartbeat |
| `GET` | `/api/v1/tracking/{trackingNumber}/stream` | Public | Alias SSE stream endpoint |
| `GET` | `/api/v1/tracking/{trackingNumber}/full` | JWT | Full private parcel view with PII and ownership checks |
| `WS` | `/ws` | Public | STOMP/SockJS endpoint (topic: `/topic/parcels.{tn}`) |
| `GET` | `/actuator/health` | Public | Readiness and liveness probes |
| `GET` | `/actuator/metrics` | Public | Micrometer operational metrics |
| `GET` | `/v3/api-docs` | Public | OpenAPI specification |

---

## Testing & Verification

Run the entire test suite including unit, slice, and integration tests:

```powershell
mvn -pl services/tracking-service -am test
```

Run verification build:

```powershell
mvn -pl services/tracking-service -am verify
```
