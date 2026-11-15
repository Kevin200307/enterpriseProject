import { createContext, useContext } from 'react'
import type { Claims, Role } from './types'

export interface AuthState {
  /** `loading` until the boot-time refresh settles, so guards never flash the wrong page. */
  status: 'loading' | 'authenticated' | 'anonymous'
  claims: Claims | null
  expired: boolean
  signInWithToken: (token: string) => void
  logout: () => Promise<void>
  hasAnyRole: (roles: Role[]) => boolean
}

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
