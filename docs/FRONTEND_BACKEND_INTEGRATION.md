# Frontend ↔ Backend Integration Plan

This plan describes how to connect the existing SwiftDrop UI to the backend services. It is based on the current
frontend and service source; it is an integration checklist, not confirmation that every service is running or that
every contract has been verified against a live environment.

The UI already has screens and API clients for most primary workflows. The main work is to resolve contract
mismatches, complete missing backend endpoints, and verify each workflow against the running services. See the
[frontend contract checklist](./FRONTEND_CONTRACTS.md) for field-level assumptions and the
[frontend execution plan](./FRONTEND_PLAN.md) for screen and architecture details.

## 1. Contract gaps to resolve first

| Priority | Finding | Integration work |
|---|---|---|
| **P0 — Booking** | The UI sends and displays `express`, but the parcel booking request and response do not include it. Parcel-service addresses require a `zone`, which the customer parcel booking payload does not provide. | Decide whether express delivery is supported end-to-end. Since booking already offers express pricing, add support throughout the parcel contract, persistence, and events, or remove express from the UI until supported. Align address fields and zone resolution. Keep merchant references on the merchant-order flow rather than sending them to parcel booking. |
| **P0 — Parcel lists and details** | The UI expects a page shape with `number`; parcel-service returns `page`. Parcel history is paged but the UI treats it as an array. The UI sends status/date filters that the list controller does not accept. Admin tracking lookup expects full parcel details but currently calls the snapshot endpoint. | Standardize the page envelope, consume history as a page, implement supported filters or remove them from the UI, and call the details-by-tracking endpoint when full details are needed. |
| **P0 — Proof of delivery** | The courier UI uploads proof and submits its ID, which is supported. The UI expects a parcel `proofUrl`, however, while parcel responses do not provide one; the UI also lacks clients for the proof-download and expiring-URL endpoints. History fields differ too: the backend returns `actorId`, `proofId`, and `failedReason`, while the UI models names and URLs. | Add API methods for proof download/URL retrieval and fetch short-lived URLs when displaying proof. Align the history DTO and render what the backend actually provides. |
| **P0 — Notifications** | **Implemented:** per-status email/SMS preferences, gateway-derived user identity, and UI-compatible paginated history/log response DTOs. Date-only admin filters now cover the full UTC date. | Verify with the focused notification service tests and a live authenticated gateway flow; this does not confirm the remote database has applied its migration. |
| **P0 — AI assistant** | The UI and authenticated assistant API share the `token`, `tool`, `done`, and `error` stream events. Tracking and pricing tools still need live-service and role-flow verification. | Verify caller-token relay to the parcel and pricing services, then test the assistant against the real stack. |

Primary files to update include [parcel API types](../apps/web/src/api/types.ts), [parcel endpoint client](../apps/web/src/api/endpoints/parcels.ts),
[notification screen](../apps/web/src/features/notifications/NotificationsPage.tsx), and the corresponding service DTOs/controllers.

## 1.1 Implementation progress

- **Parcel booking and details:** list filters, paged history, detail-by-tracking, proof URL display, pricing request/response alignment, zone propagation, and express persistence have been implemented.
- **Notifications:** per-status email and SMS settings, identity from `X-User-Id`, paginated user history, UI-shaped history/admin log DTOs, and full-day UTC date filtering have been implemented. Flyway V3 adds persisted email status choices.
- **AI assistant API:** authenticated streaming chat and owner-scoped in-memory conversation history now match the widget's `token`, `tool`, `done`, and `error` SSE contract. The conversation store retains the latest 20 turns and expires idle conversations after 24 hours.
- **AI assistant tools:** `trackParcel` uses the parcel service's privacy-safe tracking snapshot, and `estimateFee` uses the pricing service. `listMyParcels` is only offered to customer-only JWT roles and uses the parcel service's owner-scoped list endpoint; its response omits contact names, phone numbers, street addresses, and postal codes. All tools relay the caller's bearer token and emit the UI's tool-use event.
- **Still outstanding:** verify OpenAI configuration, real-stack role journeys, and Neon connectivity. Local `infra/.env` still has template DB passwords, so no live database authentication or migrations have been run.

## 2. Backend functions without a clear UI action

These are backend capabilities without an obvious user-facing action in the current UI. Add UI only where there is a
real workflow; do not expose internal service-to-service endpoints as user features.

| Backend capability | Current UI coverage | Suggested decision |
|---|---|---|
| Dispatch unassign and asynchronous command-status polling | The UI supports assignment and courier suggestions, but has no unassign/command client or controls. | Add correction/retry controls to the dispatch board if dispatchers need to unassign or monitor pending commands. |
| Date-specific courier manifest | Courier UI uses “my assignments.” | Decide whether couriers need a date selector or manifest-order workflow; otherwise the existing assignments view may be sufficient. |
| Authenticated full tracking | UI uses public tracking. | Add a private detail view only if customers, merchants, or staff need details beyond the privacy-safe public view. |
| Merchant order by ID | UI supports order creation and listing but has no order-detail route. | Add an order detail screen if that workflow is needed. |
| Zone resolution | UI builds city suggestions from the zones list. | Keep this approach unless a canonical city-to-zone resolution flow is needed. |
| Notification lookup by tracking number and health endpoint | No corresponding user-facing actions. | Keep internal/operational unless support staff need parcel-specific notification history or health visibility. |
| Identity internal lookup, JWKS, and service-token endpoints | No corresponding UI actions. | Keep these service-to-service or infrastructure-only. |

## 3. Delivery sequence

### Phase 1 — Freeze and verify contracts

1. Reconcile this plan and [FRONTEND_CONTRACTS.md](./FRONTEND_CONTRACTS.md) with the current source.
2. Use gateway OpenAPI as the shared contract where available; regenerate frontend API types after contracts stabilize.
3. Resolve the P0 mismatches above before wiring additional screens.
4. Confirm gateway authentication, CORS, error normalization, pagination conventions, and runtime API configuration.

### Phase 2 — Connect the customer journey

Verify the full flow against real services: login/session refresh, price estimates, booking, parcel list/detail/history,
cancellation, receipt download, and public tracking. Confirm request filters, status transitions, validation, and error
responses match the UI.

### Phase 3 — Connect courier and dispatch operations

Verify courier assignments, dispatch queue and suggestions, assignment, courier status updates, proof upload/display,
workload, and daily reports. Add unassign/command-status controls if they are in scope.

### Phase 4 — Connect merchant and admin workflows

Verify dashboard, user and courier management, zones/pricing, merchant approval, merchant orders, API keys, and webhook
configuration/delivery replay. Confirm role authorization on every action.

### Phase 5 — Finish notifications and assistant

Align notification DTOs and pagination. Verify assistant tool calls, caller authorization, streaming errors, and
OpenAI configuration against the running services. Confirm the customer-only “my parcels” tool against the gateway
and parcel service using a real customer token.

### Phase 6 — Prove the integrated app

Run the real Compose stack with mocks disabled. Add contract/integration tests for each P0 mismatch and smoke-test each
role’s primary journey. Keep mocks for isolated UI tests; they are not evidence of backend integration.

## 4. Completion criteria

Integration is complete when:

- Every visible action calls a real backend endpoint and is authorized for the current role.
- Frontend request/response types match actual service payloads.
- Loading, empty, validation, conflict, and unavailable states are handled correctly.
- The main customer, courier, dispatcher, admin, and merchant journeys pass against the running stack.
