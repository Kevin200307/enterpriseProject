import type { ParcelStatus } from '@/lib/status'

export type PackageSize = 'SMALL' | 'MEDIUM' | 'LARGE'

/** Spring Data `Page` shape (plan.md §4.2). */
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface Address {
  contactName: string
  phone: string
  line1: string
  line2?: string
  city: string
  postalCode: string
}

export interface Parcel {
  id: string
  trackingNumber: string
  status: ParcelStatus
  size: PackageSize
  weightKg: number
  express: boolean
  fee: number | string
  pickup: Address
  dropoff: Address
  createdAt: string
  courierName?: string
  proofUrl?: string
  failureReason?: string
}

export interface HistoryEntry {
  fromStatus: ParcelStatus | null
  toStatus: ParcelStatus
  actorName?: string
  note?: string
  failureReason?: string
  proofUrl?: string
  occurredAt: string
}

export interface CreateParcelInput {
  pickup: Address
  dropoff: Address
  size: PackageSize
  weightKg: number
  express: boolean
  /** Merchant's own order reference (B2B orders only). */
  merchantRef?: string
}

export interface EstimateInput {
  fromCity: string
  toCity: string
  size: PackageSize
  weightKg: number
  express: boolean
}

export interface Estimate {
  zoneFrom: string
  zoneTo: string
  baseFee: number | string
  weightSurcharge: number | string
  expressSurcharge: number | string
  total: number | string
  currency: string
  ruleId: string
  calculatedAt: string
}

export interface Zone {
  id: string
  name: string
  code: string
  cities: string[]
  active: boolean
}

/** Public tracking payload (TRK-3): cities and courier first name only — no street, phone or ids. */
export interface PublicTracking {
  trackingNumber: string
  status: ParcelStatus
  originCity: string
  destinationCity: string
  courierFirstName?: string
  eta?: string
  updatedAt: string
  timeline: { status: ParcelStatus; occurredAt: string; note?: string }[]
}

/** One parcel on a courier's manifest (DSP-3/4). Phone arrives masked from the server before pickup. */
export interface Assignment {
  id: string
  parcelId: string
  trackingNumber: string
  status: ParcelStatus
  pickup: Address
  dropoff: Address
  size: PackageSize
  weightKg: number
  assignedAt: string
}

export interface StatusChange {
  toStatus: ParcelStatus
  note?: string
  failureReason?: string
  proofUrl?: string
}

/** A parcel waiting for a courier (DSP-2). */
export interface QueueItem {
  parcelId: string
  trackingNumber: string
  pickupCity: string
  size: PackageSize
  weightKg: number
  createdAt: string
}

export interface Suggestion {
  courierId: string
  name: string
  reason: string
  remainingCapacity: number
}

export interface WorkloadRow {
  courierId: string
  name: string
  active: boolean
  activeAssignments: number
  deliveredToday: number
  failedToday: number
  remainingCapacity: number
}

export type UserStatus = 'ACTIVE' | 'SUSPENDED'

export interface AdminUser {
  id: string
  email: string
  fullName: string
  phone: string
  roles: string[]
  status: UserStatus
}

export type MerchantStatus = 'PENDING' | 'APPROVED' | 'SUSPENDED' | 'REJECTED'

export interface Merchant {
  id: string
  orgName: string
  businessRegNo: string
  contactEmail: string
  contactPhone: string
  status: MerchantStatus
  approvedBy?: string
  approvedAt?: string
}

/** BFF payload (GW-4). Each section is null when its upstream was down — the rest still renders. */
export interface DashboardSummary {
  parcels: {
    today: number
    outForDelivery: number
    deliveredToday: number
    failedToday: number
  } | null
  dispatch: { unassigned: number } | null
  merchants: { pending: number } | null
}

export interface ZoneInput {
  name: string
  code: string
  cities: string[]
  active: boolean
}

export interface PricingRule {
  id: string
  zoneFromId: string
  zoneToId: string
  sizeBand: PackageSize
  baseFee: number | string
  perKg: number | string
  minFee: number | string
  maxWeightKg: number | string
  active: boolean
  effectiveFrom: string
  effectiveTo?: string | null
}

export type PricingRuleInput = Omit<
  PricingRule,
  'id' | 'baseFee' | 'perKg' | 'minFee' | 'maxWeightKg'
> & {
  baseFee: number
  perKg: number
  minFee: number
  maxWeightKg: number
}

export interface DailyStat {
  date: string
  created: number
  completed: number
  failed: number
}

export type VehicleType = 'BIKE' | 'THREEWHEEL' | 'VAN'

export interface Courier {
  id: string
  userId: string
  name: string
  vehicleType: VehicleType
  homeZoneCode: string
  phone: string
  maxDailyLoad: number
  active: boolean
}

export interface CourierInput {
  userId?: string
  vehicleType: VehicleType
  homeZoneCode: string
  phone: string
  maxDailyLoad: number
  active?: boolean
}

export interface MerchantProfile {
  id: string
  orgName: string
  status: MerchantStatus
  webhookUrl?: string | null
}

export interface MerchantOrder {
  merchantRef: string
  trackingNumber: string
  status: ParcelStatus
  destinationCity: string
  createdAt: string
}

export interface MerchantApplication {
  orgName: string
  businessRegNo: string
  contactEmail: string
  contactPhone: string
}

export interface ApiKey {
  id: string
  prefix: string
  label: string
  scopes: string[]
  lastUsedAt?: string | null
  expiresAt?: string | null
}

/** Returned exactly once, at creation. `key` must never be cached or logged. */
export interface CreatedApiKey extends ApiKey {
  key: string
}

export type WebhookState = 'PENDING' | 'DELIVERED' | 'DEAD'

export interface WebhookDelivery {
  id: string
  eventId: string
  trackingNumber: string
  url: string
  attempts: number
  lastStatus?: number | null
  state: WebhookState
  nextRetryAt?: string | null
  history?: { at: string; status?: number | null; error?: string | null }[]
}

export interface ToolCall {
  name: string
  arguments?: Record<string, unknown>
}

export interface ConversationTurn {
  role: 'user' | 'assistant'
  content: string
}
