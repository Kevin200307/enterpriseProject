import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { LoadingScreen } from '@/components/common/LoadingScreen'
import { useAuth } from './authContext'
import type { Role } from './types'

export function RequireAuth({ roles }: { roles?: Role[] }) {
  const { status, hasAnyRole, expired } = useAuth()
  const location = useLocation()

  if (status === 'loading') return <LoadingScreen />
  if (status === 'anonymous') {
    const returnTo = encodeURIComponent(location.pathname + location.search)
    return (
      <Navigate to={`/login?returnTo=${returnTo}${expired ? '&reason=expired' : ''}`} replace />
    )
  }
  if (roles && !hasAnyRole(roles)) return <Navigate to="/403" replace />
  return <Outlet />
}
