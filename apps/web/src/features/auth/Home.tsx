import { Navigate } from 'react-router-dom'
import { useAuth } from '@/auth/authContext'
import { roleLanding } from '@/auth/landing'
export function Home() {
  const { status, claims } = useAuth()
  if (status === 'loading') return null
  return <Navigate to={claims ? roleLanding(claims.roles) : '/login'} replace />
}
