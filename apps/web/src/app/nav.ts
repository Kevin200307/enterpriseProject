import {
  BarChart3,
  Bell,
  Bike,
  Boxes,
  Gauge,
  History,
  Home,
  ClipboardList,
  KeyRound,
  LayoutDashboard,
  Mail,
  Map as MapIcon,
  MapPin,
  PackagePlus,
  Search,
  Store,
  Tag,
  User,
  Users,
  Webhook,
  type LucideIcon,
} from 'lucide-react'
import type { Role } from '@/auth/session'

export interface NavItem {
  to: string
  label: string
  icon: LucideIcon
  roles: Role[]
  /** Compact label for the bottom bar, where the full one would not fit. */
  short?: string
  /**
   * Reachable from the app chrome on mobile (the header bell, the profile page), so it does not
   * need a slot of its own and never pushes a persona's real tabs into the overflow sheet.
   */
  secondary?: boolean
}

export const NAV_ITEMS: NavItem[] = [
  { to: '/home', label: 'Home', icon: Home, roles: ['CUSTOMER'] },
  {
    to: '/deliveries/new',
    label: 'New delivery',
    short: 'New',
    icon: PackagePlus,
    roles: ['CUSTOMER'],
  },
  {
    to: '/merchant/register',
    label: 'Become a merchant',
    short: 'Merchant',
    icon: Store,
    roles: ['CUSTOMER'],
  },
  {
    to: '/deliveries',
    label: 'My deliveries',
    short: 'Deliveries',
    icon: Boxes,
    roles: ['CUSTOMER'],
  },
  { to: '/merchant/orders', label: 'Orders', icon: ClipboardList, roles: ['MERCHANT'] },
  {
    to: '/merchant/orders/new',
    label: 'New order',
    short: 'New',
    icon: PackagePlus,
    roles: ['MERCHANT'],
  },
  { to: '/merchant/api-keys', label: 'API keys', icon: KeyRound, roles: ['MERCHANT'] },
  { to: '/merchant/webhook', label: 'Webhook', icon: Webhook, roles: ['MERCHANT'] },
  {
    to: '/merchant/webhook/deliveries',
    label: 'Deliveries log',
    icon: History,
    roles: ['MERCHANT'],
  },
  { to: '/courier/today', label: 'Today', icon: Bike, roles: ['COURIER'] },
  { to: '/admin', label: 'Dashboard', icon: LayoutDashboard, roles: ['ADMIN', 'DISPATCHER'] },
  { to: '/admin/dispatch', label: 'Dispatch', icon: MapPin, roles: ['ADMIN', 'DISPATCHER'] },
  { to: '/admin/parcels', label: 'Parcels', icon: Boxes, roles: ['ADMIN', 'DISPATCHER'] },
  { to: '/admin/couriers', label: 'Couriers', icon: Bike, roles: ['ADMIN', 'DISPATCHER'] },
  { to: '/admin/workload', label: 'Workload', icon: Gauge, roles: ['ADMIN', 'DISPATCHER'] },
  { to: '/admin/users', label: 'Users', icon: Users, roles: ['ADMIN'] },
  { to: '/admin/notifications', label: 'Notification log', icon: Mail, roles: ['ADMIN'] },
  { to: '/admin/reports', label: 'Reports', icon: BarChart3, roles: ['ADMIN'] },
  { to: '/admin/zones', label: 'Zones', icon: MapIcon, roles: ['ADMIN'] },
  { to: '/admin/pricing', label: 'Pricing', icon: Tag, roles: ['ADMIN'] },
  { to: '/admin/merchants', label: 'Merchants', icon: Store, roles: ['ADMIN'] },
  {
    to: '/account/notifications',
    label: 'Notifications',
    short: 'Alerts',
    icon: Bell,
    roles: ['CUSTOMER', 'MERCHANT', 'COURIER', 'DISPATCHER', 'ADMIN'],
    secondary: true,
  },
  {
    to: '/track',
    label: 'Track a parcel',
    short: 'Track',
    icon: Search,
    roles: [],
    secondary: true,
  },
  {
    to: '/account',
    label: 'Profile',
    icon: User,
    roles: ['CUSTOMER', 'MERCHANT', 'COURIER', 'DISPATCHER', 'ADMIN'],
  },
]

/** Union of items across all of the user's roles; role-less items are visible to everyone. */
export const navFor = (roles: Role[]) =>
  NAV_ITEMS.filter((i) => i.roles.length === 0 || i.roles.some((r) => roles.includes(r)))

/**
 * The destinations each persona gets as bottom-bar tabs on mobile, in order. Everything else
 * they can reach falls into the "More" sheet, so the bar never exceeds five targets.
 */
const MOBILE_TABS: Record<Role, string[]> = {
  CUSTOMER: ['/home', '/deliveries', '/deliveries/new', '/merchant/register', '/account'],
  COURIER: ['/courier/today', '/track', '/account/notifications', '/account'],
  MERCHANT: ['/merchant/orders', '/merchant/orders/new', '/merchant/webhook', '/account'],
  DISPATCHER: ['/admin/dispatch', '/admin/parcels', '/admin/couriers', '/account'],
  ADMIN: ['/admin', '/admin/dispatch', '/admin/parcels', '/account'],
}

/** Same precedence as roleLanding: the highest-privilege persona decides the tab set. */
const primaryRole = (roles: Role[]): Role | undefined =>
  (['ADMIN', 'DISPATCHER', 'COURIER', 'MERCHANT', 'CUSTOMER'] as const).find((r) =>
    roles.includes(r),
  )

/**
 * Splits the nav into the bottom bar and the overflow sheet. The bar holds at most five targets,
 * so when anything overflows only four tabs show and the fifth slot becomes "More".
 */
export function mobileNavFor(roles: Role[]): { tabs: NavItem[]; overflow: NavItem[] } {
  const visible = navFor(roles)
  const wanted = MOBILE_TABS[primaryRole(roles) ?? 'CUSTOMER'] ?? []
  const byPath = new Map(visible.map((i) => [i.to, i]))

  const preferred = wanted.map((p) => byPath.get(p)).filter((i): i is NavItem => Boolean(i))
  const rest = visible.filter((i) => !preferred.includes(i))

  // Secondary items sit in the chrome already, so they alone never justify a sheet — but once
  // something else does, they are listed in it too rather than being hidden away.
  if (rest.every((i) => i.secondary)) return { tabs: preferred.slice(0, 5), overflow: [] }
  return { tabs: preferred.slice(0, 4), overflow: [...preferred.slice(4), ...rest] }
}
