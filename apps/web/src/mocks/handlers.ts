import { http, HttpResponse } from 'msw'
import { env } from '@/config/env'

const base = env.apiBaseUrl

export const handlers = [
  http.get(`${base}/api/v1/pricing/estimate-demo-error`, () =>
    HttpResponse.json(
      {
        type: 'urn:swiftdrop:error:upstream-unavailable',
        title: 'Upstream service unavailable',
        status: 503,
        detail: 'pricing-service is not responding',
        errorCode: 'UPSTREAM_UNAVAILABLE',
        traceId: 'demo-trace',
      },
      { status: 503, headers: { 'Content-Type': 'application/problem+json' } },
    ),
  ),
]
