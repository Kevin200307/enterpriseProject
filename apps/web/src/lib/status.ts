import {
  Ban,
  Bike,
  CircleAlert,
  CircleCheck,
  PackageCheck,
  PackagePlus,
  Truck,
  type LucideIcon,
} from 'lucide-react'

/** Mirrors contracts.domain.ParcelStatus, plus CANCELLED (see FRONTEND_PLAN C6). */
export type ParcelStatus =
  'CREATED' | 'PICKED_UP' | 'IN_TRANSIT' | 'OUT_FOR_DELIVERY' | 'DELIVERED' | 'FAILED' | 'CANCELLED'

interface StatusMeta {
  label: string
  icon: LucideIcon
  className: string
}

export const STATUS_META: Record<ParcelStatus, StatusMeta> = {
  CREATED: {
    label: 'Created',
    icon: PackagePlus,
    className: 'bg-slate-100 text-slate-800',
  },
  PICKED_UP: {
    label: 'Picked up',
    icon: PackageCheck,
    className: 'bg-blue-100 text-blue-900',
  },
  IN_TRANSIT: {
    label: 'In transit',
    icon: Truck,
    className: 'bg-indigo-100 text-indigo-900',
  },
  OUT_FOR_DELIVERY: {
    label: 'Out for delivery',
    icon: Bike,
    className: 'bg-amber-100 text-amber-900',
  },
  DELIVERED: {
    label: 'Delivered',
    icon: CircleCheck,
    className: 'bg-green-100 text-green-900',
  },
  FAILED: {
    label: 'Failed',
    icon: CircleAlert,
    className: 'bg-red-100 text-red-900',
  },
  CANCELLED: {
    label: 'Cancelled',
    icon: Ban,
    className: 'bg-zinc-100 text-zinc-600 line-through',
  },
}

/** Unknown statuses from a newer backend fall back to a neutral chip instead of crashing. */
export const statusMeta = (s: string): StatusMeta =>
  STATUS_META[s as ParcelStatus] ?? {
    label: s,
    icon: PackagePlus,
    className: 'bg-muted text-muted-foreground',
  }
