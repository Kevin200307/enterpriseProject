# SwiftDrop — System Design & Architecture Pack

> **Enterprise Application Development (EAD) Project**  
> **Team SwiftDrop** | University of Moratuwa

---

## 1. Notification Service (`services/notification-service`)
**Owner:** Thiranjaya Siriwardhana (`@thirsturn`)  
**Assigned Issues:** `[NTF-1]`, `[NTF-2]`, `[NTF-3]`, `[NTF-4]`

### 1.1 Architecture & Role
The **Notification Service** is an asynchronous, event-driven microservice in the SwiftDrop ecosystem responsible for customer and partner communication via Email and SMS across parcel lifecycle states (`CREATED`, `PICKED_UP`, `IN_TRANSIT`, `OUT_FOR_DELIVERY`, `DELIVERED`, `FAILED`).

- **Broker Integration:** Subscribes to RabbitMQ topic exchange `parcel.events` with queue `notifications.parcel-status-changed` bound to routing key `parcel.status.changed.v1`.
- **Dead Letter Routing:** Unprocessable / poison messages are diverted to DLQ `notifications.parcel-status-changed.dlq` without blocking the consumer queue.

---

### 1.2 Integration Patterns & Resilience (NTF-1)
1. **Idempotency Strategy:**
   - Database table `notifications` enforces a composite unique constraint:
     ```sql
     CONSTRAINT uq_event_channel_recipient UNIQUE (event_id, channel, recipient_ref)
     ```
   - Duplicate events are detected prior to dispatch and safely skipped with no duplicate emails or SMS messages dispatched.
2. **Identity Resolution:**
   - Consumes only `actorId` / `parcelId` from events (never raw PII).
   - Resolves email and phone securely via internal REST client to Identity Service:
     `GET /api/v1/internal/users/lookup?userId={uuid}` with offline resilient fallback.
3. **Scheduled Exponential Backoff Retry:**
   - A background `@Scheduled` worker (`NotificationRetryScheduler`) retries notifications with status `FAILED`.
   - Exponential delay: $2^{\text{attempts}}$ seconds, capping at 5 maximum attempts.

---

### 1.3 Channel Strategy & Open-Closed Principle (NTF-3)
The notification delivery architecture adheres strictly to the **Strategy Pattern** and **Open-Closed Principle (OCP)**:
- **`NotificationChannelHandler` Interface:**
  Core notification workflows depend on `NotificationChannelHandler` rather than concrete delivery classes.
- **Implementations:**
  - `EmailChannelHandler`: Dispatches responsive multipart HTML emails via `EmailNotificationService`.
  - `MockSmsChannelHandler`: Dispatches masked SMS logs. Pluggable and conditionally enabled via:
    `@ConditionalOnProperty(name = "app.notification.sms.provider", havingValue = "mock", matchIfMissing = true)`.
    Real SMS providers (e.g. Twilio, Dialog) can be dropped in as Spring beans without altering listeners or business logic.
- **`ChannelRouter`:**
  Dispatches notification requests dynamically to the matching channel strategy bean.

---

### 1.4 Email Channel, Templates & Mailpit (NTF-2)
- **Local Infrastructure:**
  Mailpit (`axllent/mailpit`) is integrated in `infra/docker-compose.yml` on SMTP port `1025` and Web UI port `8025`.
- **Thymeleaf Template Engine:**
  Status-specific responsive HTML email templates with plain-text alternatives in `templates/email/`:
  1. `booking-confirmed.html` (`CREATED`): Shows tracking number, fee snapshot, and tracking button.
  2. `picked-up.html` (`PICKED_UP`): Courier confirmation and transit link.
  3. `in-transit.html` (`IN_TRANSIT`): Live tracking portal button.
  4. `out-for-delivery.html` (`OUT_FOR_DELIVERY`): Arrival notice.
  5. `delivered.html` (`DELIVERED`): Direct link to delivery receipt PDF (`/api/v1/parcels/{tn}/receipt`).
  6. `delivery-failed.html` (`FAILED`): Failure cause and scheduled re-attempt notice.
- **`NotificationRenderer` Component:** Decoupled template rendering verified with golden-file unit tests ensuring zero unresolved `${...}` tokens.

---

### 1.5 User Notification Preferences (NTF-3)
- Database table `notification_preferences` stores user delivery preferences.
- REST Endpoints:
  - `GET /api/v1/notifications/preferences?userId={uuid}`
  - `PUT /api/v1/notifications/preferences`
- **Default Policy:**
  - Email: Enabled for all statuses.
  - SMS: Enabled strictly for `OUT_FOR_DELIVERY` and `DELIVERED`.
  - Disabling SMS in preferences records rows as `SKIPPED` without delivery attempts.
- **Global Kill Switch:**
  Supported via `NOTIFICATIONS_ENABLED=false` for emergency outage suppression or testing.

---

### 1.6 PII Hygiene & Security (NTF-4 preview)
- Phone numbers in logs and database are masked (e.g., `+947****567`).
- Recipient email address local parts are redacted in operational logging.
- Passwords, keys, and raw personal identifiers are strictly excluded from git and container logs.
