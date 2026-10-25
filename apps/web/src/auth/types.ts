export type Role = 'CUSTOMER' | 'MERCHANT' | 'COURIER' | 'DISPATCHER' | 'ADMIN'

export const ALL_ROLES: Role[] = ['CUSTOMER', 'MERCHANT', 'COURIER', 'DISPATCHER', 'ADMIN']

/** Claims from the access token (see plan.md §4.3). Used for display and routing only — the server authorizes. */
export interface Claims {
  sub: string
  email: string
  roles: Role[]
  merchantId?: string
  exp: number
}
