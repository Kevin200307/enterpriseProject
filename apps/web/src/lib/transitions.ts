import type { ParcelStatus } from './status'

/** Mirrors parcel-service's legal transition map (PAR-3). The server stays the authority. */
const NEXT: Record<ParcelStatus, ParcelStatus[]> = {
  CREATED: ['PICKED_UP', 'CANCELLED'],
  PICKED_UP: ['IN_TRANSIT'],
  IN_TRANSIT: ['OUT_FOR_DELIVERY'],
  OUT_FOR_DELIVERY: ['DELIVERED', 'FAILED'],
  FAILED: ['OUT_FOR_DELIVERY'],
  DELIVERED: [],
  CANCELLED: [],
}

export const allowedNext = (s: ParcelStatus): ParcelStatus[] => NEXT[s] ?? []

export interface CourierAction {
  to: ParcelStatus
  label: string
}

/** The single big button a courier sees for each state, plus an optional secondary action. */
export const COURIER_ACTIONS: Partial<
  Record<ParcelStatus, { primary: CourierAction; secondary?: CourierAction }>
> = {
  CREATED: { primary: { to: 'PICKED_UP', label: 'Picked up' } },
  PICKED_UP: { primary: { to: 'IN_TRANSIT', label: 'In transit' } },
  IN_TRANSIT: { primary: { to: 'OUT_FOR_DELIVERY', label: 'Out for delivery' } },
  OUT_FOR_DELIVERY: {
    primary: { to: 'DELIVERED', label: 'Delivered' },
    secondary: { to: 'FAILED', label: 'Failed' },
  },
  FAILED: { primary: { to: 'OUT_FOR_DELIVERY', label: 'Re-attempt' } },
}

export const FAILURE_REASONS = [
  'Recipient unavailable',
  'Wrong address',
  'Recipient refused',
  'Unsafe location',
] as const
