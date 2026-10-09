/** Placeholder session until AuthProvider lands in FE-2. Anonymous by default. */
export type Role = 'CUSTOMER' | 'MERCHANT' | 'COURIER' | 'DISPATCHER' | 'ADMIN'

export interface Session {
  roles: Role[]
  email?: string
}

export const useSession = (): Session => ({ roles: [] })
