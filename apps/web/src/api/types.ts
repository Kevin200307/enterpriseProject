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
