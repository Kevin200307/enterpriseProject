import { useQueryClient } from '@tanstack/react-query'
import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from 'react'
import { setSessionExpiredHandler } from '@/api/client'
import { authApi } from '@/api/endpoints/auth'
import { AuthContext, type AuthState } from './authContext'
import { decodeClaims } from './jwt'
import { tokenStore } from './tokenStore'
import type { Role } from './types'

const REFRESH_LEAD_MS = 60_000

tokenStore.setRefresher(async () => (await authApi.refresh()).accessToken)

export function AuthProvider({ children }: { children: ReactNode }) {
  const qc = useQueryClient()
  const token = useSyncExternalStore(tokenStore.subscribe, tokenStore.get)
  const claims = useMemo(() => decodeClaims(token), [token])
  const [booted, setBooted] = useState(false)
  const [expired, setExpired] = useState(false)

  // Boot: the refresh cookie may hold a live session. Wait for the answer before rendering guards.
  useEffect(() => {
    let cancelled = false
    void tokenStore.refresh().finally(() => !cancelled && setBooted(true))
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    setSessionExpiredHandler(() => {
      tokenStore.clear()
      qc.clear()
      setExpired(true)
    })
  }, [qc])

  // Proactive refresh shortly before exp; the 401 handler stays as the safety net.
  useEffect(() => {
    if (!claims) return
    const delay = Math.max(claims.exp * 1000 - Date.now() - REFRESH_LEAD_MS, 5_000)
    const id = setTimeout(() => void tokenStore.refresh(), delay)
    return () => clearTimeout(id)
  }, [claims])

  const signInWithToken = useCallback((t: string) => {
    setExpired(false)
    tokenStore.set(t)
  }, [])

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      // the cookie may already be gone; local sign-out must still succeed
    }
    tokenStore.clear()
    qc.clear()
    setExpired(false)
  }, [qc])

  const value = useMemo<AuthState>(
    () => ({
      status: !booted ? 'loading' : claims ? 'authenticated' : 'anonymous',
      claims,
      expired,
      signInWithToken,
      logout,
      hasAnyRole: (roles: Role[]) => !!claims && roles.some((r) => claims.roles.includes(r)),
    }),
    [booted, claims, expired, signInWithToken, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
