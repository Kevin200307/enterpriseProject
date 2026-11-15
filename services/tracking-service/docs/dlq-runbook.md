# Tracking Service Dead-Letter Queue (DLQ) Runbook

## Overview

When the `tracking-service` encounters a poison message or an unrecoverable failure during parcel event processing, the message is rejected and automatically routed by RabbitMQ to the dead-letter exchange `swiftdrop.dlx` and stored in the dead-letter queue:

- **Queue Name:** `tracking.parcel-status-changed.dlq`
- **Exchange:** `swiftdrop.dlx`
- **Original Source Queue:** `tracking.parcel-status-changed`
- **Routing Key:** `tracking.parcel-status-changed`

---

## 1. Detection and Alerts

- **Micrometer Metric:** `tracking.dlq.count` (increments whenever an event fails 3 delivery attempts)
- **Log Pattern:** `ERROR ... Failed to process eventId=..., releasing dedup key for retry/DLQ`
- **RabbitMQ Alarm:** Alert fires if `tracking.parcel-status-changed.dlq` message count > 0.

---

## 2. Inspecting Poison Messages

### Using RabbitMQ HTTP API

To inspect messages without acknowledging or losing them:

```bash
curl -u guest:guest -X POST "http://localhost:15672/api/queues/%2F/tracking.parcel-status-changed.dlq/get" \
  -H "Content-Type: application/json" \
  -d '{"count": 10, "ackmode": "ackmode_requeue_true", "encoding": "auto"}'
```

Check the headers for dead-letter diagnostics:
- `x-death`: Details about original queue, exchange, routing key, and reason (`rejected`).
- `x-first-death-reason`: Reason for initial dead-lettering.

---

## 3. Root Cause Investigation

1. **Deserialization Failure:** Message format does not match `EventEnvelope<ParcelStatusChangedV1>`. Check if upstream `parcel-service` introduced an unversioned contract change.
2. **Missing Upstream Snapshot:** Downstream backfill `parcelClient.fetchSnapshot()` failed persistently (e.g. network partition).
3. **Data Constraint Violation:** Invalid tracking number or corrupt UUID in payload.

---

## 4. Replaying Messages

Once the underlying issue is resolved (e.g. deploying a bug fix or restoring upstream connectivity):

### Method A: RabbitMQ Shovel Plugin (Recommended)

1. Enable the shovel plugin:
   ```bash
   rabbitmq-plugins enable rabbitmq_shovel rabbitmq_shovel_management
   ```
2. Create a dynamic shovel to drain the DLQ back to the primary queue:
   ```bash
   curl -u guest:guest -X PUT "http://localhost:15672/api/parameters/shovel/%2F/tracking-replay" \
     -H "Content-Type: application/json" \
     -d '{
       "value": {
         "src-uri": "amqp://",
         "src-queue": "tracking.parcel-status-changed.dlq",
         "dest-uri": "amqp://",
         "dest-queue": "tracking.parcel-status-changed",
         "delete-after": "queue-length"
       }
     }'
   ```
3. Once the shovel finishes moving messages, delete the shovel definition:
   ```bash
   curl -u guest:guest -X DELETE "http://localhost:15672/api/parameters/shovel/%2F/tracking-replay"
   ```

### Method B: Spring AMQP Management Console

1. Navigate to `http://localhost:15672/#/queues/%2F/tracking.parcel-status-changed.dlq`.
2. Expand the **Move messages** panel.
3. Destination queue: `tracking.parcel-status-changed`.
4. Click **Move messages**.

---

## 5. Post-Replay Verification

1. Verify `tracking.parcel-status-changed.dlq` is at 0 messages.
2. Check `tracking-service` logs for `Processed parcel status changed: eventId=...`.
3. Check `tracking.events.processed` metric counter increases.
