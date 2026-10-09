# Frontend ↔ backend contract checklist

Owner: Thanoj (`apps/web`). Purpose: the SPA was built **before** most services exist, against mocks. This file lists every
backend shape the UI depends on so each owner can confirm it or tell me what to change. Mocks live in the `*.test.tsx`
files next to each screen; the types are in [apps/web/src/api/types.ts](../apps/web/src/api/types.ts) and the calls in
[apps/web/src/api/endpoints/](../apps/web/src/api/endpoints/).

**How to read the status column**

| Mark | Meaning |
|---|---|
| ✅ plan | The route and behaviour are in `plan.md`; only field names below are my assumption |
| ⚠️ guess | Not specified in `plan.md`. I invented it, so **please confirm or propose another** |
| 🔴 blocker | The UI cannot work without this answer |

**How to answer:** comment on your own issue (or reply in the group chat) with the real request/response JSON. Once your
service is up behind the gateway I run `npm run gen:api`, which regenerates `schema.d.ts` from OpenAPI, and the type
checker shows every place the UI disagrees.

---

## 0. Cross-cutting (Kevin, Amandi, Praveena)

| # | Owner | Question | Why | Status |
|---|---|---|---|---|
| X1 | Kevin (GW-2) | CORS: allow `Authorization`, `Content-Type`, `X-Request-Id`, `Idempotency-Key`; `credentials: true`; **expose** `Retry-After` and `Location`; origins `http://localhost:5173` **and** `http://localhost:5174` (Compose web) | The UI reads `Retry-After` for the login countdown and rate-limit messages. Without `Access-Control-Expose-Headers` the browser hides it | 🔴 |
| X2 | Amandi (IDN-2) | `POST /auth/login`, `/auth/refresh` and the Google callback all return `{ "accessToken": "<jwt>", "expiresIn": 900 }` | One token shape for every sign-in path | ⚠️ guess |
| X3 | Amandi (IDN-2) | Refresh cookie `Path` covers `/api/v1/auth/refresh` **and** `/api/v1/auth/logout`; `HttpOnly; SameSite=Strict`. `localhost:5173/5174` → `localhost:8081` is same-site, so Strict works | The access token is memory-only, so a reload depends on this cookie | ✅ plan |
| X4 | Amandi (IDN-2) | Does `/auth/refresh` re-read roles from the database? | After a merchant is approved the UI calls refresh to pick up the new `MERCHANT` role. If roles are copied from the old token, the user must sign out and in | ⚠️ guess |
| X5 | Praveena (PAR-2/3) | Add **`CANCELLED`** to `ParcelStatus` in `packages/common`. Today it has 6 values; PAR-2/3 cancel to a 7th that does not exist | UI handles it already (grey, struck-through chip). A missing enum value means a compile error in the Java services | 🔴 |
| X6 | All | Every non-2xx is RFC 7807 with `errorCode`, `traceId`, `violations[]` (the gateway must normalise upstream errors, GW-3) | The UI maps `errorCode` to messages and shows `traceId` for support | ✅ plan |
| X7 | All | Paged lists use Spring's `Page` shape: `content, totalElements, totalPages, number, size` | One pagination component for every list | ✅ plan |
| X8 | Kevin (GW-2) | Give the web a JWT-protected `GET /api/v1/dashboard/summary` and keep `/ws/**`, `/api/v1/tracking/*/stream` outside buffering (GW-4) | Dashboard + realtime | ✅ plan |

---

## 1. Amandi: identity-service (IDN-1 … IDN-4)

| Endpoint | UI sends | UI expects | Status |
|---|---|---|---|
| `POST /auth/register` | `{ email, password, fullName, phone }` | `201`, any body. The UI then calls `/auth/login` itself. `409 CONFLICT` for a duplicate email | ✅ plan |
| `POST /auth/login` | `{ email, password }` | `{ accessToken }`. `401 INVALID_CREDENTIALS` and `429 RATE_LIMITED` (+ `Retry-After`) | ✅ plan |
| `POST /auth/refresh` | no body, cookie only | `{ accessToken }` or `401` | ✅ plan |
| `POST /auth/logout` | no body | `204` | ✅ plan |
| `GET /auth/oauth2/google/authorize` | query `codeChallenge`, `redirectUri` | `{ authorizationUrl, state }` | ⚠️ guess |
| `GET /auth/oauth2/callback/google` | query `code`, `state`, `codeVerifier`, `redirectUri` | `{ accessToken }`. `409 CONFLICT` if the Google account is linked elsewhere | ⚠️ guess |
| `GET /users/me` | none | `{ id, email, fullName, phone, roles[], status }` | ✅ plan |
| `PATCH /users/me` | `{ fullName, phone }` | the updated user | ✅ plan |
| `GET /users` | query `role`, `status`, `page`, `size` | `Page<{ id, email, fullName, phone, roles[], status }>` | ✅ plan |
| `PATCH /users/{id}/status` | `{ status: "ACTIVE" \| "SUSPENDED" }` | updated user | ⚠️ guess (body shape) |
| `PATCH /users/{id}/roles` | `{ roles: string[] }` (the full new set) | updated user | ⚠️ guess (body shape) |

**Open questions for Amandi**
1. **PKCE (C4):** the UI generates `code_verifier` + S256 `code_challenge`, keeps the verifier in `sessionStorage` for the redirect only, and sends it back to `/callback/google`. Is that how you verify it, or do you hold it server-side?
2. Registered redirect URI in the Google console must be `{origin}/oauth/callback` for **both** `http://localhost:5173` and `http://localhost:5174`.
3. **Forced password change** for the bootstrap admin (IDN-4): how is it signalled, and which endpoint changes the password? The UI has **no screen for this yet**.
4. Should `status: SUSPENDED` make the next `/auth/refresh` return 401? (The UI assumes yes: "signed out everywhere".)

---

## 2. Pavindu: pricing-service (PRC-1 … PRC-4)

| Endpoint | UI sends | UI expects | Status |
|---|---|---|---|
| `GET /pricing/zones` | optional `includeInactive=true` (admin screens only) | `Zone[]` = `{ id, name, code, cities[], active }` | ✅ plan |
| `POST /pricing/zones`, `PUT /pricing/zones/{id}` | `{ name, code, cities[], active }` (code upper-cased) | the zone | ✅ plan |
| `DELETE /pricing/zones/{id}` | none | `204`, or `409 CONFLICT` when an active rule uses it | ✅ plan |
| `GET /pricing/rules` | `includeInactive=true` | `PricingRule[]` = `{ id, zoneFromId, zoneToId, sizeBand, baseFee, perKg, minFee, maxWeightKg, active, effectiveFrom, effectiveTo }` | ⚠️ guess (field names) |
| `POST /pricing/rules`, `PUT /pricing/rules/{id}` | same fields, fees as JSON **numbers**, dates `YYYY-MM-DD`, `effectiveTo: null` when open-ended | the rule | ⚠️ guess |
| `POST /pricing/estimate` | `{ fromCity, toCity, size, weightKg, express }` | `{ zoneFrom, zoneTo, baseFee, weightSurcharge, expressSurcharge, total, currency, ruleId, calculatedAt }` | ✅ plan |

**Notes**
- Estimate errors: `404 RESOURCE_NOT_FOUND` → "we don't deliver there yet"; `400 VALIDATION_FAILED` → shown on the weight field.
- The booking form's city box is a `<datalist>` built from `zones[].cities`. I do **not** call `/pricing/zones/resolve`. Say if you want me to.
- Please confirm money is returned as a number or string (the UI formats both).
- **Zones are readable by any signed-in user**, writable by ADMIN only. The UI relies on that for the dropdowns.

---

## 3. Praveena: parcel-service (PAR-1 … PAR-4)

| Endpoint | UI sends | UI expects | Status |
|---|---|---|---|
| `POST /parcels` | `{ pickup, dropoff, size, weightKg, express, merchantRef? }` where an address is `{ contactName, phone, line1, line2?, city, postalCode }` | the parcel incl. `id`, `trackingNumber`. `201` | ✅ plan (field names ⚠️) |
| `GET /parcels` | `status` (repeatable), `from`, `to` (`YYYY-MM-DD`), `page`, `size`, `sort=createdAt,desc` | `Page<Parcel>` | ⚠️ guess (`from`/`to` names) |
| `GET /parcels/{id}` | none | `Parcel` = `{ id, trackingNumber, status, size, weightKg, express, fee, pickup, dropoff, createdAt, courierName?, proofUrl?, failureReason? }`. **404 (not 403)** when not the caller's | ✅ plan |
| `GET /parcels/by-tracking/{tn}` | none | same `Parcel` (admin/dispatcher search) | ✅ plan |
| `GET /parcels/{id}/history` | none | `[{ fromStatus, toStatus, actorName?, note?, failureReason?, proofUrl?, occurredAt }]` | ⚠️ guess (field names) |
| `POST /parcels/{id}/cancel` | none | updated parcel. `409 INVALID_STATUS_TRANSITION` after pickup | ✅ plan |
| `PATCH /parcels/{id}/status` | `{ toStatus, note?, failureReason?, proofUrl? }` | updated parcel | ✅ plan |
| `POST /parcels/{id}/proof` | `multipart/form-data`, field name **`file`** | `{ proofUrl }` (presigned) | ⚠️ guess |
| `GET /parcels/{id}/receipt` | Bearer token | the PDF bytes | ✅ plan |

**Open questions for Praveena**
1. **`merchantRef`** (C14): a merchant creating an order through the UI sends their own reference in `POST /parcels`. Is there such a field, or should this go through `/merchants/orders`?
2. **Fee breakdown (C7):** the detail page shows only the total `fee`. If the parcel stores `baseFee`, `weightSurcharge`, `expressSurcharge`, send them and I'll show the breakdown.
3. `courierName`: first name only is fine. Phone masking before pickup, as in PAR-4, is shown exactly as sent.
4. **CORS for the receipt and proof URLs:** the proof image is an `<img src=presignedUrl>` and needs no CORS, but please set a short expiry and I'll refetch before it lapses.

---

## 4. Methsara: dispatch-service (DSP-1 … DSP-4)

| Endpoint | UI sends | UI expects | Status |
|---|---|---|---|
| `GET /couriers/me/assignments` | none | `Assignment[]` = `{ id, parcelId, trackingNumber, status, pickup, dropoff, size, weightKg, assignedAt }` for **today**, including DELIVERED/FAILED ones (the UI counts them) | ⚠️ guess |
| `GET /dispatch/queue` | optional `zone` (zone **code**) | `QueueItem[]` = `{ parcelId, trackingNumber, pickupCity, size, weightKg, createdAt }` | ⚠️ guess (shape; plan says a list) |
| `GET /dispatch/suggest` | `parcelId` | `Suggestion[]` = `{ courierId, name, reason, remainingCapacity }` (top 3) | ⚠️ guess |
| `GET /dispatch/workload` | none | `WorkloadRow[]` = `{ courierId, name, active, activeAssignments, deliveredToday, failedToday, remainingCapacity }` | ⚠️ guess |
| `POST /dispatch/assignments` | `{ parcelId, courierId }`, and `?reassign=true` when confirming | `200`. `409 CONFLICT` when already assigned elsewhere | ✅ plan |
| `GET /dispatch/stats/daily` | `from`, `to` (`YYYY-MM-DD`) | `[{ date, created, completed, failed }]` | ⚠️ guess |
| `GET /couriers` | `zone`, `vehicle`, `active`, `page`, `size` | `Page<Courier>` = `{ id, userId, name, vehicleType, homeZoneCode, phone, maxDailyLoad, active }` | ⚠️ guess |
| `POST /couriers` | `{ userId, vehicleType, homeZoneCode, phone, maxDailyLoad, active }` | the courier | ✅ plan |
| `PATCH /couriers/{id}` | partial of the same | the courier. `409 CONFLICT` with the tracking numbers to reassign in `detail` | ✅ plan |

**Open questions for Methsara**
1. **🔴 Courier's own list (C12):** `GET /couriers/{id}/manifest` needs a courier id that a courier never knows. I call **`GET /couriers/me/assignments`**. Does it include today's completed parcels, and is it already in manifest order (I sort by pickup city then drop-off city myself)?
2. `name` on couriers/workload comes from identity. Who joins it?
3. Who can call `/couriers` and `/dispatch/*`? The UI lets DISPATCHER and ADMIN in.
4. Registering a courier needs a list of COURIER-role users, but `GET /users` is admin-only. A dispatcher currently types the user id by hand. Should the courier endpoint offer a lookup?

---

## 5. Charithma: merchant-service (MER-1 … MER-4)

| Endpoint | UI sends | UI expects | Status |
|---|---|---|---|
| `POST /merchants/register` | `{ orgName, businessRegNo, contactEmail, contactPhone }` | `201`. `409 CONFLICT` if one exists | ✅ plan |
| **`GET /merchants/me`** | none | `{ id, orgName, status, webhookUrl? }`, `404` if no application | 🔴 not in plan (C13) |
| `GET /merchants` | `status`, `page`, `size` | `Page<Merchant>` = `{ id, orgName, businessRegNo, contactEmail, contactPhone, status, approvedBy?, approvedAt? }` | ✅ plan |
| `POST /merchants/{id}/approve \| reject \| suspend` | optional `{ note }` | updated merchant | ✅ plan (note ⚠️) |
| **`GET /merchants/orders`** | `q` (reference search), `page`, `size`. Called with the **user's JWT** | `Page<{ merchantRef, trackingNumber, status, destinationCity, createdAt }>` | ⚠️ guess (C14) |
| `GET /merchants/me/api-keys` | none | `[{ id, prefix, label, scopes[], lastUsedAt?, expiresAt? }]` | ✅ plan |
| `POST /merchants/me/api-keys` | `{ label, scopes: ["orders:write","orders:read"] }` | the same fields **plus `key`** (once). Scope names are mine | ⚠️ guess (scopes) |
| `DELETE /merchants/me/api-keys/{id}` | none | `204` | ✅ plan |
| `PUT /merchants/me/webhook` | `{ url }` | `{ secret }` (once). Saving **always rotates** the secret; the UI warns about it | ✅ plan |
| `GET /merchants/me/webhook/deliveries` | `page`, `size` | `Page<{ id, eventId, trackingNumber, url, attempts, lastStatus?, state, nextRetryAt?, history?[{ at, status?, error? }] }>` | ⚠️ guess (history) |
| `POST /merchants/me/webhook/deliveries/{id}/replay` | none | `202`/`200` | ✅ plan |

**Open questions for Charithma**
1. **🔴 `GET /merchants/me` (C13).** Without it, an applicant cannot see whether they were approved.
2. **🔴 Orders with a user JWT (C14).** Your order endpoints accept an API key or a client-credentials token. The console is signed in as a normal user with the `MERCHANT` role. May that user `GET /merchants/orders`? (Or should the UI list parcels via `GET /parcels`, which already filters by `merchantId`?)
3. Scope names for keys, and whether `expiresAt` can be chosen at creation (the UI has no expiry picker yet).
4. A webhook delivery's `history` (per-attempt time, HTTP status or error): is it available? It is optional in the UI.

---

## 6. Maheesha: tracking-service (TRK-1 … TRK-4)

| Endpoint | UI expects | Status |
|---|---|---|
| `GET /tracking/{tn}` (unauthenticated) | `{ trackingNumber, status, originCity, destinationCity, courierFirstName?, eta?, updatedAt, timeline[{ status, occurredAt, note? }] }`. `400` bad format, flat `404`, `429` + `Retry-After` | ⚠️ guess (names) |
| STOMP over SockJS at **`/ws`**, subscribe **`/topic/parcels.{tn}`** | message body = the same JSON as above, with a monotonic `updatedAt` | ⚠️ guess (C10) |
| `GET /tracking/{tn}/stream` (SSE) | each `data:` line = the same JSON; comment heartbeat every 20 s | ⚠️ guess |

**Notes**
- The UI never applies a push whose `updatedAt` is older than what it already shows, so out-of-order events are safe.
- Fallback ladder: WebSocket → SSE → poll every 15 s. SockJS needs `/ws/info` and the transport paths proxied (Kevin, GW-4).
- Tracking numbers are validated client-side with `^SD-[A-HJ-NP-Z2-9]{8}$` before any request.

---

## 7. Tharusha: ai-assistant-service (AI-1 … AI-4)

| Endpoint | UI sends | UI expects | Status |
|---|---|---|---|
| `POST /assistant/chat/stream` | `{ message, conversationId? }`, header `Accept: text/event-stream` | SSE with events **`token`** `{ text }`, **`tool`** `{ name, arguments }`, **`done`** `{ conversationId }`, **`error`** `{ message }` | 🔴 event names are mine (C15) |
| `GET /assistant/conversations/{id}` | none | `{ conversationId, messages: [{ role: "user"\|"assistant", content }] }`. `404` for someone else's id | ⚠️ guess |
| `DELETE /assistant/conversations/{id}` | none | `204` | ✅ plan |

**Notes**
- It is a **POST**, so the UI uses `fetch` + a stream parser, not `EventSource`.
- `429 RATE_LIMITED` → "hourly message limit". `503` → the launcher disappears for the session. This is also how the UI copes with `ASSISTANT_ENABLED=false` on the server, in addition to the runtime `config.js` flag.
- Input is capped at 2000 characters client-side to match AI-4.

---

## 8. Kevin: gateway extras

| Item | Need | Status |
|---|---|---|
| `GET /dashboard/summary` | `{ parcels: { today, outForDelivery, deliveredToday, failedToday } \| null, dispatch: { unassigned } \| null, merchants: { pending } \| null }`. A `null` section means that upstream was down; the tile shows "Unavailable" and the rest still renders (C16) | ⚠️ guess |
| WebSocket/SSE paths | see §6 | ⚠️ guess |
| CSP | the SPA uses no inline scripts (runtime config is the external `/config.js`). Needs `connect-src` for the gateway and `ws:`/`wss:`, and `img-src` for the MinIO host | ✅ proposal |

---

## 9. Not built yet (so nothing to confirm)

- Forced password change screen (needs Amandi's answer above).
- Notification preferences and the admin notification log (NTF-3/4).
- Pricing rule matrix view, and a city → zone `resolve` call.
