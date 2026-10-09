import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { tokenStore } from '@/auth/tokenStore'
import { server } from '@/test/server'
import { ApiError } from './ApiError'
import { api, setSessionExpiredHandler } from './client'

const fail = (p: Promise<unknown>) =>
  p.then(
    () => {
      throw new Error('expected the request to fail')
    },
    (e: unknown) => e as ApiError,
  )

const url = (p: string) => `http://localhost:8081${p}`

beforeEach(() => {
  tokenStore.clear()
  tokenStore.setRefresher(async () => null)
})

describe('api client', () => {
  it('parses an RFC 7807 body into a typed ApiError', async () => {
    server.use(
      http.post(url('/api/v1/parcels'), () =>
        HttpResponse.json(
          {
            title: 'Validation failed',
            status: 400,
            detail: 'Request body is invalid',
            errorCode: 'VALIDATION_FAILED',
            traceId: 'abc123',
            violations: [{ field: 'weightKg', message: 'must be greater than 0' }],
          },
          { status: 400, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    const err = await fail(api.post('/api/v1/parcels', {}))
    expect(err).toBeInstanceOf(ApiError)
    expect(err.errorCode).toBe('VALIDATION_FAILED')
    expect(err.violations).toEqual([{ field: 'weightKg', message: 'must be greater than 0' }])
    expect(err.traceId).toBe('abc123')
  })

  it('turns a non-problem 5xx into INTERNAL_ERROR', async () => {
    server.use(http.get(url('/x'), () => new HttpResponse('<html>boom</html>', { status: 502 })))
    const err = await fail(api.get('/x'))
    expect(err.errorCode).toBe('INTERNAL_ERROR')
    expect(err.status).toBe(502)
  })

  it('reports an unreachable server as NETWORK_ERROR', async () => {
    server.use(http.get(url('/x'), () => HttpResponse.error()))
    const err = await fail(api.get('/x'))
    expect(err.errorCode).toBe('NETWORK_ERROR')
  })

  it('attaches the bearer token and omits empty query params', async () => {
    tokenStore.set('tok')
    let seen: Request | undefined
    server.use(
      http.get(url('/api/v1/parcels'), ({ request }) => {
        seen = request
        return HttpResponse.json({ content: [] })
      }),
    )
    await api.get('/api/v1/parcels', { status: 'CREATED', q: '', page: 0 })
    expect(seen?.headers.get('Authorization')).toBe('Bearer tok')
    expect(new URL(seen!.url).search).toBe('?status=CREATED&page=0')
  })

  it('refreshes exactly once for three parallel 401s, then retries each', async () => {
    tokenStore.set('old')
    const refresh = vi.fn(async () => 'new')
    tokenStore.setRefresher(refresh)
    server.use(
      http.get(url('/secure'), ({ request }) =>
        request.headers.get('Authorization') === 'Bearer new'
          ? HttpResponse.json({ ok: true })
          : new HttpResponse(null, { status: 401 }),
      ),
    )
    const results = await Promise.all([api.get('/secure'), api.get('/secure'), api.get('/secure')])
    expect(results).toEqual([{ ok: true }, { ok: true }, { ok: true }])
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('signals session expiry when refresh fails', async () => {
    const expired = vi.fn()
    setSessionExpiredHandler(expired)
    server.use(http.get(url('/secure'), () => new HttpResponse(null, { status: 401 })))
    await expect(api.get('/secure')).rejects.toBeInstanceOf(ApiError)
    expect(expired).toHaveBeenCalled()
  })

  it('does not retry /auth calls that opt out', async () => {
    const refresh = vi.fn(async () => 'new')
    tokenStore.setRefresher(refresh)
    server.use(http.post(url('/api/v1/auth/login'), () => new HttpResponse(null, { status: 401 })))
    await expect(
      api.post('/api/v1/auth/login', {}, { skipAuthRetry: true }),
    ).rejects.toBeInstanceOf(ApiError)
    expect(refresh).not.toHaveBeenCalled()
  })
})
