import type {
  Address,
  Estimate,
  EstimateInput,
  HistoryEntry,
  Parcel,
  PricingRule,
  Zone,
  AdminNotification,
  Merchant,
  MyNotification,
  PackageSize,
} from '@/api/types'
import type { Role } from '@/auth/types'
import type { ParcelStatus } from '@/lib/status'
import { DEMO_PASSWORD } from './demoAccounts'
import { ago } from './util'

// ---------- users ----------
export interface DemoUser {
  id: string
  email: string
  fullName: string
  phone: string
  password: string
  roles: Role[]
  status: 'ACTIVE' | 'SUSPENDED'
  merchantId?: string
}
const u = (
  id: string,
  email: string,
  fullName: string,
  phone: string,
  roles: Role[],
  merchantId?: string,
): DemoUser => ({
  id,
  email,
  fullName,
  phone,
  password: DEMO_PASSWORD,
  roles,
  status: 'ACTIVE',
  merchantId,
})

export const users: DemoUser[] = [
  u('u-customer', 'customer@swiftdrop.demo', 'Nimal Perera', '+94771234567', ['CUSTOMER']),
  u(
    'u-merchant',
    'merchant@swiftdrop.demo',
    'Shanika Fernando',
    '+94112345678',
    ['CUSTOMER', 'MERCHANT'],
    'm-1',
  ),
  u('u-courier', 'courier@swiftdrop.demo', 'Kasun Silva', '+94772223344', ['COURIER']),
  u('u-dispatcher', 'dispatcher@swiftdrop.demo', 'Dilani Jayasuriya', '+94773334455', [
    'DISPATCHER',
  ]),
  u('u-admin', 'admin@swiftdrop.demo', 'Amal Wickramasinghe', '+94774445566', ['ADMIN']),
  u('u-other', 'other.customer@swiftdrop.demo', 'Ruwan Bandara', '+94775556677', ['CUSTOMER']),
  u('u-courier2', 'nuwan@swiftdrop.demo', 'Nuwan Gunasekara', '+94776667788', ['COURIER']),
  u('u-courier3', 'dilini@swiftdrop.demo', 'Dilini Rathnayake', '+94777778899', ['COURIER']),
  u('u-courier4', 'ruvini@swiftdrop.demo', 'Ruvini Amarasinghe', '+94778889900', ['COURIER']),
  u('u-teas', 'teas@swiftdrop.demo', 'Ceylon Teas Owner', '+94112200110', ['CUSTOMER']),
]

// ---------- zones, rules, pricing ----------
export const zones: Zone[] = [
  {
    id: 'z-cmb',
    name: 'Colombo',
    code: 'CMB',
    cities: ['Colombo', 'Dehiwala', 'Moratuwa', 'Kotte'],
    active: true,
  },
  {
    id: 'z-gmp',
    name: 'Gampaha',
    code: 'GMP',
    cities: ['Gampaha', 'Negombo', 'Ja-Ela'],
    active: true,
  },
  {
    id: 'z-kdy',
    name: 'Kandy',
    code: 'KDY',
    cities: ['Kandy', 'Peradeniya', 'Katugastota'],
    active: true,
  },
  { id: 'z-gle', name: 'Galle', code: 'GLE', cities: ['Galle', 'Hikkaduwa'], active: true },
  { id: 'z-jfn', name: 'Jaffna', code: 'JFN', cities: ['Jaffna', 'Nallur'], active: true },
]

const SIZES: PackageSize[] = ['SMALL', 'MEDIUM', 'LARGE']
const mult: Record<PackageSize, number> = { SMALL: 1, MEDIUM: 1.4, LARGE: 2 }
export const rules: PricingRule[] = zones.flatMap((a, i) =>
  zones.flatMap((b, j) =>
    SIZES.map((size) => ({
      id: `r-${a.code}-${b.code}-${size}`,
      zoneFromId: a.id,
      zoneToId: b.id,
      sizeBand: size,
      baseFee: Math.round((i === j ? 300 : 450 + 80 * Math.abs(i - j)) * mult[size]),
      perKg: { SMALL: 60, MEDIUM: 80, LARGE: 110 }[size],
      minFee: 250,
      maxWeightKg: { SMALL: 5, MEDIUM: 15, LARGE: 40 }[size],
      active: true,
      effectiveFrom: '2026-01-01',
      effectiveTo: null,
    })),
  ),
)

export const zoneOf = (city: string) =>
  zones.find((z) => z.active && z.cities.some((c) => c.toLowerCase() === city.trim().toLowerCase()))

export type EstimateResult =
  Estimate | { error: 'city'; city: string } | { error: 'weight'; max: number }

export function estimate(i: EstimateInput): EstimateResult {
  const from = zoneOf(i.fromCity)
  const to = zoneOf(i.toCity)
  if (!from) return { error: 'city', city: i.fromCity }
  if (!to) return { error: 'city', city: i.toCity }
  const rule = rules.find(
    (r) => r.active && r.zoneFromId === from.id && r.zoneToId === to.id && r.sizeBand === i.size,
  )
  if (!rule) return { error: 'city', city: i.toCity }
  if (i.weightKg <= 0 || i.weightKg > Number(rule.maxWeightKg))
    return { error: 'weight', max: Number(rule.maxWeightKg) }
  const base = Number(rule.baseFee)
  const weight = Math.round(Math.max(0, i.weightKg - 2) * Number(rule.perKg))
  const express = i.express ? Math.round((base + weight) * 0.25) : 0
  return {
    zoneFrom: from.name,
    zoneTo: to.name,
    baseFee: base,
    weightSurcharge: weight,
    expressSurcharge: express,
    total: Math.max(base + weight + express, Number(rule.minFee)),
    currency: 'LKR',
    ruleId: rule.id,
    calculatedAt: new Date().toISOString(),
  }
}

// ---------- couriers ----------
export interface DemoCourier {
  id: string
  userId: string
  name: string
  vehicleType: 'BIKE' | 'THREEWHEEL' | 'VAN'
  homeZoneCode: string
  phone: string
  maxDailyLoad: number
  active: boolean
}
export const couriers: DemoCourier[] = [
  {
    id: 'c-1',
    userId: 'u-courier',
    name: 'Kasun Silva',
    vehicleType: 'BIKE',
    homeZoneCode: 'CMB',
    phone: '+94772223344',
    maxDailyLoad: 12,
    active: true,
  },
  {
    id: 'c-2',
    userId: 'u-courier2',
    name: 'Nuwan Gunasekara',
    vehicleType: 'THREEWHEEL',
    homeZoneCode: 'CMB',
    phone: '+94776667788',
    maxDailyLoad: 15,
    active: true,
  },
  {
    id: 'c-3',
    userId: 'u-courier3',
    name: 'Dilini Rathnayake',
    vehicleType: 'VAN',
    homeZoneCode: 'KDY',
    phone: '+94777778899',
    maxDailyLoad: 20,
    active: false,
  },
  {
    id: 'c-4',
    userId: 'u-courier4',
    name: 'Ruvini Amarasinghe',
    vehicleType: 'BIKE',
    homeZoneCode: 'GMP',
    phone: '+94778889900',
    maxDailyLoad: 2,
    active: true,
  },
]

// ---------- parcels ----------
export interface DemoParcel extends Parcel {
  customerId: string
  merchantId?: string
  merchantRef?: string
  courierId?: string
}
export const history: Record<string, HistoryEntry[]> = {}
export const parcels: DemoParcel[] = []

const addr = (
  contactName: string,
  city: string,
  line1: string,
  phone = '+94771234567',
): Address => ({ contactName, phone, line1, city, postalCode: '10100' })
const CLOSED: ParcelStatus[] = ['DELIVERED', 'FAILED', 'CANCELLED']
export const isOpen = (p: DemoParcel) => !CLOSED.includes(p.status)

const FLOW: ParcelStatus[] = ['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED']
export const TRACKING_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
export const newTrackingNumber = () =>
  'SD-' +
  Array.from(
    { length: 8 },
    () => TRACKING_ALPHABET[Math.floor(Math.random() * TRACKING_ALPHABET.length)],
  ).join('')

export function pushHistory(
  p: DemoParcel,
  to: ParcelStatus,
  actor?: string,
  extra: Partial<HistoryEntry> = {},
  hoursAgo = 0,
) {
  const list = (history[p.id] ??= [])
  list.push({
    fromStatus: list[list.length - 1]?.toStatus ?? null,
    toStatus: to,
    actorName: actor,
    occurredAt: ago(hoursAgo),
    ...extra,
  })
}

function seed(spec: {
  tn: string
  status: ParcelStatus
  customer: string
  from: [string, string]
  to: [string, string]
  size?: PackageSize
  kg?: number
  express?: boolean
  hours: number
  courier?: string
  merchant?: string
  ref?: string
  reason?: string
}) {
  const id = `p-${parcels.length + 1}`
  const est = estimate({
    fromCity: spec.from[0],
    toCity: spec.to[0],
    size: spec.size ?? 'SMALL',
    weightKg: spec.kg ?? 1.5,
    express: !!spec.express,
  })
  const p: DemoParcel = {
    id,
    trackingNumber: spec.tn,
    status: spec.status,
    size: spec.size ?? 'SMALL',
    weightKg: spec.kg ?? 1.5,
    express: !!spec.express,
    fee: 'total' in est ? Number(est.total) : 600,
    pickup: addr(spec.from[1], spec.from[0], '12 Galle Road'),
    dropoff: addr(spec.to[1], spec.to[0], '45 Temple Street', '+94712223344'),
    createdAt: ago(spec.hours),
    customerId: spec.customer,
    merchantId: spec.merchant,
    merchantRef: spec.ref,
    courierId: spec.courier,
    courierName: couriers.find((c) => c.id === spec.courier)?.name,
    failureReason: spec.reason,
  }
  parcels.push(p)
  const steps =
    spec.status === 'CANCELLED'
      ? ['CREATED', 'CANCELLED']
      : spec.status === 'FAILED'
        ? ['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'FAILED']
        : FLOW.slice(0, FLOW.indexOf(spec.status) + 1)
  steps.forEach((s, i) => {
    const hoursAgo = spec.hours - ((i * spec.hours) / Math.max(steps.length, 1)) * 0.8
    pushHistory(
      p,
      s as ParcelStatus,
      i === 0 ? undefined : couriers.find((c) => c.id === spec.courier)?.name,
      s === 'FAILED' ? { failureReason: spec.reason } : {},
      Math.max(hoursAgo, 0.2),
    )
  })
  if (spec.status === 'DELIVERED') p.proofUrl = proofImage(spec.tn)
  return p
}

export const proofImage = (label: string) =>
  'data:image/svg+xml;utf8,' +
  encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="480" height="320"><rect width="480" height="320" fill="#fdf2e9"/><text x="240" y="150" text-anchor="middle" font-family="sans-serif" font-size="22" fill="#BD580F">Proof of delivery</text><text x="240" y="185" text-anchor="middle" font-family="monospace" font-size="18" fill="#444">${label}</text></svg>`,
  )

const C = 'u-customer'
seed({
  tn: 'SD-K7M2QX4A',
  status: 'CREATED',
  customer: C,
  from: ['Colombo', 'Nimal Perera'],
  to: ['Kandy', 'Sanduni Weerasinghe'],
  hours: 6,
})
seed({
  tn: 'SD-R9T3WB6C',
  status: 'PICKED_UP',
  customer: C,
  from: ['Colombo', 'Nimal Perera'],
  to: ['Galle', 'Chamara Dias'],
  courier: 'c-1',
  hours: 9,
  express: true,
})
seed({
  tn: 'SD-H4N8ZD2F',
  status: 'IN_TRANSIT',
  customer: C,
  from: ['Dehiwala', 'Nimal Perera'],
  to: ['Negombo', 'Kumari Ratnayake'],
  courier: 'c-1',
  hours: 14,
  size: 'MEDIUM',
  kg: 4,
})
seed({
  tn: 'SD-P5V7JG3L',
  status: 'OUT_FOR_DELIVERY',
  customer: C,
  from: ['Colombo', 'Nimal Perera'],
  to: ['Moratuwa', 'Isuru Lakmal'],
  courier: 'c-1',
  hours: 20,
})
seed({
  tn: 'SD-C2X9MK8R',
  status: 'DELIVERED',
  customer: C,
  from: ['Colombo', 'Nimal Perera'],
  to: ['Kotte', 'Hasini Perera'],
  courier: 'c-1',
  hours: 30,
})
seed({
  tn: 'SD-W6B4TN7Q',
  status: 'FAILED',
  customer: C,
  from: ['Gampaha', 'Nimal Perera'],
  to: ['Colombo', 'Tharindu Fonseka'],
  courier: 'c-1',
  hours: 27,
  reason: 'Recipient unavailable',
})
seed({
  tn: 'SD-F3L8HD5Y',
  status: 'CANCELLED',
  customer: C,
  from: ['Kandy', 'Nimal Perera'],
  to: ['Colombo', 'Anusha Silva'],
  hours: 48,
})
seed({
  tn: 'SD-Z7G2PA9S',
  status: 'CREATED',
  customer: 'u-other',
  from: ['Colombo', 'Ruwan Bandara'],
  to: ['Jaffna', 'Thevan Kumar'],
  hours: 30,
  size: 'LARGE',
  kg: 12,
})
seed({
  tn: 'SD-D8R5VC4K',
  status: 'CREATED',
  customer: 'u-other',
  from: ['Negombo', 'Ruwan Bandara'],
  to: ['Colombo', 'Mala Perera'],
  hours: 2,
})
seed({
  tn: 'SD-N4Q6XM2T',
  status: 'CREATED',
  customer: 'u-other',
  from: ['Kandy', 'Ruwan Bandara'],
  to: ['Peradeniya', 'Saman Kumara'],
  hours: 5,
})
seed({
  tn: 'SD-T9J3BW7E',
  status: 'IN_TRANSIT',
  customer: 'u-merchant',
  merchant: 'm-1',
  ref: 'ORD-1001',
  courier: 'c-2',
  from: ['Colombo', 'Lanka Crafts'],
  to: ['Kandy', 'Priya Nair'],
  hours: 12,
})
seed({
  tn: 'SD-M5Y8KH6P',
  status: 'DELIVERED',
  customer: 'u-merchant',
  merchant: 'm-1',
  ref: 'ORD-1002',
  courier: 'c-2',
  from: ['Colombo', 'Lanka Crafts'],
  to: ['Galle', 'Ashen Peiris'],
  hours: 40,
})
seed({
  tn: 'SD-B3C7ZW5H',
  status: 'CREATED',
  customer: 'u-merchant',
  merchant: 'm-1',
  ref: 'ORD-1003',
  from: ['Colombo', 'Lanka Crafts'],
  to: ['Negombo', 'Dulani Mendis'],
  hours: 3,
})

// ---------- merchants ----------
export interface DemoMerchant extends Merchant {
  ownerUserId: string
  webhookUrl?: string | null
}
export const merchants: DemoMerchant[] = [
  {
    id: 'm-1',
    ownerUserId: 'u-merchant',
    orgName: 'Lanka Crafts',
    businessRegNo: 'PV12345',
    contactEmail: 'shop@lankacrafts.lk',
    contactPhone: '+94112345678',
    status: 'APPROVED',
    approvedBy: 'Amal Wickramasinghe',
    approvedAt: ago(500),
    webhookUrl: 'https://lankacrafts.example/hooks/swiftdrop',
  },
  {
    id: 'm-2',
    ownerUserId: 'u-teas',
    orgName: 'Ceylon Teas',
    businessRegNo: 'PV22871',
    contactEmail: 'hello@ceylonteas.lk',
    contactPhone: '+94112200110',
    status: 'PENDING',
  },
  {
    id: 'm-3',
    ownerUserId: 'u-x3',
    orgName: 'Island Gadgets',
    businessRegNo: 'PV55302',
    contactEmail: 'sales@islandgadgets.lk',
    contactPhone: '+94112211220',
    status: 'PENDING',
  },
  {
    id: 'm-4',
    ownerUserId: 'u-x4',
    orgName: 'Galle Spices',
    businessRegNo: 'PV40019',
    contactEmail: 'orders@gallespices.lk',
    contactPhone: '+94912233445',
    status: 'SUSPENDED',
    approvedBy: 'Amal Wickramasinghe',
    approvedAt: ago(900),
  },
  {
    id: 'm-5',
    ownerUserId: 'u-x5',
    orgName: 'Quick Mart',
    businessRegNo: 'PV10001',
    contactEmail: 'info@quickmart.lk',
    contactPhone: '+94112299001',
    status: 'REJECTED',
    approvedBy: 'Amal Wickramasinghe',
    approvedAt: ago(700),
  },
]

// ---------- notifications ----------
const T = ['booking-confirmed', 'picked-up', 'out-for-delivery', 'delivered', 'delivery-failed']
export const myNotifications: MyNotification[] = T.map((k, i) => ({
  id: `n-${i + 1}`,
  channel: i === 2 || i === 3 ? 'SMS' : 'EMAIL',
  templateKey: k,
  status: i === 4 ? 'FAILED' : 'SENT',
  trackingNumber: 'SD-C2X9MK8R',
  createdAt: ago(40 - i * 6),
  sentAt: ago(40 - i * 6),
}))
export const notificationLog: AdminNotification[] = Array.from({ length: 12 }, (_, i) => ({
  id: `a-${i + 1}`,
  eventId: `e-${i + 1}`,
  channel: i % 3 === 0 ? 'SMS' : 'EMAIL',
  recipientRef: users[i % users.length]!.id.padEnd(8, '0') + '-0000-4000-8000-000000000000',
  templateKey: T[i % T.length]!,
  status: (['SENT', 'SENT', 'FAILED', 'SKIPPED', 'SENT', 'PENDING'] as const)[i % 6]!,
  attempts: i % 6 === 2 ? 5 : 1,
  lastError: i % 6 === 2 ? 'provider timeout' : null,
  createdAt: ago(i * 3 + 1),
  sentAt: i % 6 === 2 ? null : ago(i * 3 + 1),
}))
export const prefs = new Map<string, { EMAIL: string[]; SMS: string[] }>()
export const defaultPrefs = () => ({
  EMAIL: ['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'FAILED'],
  SMS: ['OUT_FOR_DELIVERY', 'DELIVERED'],
})

// ---------- merchant keys & webhook deliveries ----------
export const apiKeys = [
  {
    id: 'k-1',
    prefix: 'sk_live_9f8e7d6c',
    label: 'Production server',
    scopes: ['orders:write', 'orders:read'],
    lastUsedAt: ago(5) as string | null,
    expiresAt: null as string | null,
  },
]
export const deliveries = [
  {
    id: 'd-1',
    eventId: 'e-1',
    trackingNumber: 'SD-T9J3BW7E',
    url: 'https://lankacrafts.example/hooks/swiftdrop',
    attempts: 1,
    lastStatus: 200 as number | null,
    state: 'DELIVERED' as const,
    nextRetryAt: null as string | null,
    history: [{ at: ago(11), status: 200 as number | null, error: null as string | null }],
  },
  {
    id: 'd-2',
    eventId: 'e-2',
    trackingNumber: 'SD-M5Y8KH6P',
    url: 'https://lankacrafts.example/hooks/swiftdrop',
    attempts: 3,
    lastStatus: 500 as number | null,
    state: 'PENDING' as 'PENDING' | 'DELIVERED' | 'DEAD',
    nextRetryAt: new Date(Date.now() + 600_000).toISOString() as string | null,
    history: [
      { at: ago(1), status: 500 as number | null, error: null as string | null },
      { at: ago(0.9), status: null, error: 'timeout' },
      { at: ago(0.5), status: 500 as number | null, error: null as string | null },
    ],
  },
  {
    id: 'd-3',
    eventId: 'e-3',
    trackingNumber: 'SD-M5Y8KH6P',
    url: 'https://old.example/hook',
    attempts: 6,
    lastStatus: 400 as number | null,
    state: 'DEAD' as 'PENDING' | 'DELIVERED' | 'DEAD',
    nextRetryAt: null as string | null,
    history: [{ at: ago(30), status: 400 as number | null, error: null as string | null }],
  },
]
