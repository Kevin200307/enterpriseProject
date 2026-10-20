import { http, HttpResponse } from 'msw'
import type { Claims } from '@/auth/types'
import type {
  CreateParcelInput,
  Parcel,
  PricingRule,
  PublicTracking,
  StatusChange,
  ZoneInput,
} from '@/api/types'
import { allowedNext } from '@/lib/transitions'
import type { ParcelStatus } from '@/lib/status'
import {
  couriers,
  estimate,
  history,
  newTrackingNumber,
  parcels,
  proofImage,
  pushHistory,
  rules,
  users,
  zones,
  type DemoParcel,
} from '../db'
import { base, body, empty, guard, open, paginate, param, problem } from '../util'

const STAFF = ['DISPATCHER', 'ADMIN'] as const
const isStaff = (me: Claims) => STAFF.some((r) => me.roles.includes(r))
const courierOf = (me: Claims) => couriers.find((c) => c.userId === me.sub)

/** Mirrors the real rule: a caller only ever sees their own parcels; anything else is a plain 404. */
export function canSee(p: DemoParcel, me: Claims) {
  if (isStaff(me)) return true
  if (me.roles.includes('COURIER') && p.courierId && p.courierId === courierOf(me)?.id) return true
  if (me.roles.includes('MERCHANT') && p.merchantId && p.merchantId === me.merchantId) return true
  return p.customerId === me.sub
}

const INTERNAL = ['customerId', 'merchantId', 'merchantRef', 'courierId'] as const
/** Strips the mock's bookkeeping fields so responses match the real parcel DTO. */
const view = (p: DemoParcel): Parcel => {
  const copy: Record<string, unknown> = { ...p }
  for (const k of INTERNAL) delete copy[k]
  return copy as unknown as Parcel
}
const notFound = () => problem(404, 'RESOURCE_NOT_FOUND', 'Resource not found')
const actorName = (me: Claims) => users.find((u) => u.id === me.sub)?.fullName ?? me.email

function pdf(lines: string[]) {
  const stream = `BT /F1 14 Tf 72 740 Td ${lines.map((l, i) => `${i ? '0 -22 Td ' : ''}(${l.replace(/[()\\]/g, '')}) Tj`).join(' ')} ET`
  const objs = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ]
  let out = '%PDF-1.4\n'
  const offsets = objs.map(
    (o, i) => (
      (out += `${i + 1} 0 obj\n${o}\nendobj\n`),
      out.length - `${i + 1} 0 obj\n${o}\nendobj\n`.length
    ),
  )
  const xref = out.length
  out += `xref\n0 ${objs.length + 1}\n0000000000 65535 f \n${offsets.map((o) => `${String(o).padStart(10, '0')} 00000 n \n`).join('')}trailer\n<< /Size ${objs.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`
  return out
}

export const parcelHandlers = [
  http.get(
    `${base}/api/v1/parcels`,
    guard(null, ({ request }, me) => {
      const url = new URL(request.url)
      const statuses = url.searchParams.getAll('status')
      const from = url.searchParams.get('from')
      const to = url.searchParams.get('to')
      const all = parcels
        .filter((p) => canSee(p, me))
        // a customer/merchant list is "mine"; staff see everything
        .filter(
          (p) =>
            isStaff(me) ||
            me.roles.includes('COURIER') ||
            p.customerId === me.sub ||
            (!!me.merchantId && p.merchantId === me.merchantId),
        )
        .filter((p) => !statuses.length || statuses.includes(p.status))
        .filter((p) => !from || p.createdAt.slice(0, 10) >= from)
        .filter((p) => !to || p.createdAt.slice(0, 10) <= to)
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      return HttpResponse.json(paginate(all.map(view), url))
    }),
  ),
  http.get(
    `${base}/api/v1/parcels/by-tracking/:tn`,
    guard(null, ({ params }, me) => {
      const p = parcels.find((x) => x.trackingNumber === param(params, 'tn').toUpperCase())
      return p && canSee(p, me) ? HttpResponse.json(view(p)) : notFound()
    }),
  ),
  http.get(
    `${base}/api/v1/parcels/:id`,
    guard(null, ({ params }, me) => {
      const p = parcels.find((x) => x.id === param(params, 'id'))
      return p && canSee(p, me) ? HttpResponse.json(view(p)) : notFound()
    }),
  ),
  http.get(
    `${base}/api/v1/parcels/:id/history`,
    guard(null, ({ params }, me) => {
      const p = parcels.find((x) => x.id === param(params, 'id'))
      return p && canSee(p, me) ? HttpResponse.json(history[p.id] ?? []) : notFound()
    }),
  ),
  http.post(
    `${base}/api/v1/parcels`,
    guard(['CUSTOMER', 'MERCHANT'], async ({ request }, me) => {
      const b = await body<CreateParcelInput>(request)
      const est = estimate({
        fromCity: b.pickup.city,
        toCity: b.dropoff.city,
        size: b.size,
        weightKg: b.weightKg,
        express: b.express,
      })
      if ('error' in est)
        return est.error === 'city'
          ? problem(404, 'RESOURCE_NOT_FOUND', `We don't deliver to ${est.city} yet`)
          : problem(400, 'VALIDATION_FAILED', 'Request body is invalid', {
              violations: [
                {
                  field: 'weightKg',
                  message: `must be above 0 and at most ${est.max} kg for this size`,
                },
              ],
            })
      const p: DemoParcel = {
        id: `p-${parcels.length + 1}`,
        trackingNumber: newTrackingNumber(),
        status: 'CREATED',
        size: b.size,
        weightKg: b.weightKg,
        express: b.express,
        fee: Number(est.total),
        pickup: b.pickup,
        dropoff: b.dropoff,
        createdAt: new Date().toISOString(),
        customerId: me.sub,
        merchantId: b.merchantRef ? me.merchantId : undefined,
        merchantRef: b.merchantRef,
      }
      parcels.push(p)
      pushHistory(p, 'CREATED', actorName(me))
      return HttpResponse.json(view(p), {
        status: 201,
        headers: { Location: `/api/v1/parcels/${p.id}` },
      })
    }),
  ),
  http.post(
    `${base}/api/v1/parcels/:id/cancel`,
    guard(null, ({ params }, me) => {
      const p = parcels.find((x) => x.id === param(params, 'id'))
      if (!p || !canSee(p, me)) return notFound()
      if (p.status !== 'CREATED')
        return problem(
          409,
          'INVALID_STATUS_TRANSITION',
          `A ${p.status.toLowerCase().replace(/_/g, ' ')} parcel can no longer be cancelled`,
        )
      p.status = 'CANCELLED'
      pushHistory(p, 'CANCELLED', actorName(me))
      return HttpResponse.json(view(p))
    }),
  ),
  http.patch(
    `${base}/api/v1/parcels/:id/status`,
    guard(['COURIER', 'DISPATCHER', 'ADMIN'], async ({ request, params }, me) => {
      const p = parcels.find((x) => x.id === param(params, 'id'))
      if (!p || !canSee(p, me)) return notFound()
      const b = await body<StatusChange>(request)
      if (!allowedNext(p.status).includes(b.toStatus as ParcelStatus))
        return problem(
          409,
          'INVALID_STATUS_TRANSITION',
          `Cannot move from ${p.status} to ${b.toStatus}`,
        )
      if (b.toStatus === 'FAILED' && !b.failureReason?.trim())
        return problem(400, 'VALIDATION_FAILED', 'Request body is invalid', {
          violations: [{ field: 'failureReason', message: 'is required when a delivery fails' }],
        })
      p.status = b.toStatus
      if (b.toStatus === 'FAILED') p.failureReason = b.failureReason
      if (b.toStatus === 'DELIVERED' && b.proofUrl) p.proofUrl = b.proofUrl
      pushHistory(p, b.toStatus, actorName(me), {
        note: b.note,
        failureReason: b.failureReason,
        proofUrl: b.proofUrl,
      })
      return HttpResponse.json(view(p))
    }),
  ),
  http.post(
    `${base}/api/v1/parcels/:id/proof`,
    guard(['COURIER'], async ({ params }) => {
      const p = parcels.find((x) => x.id === param(params, 'id'))
      return p ? HttpResponse.json({ proofUrl: proofImage(p.trackingNumber) }) : notFound()
    }),
  ),
  http.get(
    `${base}/api/v1/parcels/:id/receipt`,
    guard(null, ({ params }, me) => {
      const p = parcels.find((x) => x.id === param(params, 'id'))
      if (!p || !canSee(p, me)) return notFound()
      const text = pdf([
        'SwiftDrop receipt',
        `Tracking: ${p.trackingNumber}`,
        `From: ${p.pickup.city}  To: ${p.dropoff.city}`,
        `Size: ${p.size}  Weight: ${p.weightKg} kg`,
        `Total: LKR ${Number(p.fee).toFixed(2)}`,
        `Status: ${p.status}`,
      ])
      return new HttpResponse(text, {
        headers: { 'Content-Type': 'application/pdf', 'Content-Disposition': 'attachment' },
      })
    }),
  ),

  // public tracking: cities and courier first name only
  http.get(
    `${base}/api/v1/tracking/:tn`,
    open(({ params }) => {
      const p = parcels.find((x) => x.trackingNumber === param(params, 'tn').toUpperCase())
      if (!p) return notFound()
      const timeline = (history[p.id] ?? []).map((h) => ({
        status: h.toStatus,
        occurredAt: h.occurredAt,
        note: h.note,
      }))
      const t: PublicTracking = {
        trackingNumber: p.trackingNumber,
        status: p.status,
        originCity: p.pickup.city,
        destinationCity: p.dropoff.city,
        courierFirstName: p.courierName?.split(' ')[0],
        eta:
          p.status === 'DELIVERED' || p.status === 'CANCELLED' || p.status === 'FAILED'
            ? undefined
            : new Date(Date.now() + 8 * 3_600_000).toISOString(),
        updatedAt: timeline[timeline.length - 1]?.occurredAt ?? p.createdAt,
        timeline,
      }
      return HttpResponse.json(t)
    }),
  ),
  http.get(`${base}/ws/*`, () => new HttpResponse(null, { status: 404 })),
  http.get(`${base}/api/v1/tracking/:tn/stream`, () => new HttpResponse(null, { status: 404 })),

  // pricing
  http.get(
    `${base}/api/v1/pricing/zones`,
    guard(null, ({ request }) =>
      HttpResponse.json(
        new URL(request.url).searchParams.get('includeInactive') === 'true'
          ? zones
          : zones.filter((z) => z.active),
      ),
    ),
  ),
  http.post(
    `${base}/api/v1/pricing/zones`,
    guard(['ADMIN'], async ({ request }) => {
      const b = await body<ZoneInput>(request)
      const z = { id: `z-${crypto.randomUUID().slice(0, 6)}`, ...b }
      zones.push(z)
      return HttpResponse.json(z, { status: 201 })
    }),
  ),
  http.put(
    `${base}/api/v1/pricing/zones/:id`,
    guard(['ADMIN'], async ({ request, params }) => {
      const z = zones.find((x) => x.id === param(params, 'id'))
      if (!z) return notFound()
      Object.assign(z, await body<ZoneInput>(request))
      return HttpResponse.json(z)
    }),
  ),
  http.delete(
    `${base}/api/v1/pricing/zones/:id`,
    guard(['ADMIN'], ({ params }) => {
      const id = param(params, 'id')
      const i = zones.findIndex((x) => x.id === id)
      if (i < 0) return notFound()
      if (rules.some((r) => r.active && (r.zoneFromId === id || r.zoneToId === id)))
        return problem(409, 'CONFLICT', 'This zone is used by an active pricing rule')
      zones.splice(i, 1)
      return empty()
    }),
  ),
  http.get(
    `${base}/api/v1/pricing/rules`,
    guard(['ADMIN'], () => HttpResponse.json(rules)),
  ),
  http.post(
    `${base}/api/v1/pricing/rules`,
    guard(['ADMIN'], async ({ request }) => {
      const r = {
        id: `r-${crypto.randomUUID().slice(0, 6)}`,
        ...(await body<Omit<PricingRule, 'id'>>(request)),
      } as PricingRule
      rules.push(r)
      return HttpResponse.json(r, { status: 201 })
    }),
  ),
  http.put(
    `${base}/api/v1/pricing/rules/:id`,
    guard(['ADMIN'], async ({ request, params }) => {
      const r = rules.find((x) => x.id === param(params, 'id'))
      if (!r) return notFound()
      Object.assign(r, await body<Omit<PricingRule, 'id'>>(request))
      return HttpResponse.json(r)
    }),
  ),
  http.post(
    `${base}/api/v1/pricing/estimate`,
    guard(null, async ({ request }) => {
      const b = await body<Parameters<typeof estimate>[0]>(request)
      const est = estimate(b)
      if (!('error' in est)) return HttpResponse.json(est)
      return est.error === 'city'
        ? problem(404, 'RESOURCE_NOT_FOUND', `We don't deliver to ${est.city} yet`)
        : problem(400, 'VALIDATION_FAILED', 'Request body is invalid', {
            violations: [
              {
                field: 'weightKg',
                message: `must be above 0 and at most ${est.max} kg for this size`,
              },
            ],
          })
    }),
  ),
]
