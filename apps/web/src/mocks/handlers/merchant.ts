import { http, HttpResponse } from 'msw'
import type { MerchantApplication } from '@/api/types'
import { apiKeys, deliveries, merchants, parcels } from '../db'
import { ago, base, body, empty, guard, paginate, param, problem } from '../util'

const hex = (n: number) =>
  Array.from(crypto.getRandomValues(new Uint8Array(n)), (b) =>
    b.toString(16).padStart(2, '0'),
  ).join('')
const mine = (sub: string) => merchants.find((m) => m.ownerUserId === sub)

export const merchantHandlers = [
  http.get(
    `${base}/api/v1/merchants/me`,
    guard(null, (_i, me) => {
      const m = mine(me.sub)
      return m
        ? HttpResponse.json({
            id: m.id,
            orgName: m.orgName,
            status: m.status,
            webhookUrl: m.webhookUrl ?? null,
          })
        : problem(404, 'RESOURCE_NOT_FOUND', 'No merchant application')
    }),
  ),
  http.post(
    `${base}/api/v1/merchants/register`,
    guard(null, async ({ request }, me) => {
      if (mine(me.sub)) return problem(409, 'CONFLICT', 'You already have a merchant application')
      const a = await body<MerchantApplication>(request)
      const m = {
        id: `m-${merchants.length + 1}`,
        ownerUserId: me.sub,
        ...a,
        status: 'PENDING' as const,
      }
      merchants.push(m)
      return HttpResponse.json({ id: m.id, orgName: m.orgName, status: m.status }, { status: 201 })
    }),
  ),

  http.get(
    `${base}/api/v1/merchants/orders`,
    guard(['MERCHANT'], ({ request }, me) => {
      const url = new URL(request.url)
      const q = (url.searchParams.get('q') ?? '').toLowerCase()
      const all = parcels
        .filter(
          (p) =>
            p.merchantId &&
            p.merchantId === me.merchantId &&
            p.merchantRef?.toLowerCase().includes(q),
        )
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        .map((p) => ({
          merchantRef: p.merchantRef!,
          trackingNumber: p.trackingNumber,
          status: p.status,
          destinationCity: p.dropoff.city,
          createdAt: p.createdAt,
        }))
      return HttpResponse.json(paginate(all, url))
    }),
  ),

  http.get(
    `${base}/api/v1/merchants/me/api-keys`,
    guard(['MERCHANT'], () => HttpResponse.json(apiKeys)),
  ),
  http.post(
    `${base}/api/v1/merchants/me/api-keys`,
    guard(['MERCHANT'], async ({ request }) => {
      const { label, scopes } = await body<{ label: string; scopes: string[] }>(request)
      const key = `sk_live_${hex(16)}`
      const k = {
        id: `k-${apiKeys.length + 1}`,
        prefix: key.slice(0, 16),
        label,
        scopes,
        lastUsedAt: null,
        expiresAt: null,
      }
      apiKeys.push(k)
      return HttpResponse.json({ ...k, key }, { status: 201 }) // the only time the full key is ever returned
    }),
  ),
  http.delete(
    `${base}/api/v1/merchants/me/api-keys/:id`,
    guard(['MERCHANT'], ({ params }) => {
      const i = apiKeys.findIndex((k) => k.id === param(params, 'id'))
      if (i < 0) return problem(404, 'RESOURCE_NOT_FOUND', 'Resource not found')
      apiKeys.splice(i, 1)
      return empty()
    }),
  ),

  http.put(
    `${base}/api/v1/merchants/me/webhook`,
    guard(['MERCHANT'], async ({ request }, me) => {
      const { url } = await body<{ url: string }>(request)
      if (!url.startsWith('https://'))
        return problem(400, 'VALIDATION_FAILED', 'Request body is invalid', {
          violations: [{ field: 'url', message: 'must use https' }],
        })
      const m = mine(me.sub)
      if (m) m.webhookUrl = url
      return HttpResponse.json({ secret: `whsec_${hex(20)}` })
    }),
  ),
  http.get(
    `${base}/api/v1/merchants/me/webhook/deliveries`,
    guard(['MERCHANT'], ({ request }) =>
      HttpResponse.json(paginate(deliveries, new URL(request.url))),
    ),
  ),
  http.post(
    `${base}/api/v1/merchants/me/webhook/deliveries/:id/replay`,
    guard(['MERCHANT'], ({ params }) => {
      const d = deliveries.find((x) => x.id === param(params, 'id'))
      if (!d) return problem(404, 'RESOURCE_NOT_FOUND', 'Resource not found')
      d.state = 'PENDING'
      d.nextRetryAt = new Date(Date.now() + 30_000).toISOString()
      d.history.push({ at: ago(0), status: null, error: 'replay requested' })
      return empty(202)
    }),
  ),
]
