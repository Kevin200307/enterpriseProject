# SwiftDrop Web

React 18 + TypeScript + Vite + Tailwind SPA for all SwiftDrop personas. Plan: [docs/FRONTEND_PLAN.md](../../docs/FRONTEND_PLAN.md).

## Develop

```bash
cd apps/web
npm install
npm run dev            # http://localhost:5173, talks to the gateway on :8081
VITE_USE_MOCKS=true npm run dev   # run against MSW mocks, no backend needed
```

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
