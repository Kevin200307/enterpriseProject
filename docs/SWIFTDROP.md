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

### 1.6 PII Hygiene & Security (NTF-4)
- **Zero-Raw-PII in Database (`notifications.recipient`):**
  - Raw email addresses and phone numbers are **never stored plaintext** in PostgreSQL.
  - The `recipient_ref` (UUID) references the user in Identity Service.
  - The `recipient` column stores masked identifiers for audit/admin tracking:
    - Emails: `thiran@gmail.com` $\rightarrow$ `th****@gmail.com` via `PiiMaskingConverter.maskEmail(...)`.
    - Phone Numbers: `+94771234567` $\rightarrow$ `+947****4567` via `smsProvider.maskPhoneNumber(...)`.
  - At delivery and retry time, raw contacts are securely resolved just-in-time via `IdentityClient.lookupUser(recipientRef)`.
- **Logback PII Redaction Filter (`logback-spring.xml` + `PiiMaskingConverter`):**
  - Custom Logback `CompositeConverter<ILoggingEvent>` registered as `%maskPii`.
  - Console and container stdout automatically sanitize regex patterns for RFC 5322 emails and international/local phone numbers across all logger statements.
- **Credential Hygiene:**
  - Passwords, API secrets, and raw personal identifiers are strictly excluded from git tracking, logs, and database tables.

---

### 1.7 Delivery Logs & Admin Query API (NTF-4)
- **Admin Paginated Delivery Log Endpoint:**
  - `GET /api/v1/notifications`
  - **Query Parameters:**
    - `status` (`Optional<NotificationStatus>`): e.g. `SENT`, `FAILED`, `SKIPPED`, `PENDING`
    - `channel` (`Optional<NotificationChannel>`): e.g. `EMAIL`, `SMS`
    - `from` (`Optional<Instant>`): ISO-8601 start timestamp
    - `to` (`Optional<Instant>`): ISO-8601 end timestamp
    - `page`, `size`, `sort`: Standard Spring Data pagination (defaults to `size=20, sort=createdAt,desc`)
  - **Filtering Engine:** Powered by dynamic criteria queries in `NotificationLogSpecifications` combined with `JpaSpecificationExecutor`.
- **Parcel Notification History:**
  - `GET /api/v1/notifications/{trackingNumber}`: Chronological delivery audit trail for a specific tracking number.
- **Manual Resend Trigger:**
  - `POST /api/v1/notifications/{id}/resend`: Triggers immediate re-dispatch of a notification by ID.
  - Resolves live unmasked recipient details just-in-time via `IdentityClient` and routes dynamically through `ChannelRouter`.
  - Increments `attempts`, updates `status` (`SENT` or `FAILED`), and sets `sentAt` timestamp upon successful transmission.
- **User Notification History:**
  - `GET /api/v1/notifications/me`: Retrieves notification delivery history for the currently authenticated user (`recipientRef`).
  - Supports resolution via `X-User-Id` gateway header or `userId` query parameter.




