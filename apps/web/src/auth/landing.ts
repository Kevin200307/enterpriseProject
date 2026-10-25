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
  return value
}
