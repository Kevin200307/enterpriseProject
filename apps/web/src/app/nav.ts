import {
  Bike,
  Boxes,
  Gauge,
  ClipboardList,
  KeyRound,
  LayoutDashboard,
  Map,
  MapPin,
  PackagePlus,
  Search,
  Store,
  Tag,
  Users,
  type LucideIcon,
} from 'lucide-react'
import type { Role } from '@/auth/session'

export interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  roles: Role[]
}

export const NAV_ITEMS: NavItem[] = [
  { to: '/deliveries/new', label: 'New delivery', icon: PackagePlus, roles: ['CUSTOMER'] },
  { to: '/deliveries', label: 'My deliveries', icon: Boxes, roles: ['CUSTOMER'] },
  { to: '/merchant/orders', label: 'Orders', icon: ClipboardList, roles: ['MERCHANT'] },
  { to: '/merchant/api-keys', label: 'API keys', icon: KeyRound, roles: ['MERCHANT'] },
  { to: '/courier/today', label: 'Today', icon: Bike, roles: ['COURIER'] },
  { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, roles: ['ADMIN', 'DISPATCHER'] },
  { to: '/admin/dispatch', label: 'Dispatch', icon: MapPin, roles: ['ADMIN', 'DISPATCHER'] },
  { to: '/admin/workload', label: 'Workload', icon: Gauge, roles: ['ADMIN', 'DISPATCHER'] },
  { to: '/admin/users', label: 'Users', icon: Users, roles: ['ADMIN'] },
  { to: '/admin/zones', label: 'Zones', icon: Map, roles: ['ADMIN'] },
  { to: '/admin/pricing', label: 'Pricing', icon: Tag, roles: ['ADMIN'] },
  { to: '/admin/merchants', label: 'Merchants', icon: Store, roles: ['ADMIN'] },
  { to: '/track', label: 'Track a parcel', icon: Search, roles: [] },
]

/** Union of items across all of the user's roles; role-less items are visible to everyone. */
export const navFor = (roles: Role[]) =>
  NAV_ITEMS.filter((i) => i.roles.length === 0 || i.roles.some((r) => roles.includes(r)))
