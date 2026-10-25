import { http, HttpResponse } from 'msw'
import type {
  Assignment,
  CourierInput,
  DailyStat,
  DashboardSummary,
  QueueItem,
  Suggestion,
  WorkloadRow,
} from '@/api/types'
import {
  couriers,
  isOpen,
  merchants,
  parcels,
  users,
  zoneOf,
  type DemoCourier,
  type DemoParcel,
} from '../db'
import { ago, base, body, guard, paginate, param, problem } from '../util'

const mask = (phone: string) =>
  phone.length > 3 ? '•'.repeat(phone.length - 3) + phone.slice(-3) : phone
const loadOf = (c: DemoCourier) => parcels.filter((p) => p.courierId === c.id && isOpen(p)).length
const workload = (c: DemoCourier): WorkloadRow => ({
  courierId: c.id,
  name: c.name,
  active: c.active,
  activeAssignments: loadOf(c),
  deliveredToday: parcels.filter((p) => p.courierId === c.id && p.status === 'DELIVERED').length,
  failedToday: parcels.filter((p) => p.courierId === c.id && p.status === 'FAILED').length,
  remainingCapacity: Math.max(0, c.maxDailyLoad - loadOf(c)),
})
const waiting = (p: DemoParcel) => p.status === 'CREATED' && !p.courierId

const STAFF = ['DISPATCHER', 'ADMIN'] as const

export const opsHandlers = [
  http.get(
    `${base}/api/v1/couriers/me/assignments`,
    guard(['COURIER'], (_i, me) => {
      const c = couriers.find((x) => x.userId === me.sub)
      const list: Assignment[] = parcels
        .filter((p) => c && p.courierId === c.id)
        .map((p) => ({
          id: `a-${p.id}`,
          parcelId: p.id,
          trackingNumber: p.trackingNumber,
          status: p.status,
          pickup: p.pickup,
          // recipient phone stays masked until the parcel has been picked up
          dropoff:
            p.status === 'CREATED' ? { ...p.dropoff, phone: mask(p.dropoff.phone) } : p.dropoff,
          size: p.size,
          weightKg: p.weightKg,
          assignedAt: p.createdAt,
        }))
      return HttpResponse.json(list)
    }),
  ),

  http.get(
    `${base}/api/v1/dispatch/queue`,
    guard([...STAFF], ({ request }) => {
      const zone = new URL(request.url).searchParams.get('zone')
      const items: QueueItem[] = parcels
        .filter(waiting)
        .filter((p) => !zone || zoneOf(p.pickup.city)?.code === zone)
        .map((p) => ({
          parcelId: p.id,
          trackingNumber: p.trackingNumber,
          pickupCity: p.pickup.city,
          size: p.size,
          weightKg: p.weightKg,
          createdAt: p.createdAt,
        }))
      return HttpResponse.json(items)
    }),
  ),
  http.get(
    `${base}/api/v1/dispatch/suggest`,
    guard([...STAFF], ({ request }) => {
      const p = parcels.find((x) => x.id === new URL(request.url).searchParams.get('parcelId'))
      const zone = p && zoneOf(p.pickup.city)?.code
      const out: Suggestion[] = couriers
        .filter((c) => c.active && c.maxDailyLoad - loadOf(c) > 0)
        .sort(
          (a, b) =>
            Number(b.homeZoneCode === zone) - Number(a.homeZoneCode === zone) ||
            loadOf(a) - loadOf(b),
        )
        .slice(0, 3)
        .map((c) => ({
          courierId: c.id,
          name: c.name,
          remainingCapacity: c.maxDailyLoad - loadOf(c),
          reason: `${c.homeZoneCode === zone ? 'Home zone matches' : 'Other zone'}, ${loadOf(c)} active`,
        }))
      return HttpResponse.json(out)
    }),
  ),
  http.get(
    `${base}/api/v1/dispatch/workload`,
    guard([...STAFF], () => HttpResponse.json(couriers.map(workload))),
  ),
  http.post(
    `${base}/api/v1/dispatch/assignments`,
    guard([...STAFF], async ({ request }) => {
      const { parcelId, courierId } = await body<{ parcelId: string; courierId: string }>(request)
      const reassign = new URL(request.url).searchParams.get('reassign') === 'true'
      const p = parcels.find((x) => x.id === parcelId)
      const c = couriers.find((x) => x.id === courierId)
      if (!p || !c) return problem(404, 'RESOURCE_NOT_FOUND', 'Resource not found')
      if (p.courierId === c.id) return HttpResponse.json({}, { status: 200 })
      if (p.courierId && !reassign)
        return problem(409, 'CONFLICT', 'This parcel is already assigned to another courier')
      if (!c.active || loadOf(c) >= c.maxDailyLoad)
        return problem(409, 'CONFLICT', 'That courier is inactive or at full capacity')
      p.courierId = c.id
      p.courierName = c.name
      return HttpResponse.json({}, { status: 200 })
    }),
  ),
  http.get(
    `${base}/api/v1/dispatch/stats/daily`,
    guard([...STAFF], ({ request }) => {
      const q = new URL(request.url).searchParams
      const from = new Date(q.get('from') ?? ago(24 * 13)),
        to = new Date(q.get('to') ?? new Date().toISOString())
      const out: DailyStat[] = []
      for (
        let d = new Date(from.toISOString().slice(0, 10));
        d <= to && out.length < 120;
        d = new Date(d.getTime() + 86_400_000)
      ) {
        const n = d.getUTCDate()
        out.push({
          date: d.toISOString().slice(0, 10),
          created: 8 + ((n * 7) % 9),
          completed: 5 + ((n * 5) % 8),
          failed: (n * 3) % 3,
        })
      }
      return HttpResponse.json(out)
    }),
  ),

  http.get(
    `${base}/api/v1/couriers`,
    guard([...STAFF], ({ request }) => {
      const url = new URL(request.url)
      const [zone, vehicle, active] = ['zone', 'vehicle', 'active'].map((k) =>
        url.searchParams.get(k),
      )
      const all = couriers.filter(
        (c) =>
          (!zone || c.homeZoneCode === zone) &&
          (!vehicle || c.vehicleType === vehicle) &&
          (!active || String(c.active) === active),
      )
      return HttpResponse.json(paginate(all, url))
    }),
  ),
  http.post(
    `${base}/api/v1/couriers`,
    guard([...STAFF], async ({ request }) => {
      const b = await body<CourierInput>(request)
      const u = users.find((x) => x.id === b.userId)
      if (!u || !u.roles.includes('COURIER'))
        return problem(400, 'VALIDATION_FAILED', 'That user does not hold the COURIER role', {
          violations: [{ field: 'userId', message: 'must belong to a user with the COURIER role' }],
        })
      const c: DemoCourier = {
        id: `c-${couriers.length + 1}`,
        userId: u.id,
        name: u.fullName,
        vehicleType: b.vehicleType,
        homeZoneCode: b.homeZoneCode,
        phone: b.phone,
        maxDailyLoad: b.maxDailyLoad,
        active: b.active ?? true,
      }
      couriers.push(c)
      return HttpResponse.json(c, { status: 201 })
    }),
  ),
  http.patch(
    `${base}/api/v1/couriers/:id`,
    guard([...STAFF], async ({ request, params }) => {
      const c = couriers.find((x) => x.id === param(params, 'id'))
      if (!c) return problem(404, 'RESOURCE_NOT_FOUND', 'Resource not found')
      const b = await body<Partial<CourierInput>>(request)
      const open = parcels.filter((p) => p.courierId === c.id && isOpen(p))
      if (b.active === false && c.active && open.length)
        return problem(
          409,
          'CONFLICT',
          `Reassign ${open.map((p) => p.trackingNumber).join(', ')} first`,
        )
      const rest: Partial<CourierInput> = { ...b }
      delete rest.userId // the linked identity user is immutable
      Object.assign(c, rest)
      return HttpResponse.json(c)
    }),
  ),

  http.get(
    `${base}/api/v1/dashboard/summary`,
    guard([...STAFF], () => {
      const day = (p: DemoParcel) => Date.now() - new Date(p.createdAt).getTime() < 86_400_000
      const s: DashboardSummary = {
        parcels: {
          today: parcels.filter(day).length,
          outForDelivery: parcels.filter((p) => p.status === 'OUT_FOR_DELIVERY').length,
          deliveredToday: parcels.filter((p) => p.status === 'DELIVERED').length,
          failedToday: parcels.filter((p) => p.status === 'FAILED').length,
        },
        dispatch: { unassigned: parcels.filter(waiting).length },
        merchants: { pending: merchants.filter((m) => m.status === 'PENDING').length },
      }
      return HttpResponse.json(s)
    }),
  ),
]
