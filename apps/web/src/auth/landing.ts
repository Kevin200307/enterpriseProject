import type { Role } from './types'

/** Highest-privilege persona wins when a user holds several roles. */
export function roleLanding(roles: Role[]): string {
  if (roles.includes('ADMIN')) return '/admin'
  if (roles.includes('DISPATCHER')) return '/admin'
  if (roles.includes('COURIER')) return '/courier/today'
  if (roles.includes('MERCHANT')) return '/merchant/orders'
  return '/home'
}

/** Open-redirect guard: only same-origin relative paths are honoured. */
export function safeReturnTo(value: string | null | undefined, fallback: string): string {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.includes('\\'))
    return fallback
  // Never bounce the user back to the error pages — go to their home instead.
  if (value === '/403' || value === '/404') return fallback
  return value
}

/** Do not send a newly signed-in user back to a route their role cannot open. */
export function safeRoleReturnTo(
  value: string | null | undefined,
  fallback: string,
  roles: Role[],
) {
  const destination = safeReturnTo(value, fallback)
  if (destination === fallback) return fallback

  const pathname = destination.split(/[?#]/, 1)[0] ?? destination
  let requiredRoles: Role[] | undefined

  if (pathname === '/home' || pathname === '/deliveries' || pathname.startsWith('/deliveries/')) {
    requiredRoles = ['CUSTOMER']
  } else if (pathname === '/courier' || pathname.startsWith('/courier/')) {
    requiredRoles = ['COURIER']
  } else if (
    pathname === '/admin/users' ||
    pathname === '/admin/notifications' ||
    pathname === '/admin/reports' ||
    pathname === '/admin/zones' ||
    pathname === '/admin/pricing' ||
    pathname === '/admin/merchants'
  ) {
    requiredRoles = ['ADMIN']
  } else if (pathname === '/admin' || pathname.startsWith('/admin/')) {
    requiredRoles = ['ADMIN', 'DISPATCHER']
  } else if (pathname.startsWith('/merchant/')) {
    const publicMerchantPath = ['/merchant/register', '/merchant/pending'].includes(pathname)
    if (!publicMerchantPath) requiredRoles = ['MERCHANT']
  }

  return requiredRoles && !requiredRoles.some((role) => roles.includes(role))
    ? fallback
    : destination
}
