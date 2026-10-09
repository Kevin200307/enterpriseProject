import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'

// Incidental fetches that many tests trigger just by navigating (post-login landing page, city list on the
// booking page). Tests that care about these override them with server.use().
export const server = setupServer(
  http.get('http://localhost:8081/api/v1/pricing/zones', () => HttpResponse.json([])),
  http.get('http://localhost:8081/api/v1/couriers/me/assignments', () => HttpResponse.json([])),
  http.get('http://localhost:8081/api/v1/parcels', () =>
    HttpResponse.json({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
  ),
)
