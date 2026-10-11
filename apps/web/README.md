# SwiftDrop Web

React 18 + TypeScript + Vite + Tailwind SPA for all SwiftDrop personas. Plan: [docs/FRONTEND_PLAN.md](../../docs/FRONTEND_PLAN.md).

## Develop

```bash
cd apps/web
npm install
npm run dev        # http://localhost:5173 — OFFLINE DEMO: an in-browser mock backend, no services needed
npm run dev:live   # talk to a real gateway on :8081 instead (demo logins are hidden)
```

`npm run dev` loads `.env.development` (`VITE_USE_MOCKS=true`). Production builds and the Docker image never include the
mock backend or the demo logins.

### Demo logins (offline demo only)

Open `/login` and click a role to go straight in, or type the email with the shared password **`Demo1234`**.

| Role | Email | What you can do |
|---|---|---|
| Customer | `customer@swiftdrop.demo` | Book, track, cancel, download a receipt |
| Merchant | `merchant@swiftdrop.demo` | Orders, API keys (copy-once), webhook and delivery log |
| Courier | `courier@swiftdrop.demo` | Today's manifest, one-tap status, proof photo |
| Dispatcher | `dispatcher@swiftdrop.demo` | Dispatch board, workload, couriers, parcels |
| Admin | `admin@swiftdrop.demo` | Everything, plus users, merchants, zones, pricing, reports |

These accounts exist only inside the browser mock. The data resets on every page reload. Try tracking
`SD-P5V7JG3L` (out for delivery) on `/track`.

| Script | Purpose |
|---|---|
| `npm run lint` / `typecheck` / `test` / `build` | what CI runs |
| `npm run gen:api` | regenerate `src/api/schema.d.ts` from the gateway OpenAPI (`OPENAPI_URL` to override) |

## Docker

```bash
cp infra/.env.example infra/.env   # fill it in
docker compose -f infra/docker-compose.yml up --build web
# http://localhost:5174  (WEB_HOST_PORT)
```

Config is read at **container start** (`/config.js`), not build time: set `WEB_API_BASE_URL` (the gateway URL as the
browser sees it) and `ASSISTANT_ENABLED`. The gateway's `WEB_ORIGIN` must include the web origin for CORS.

## Rules

- Access token lives in memory only; never `localStorage`.
- All fetching goes through `src/api/client.ts` and TanStack Query; query keys come from `src/api/queryKeys.ts`.
- Errors are RFC 7807 `ApiError`s, rendered via `ErrorState` / `describeError`.
