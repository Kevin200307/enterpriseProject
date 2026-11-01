import { Link } from 'react-router-dom'
import { useAuth } from '@/auth/authContext'
import { roleLanding } from '@/auth/landing'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/common/EmptyState'

export const NotFoundPage = () => (
  <EmptyState
    title="Page not found"
    description="The page you're looking for doesn't exist."
    action={
      <Button asChild>
        <Link to="/">Go home</Link>
      </Button>
    }
  />
)

export function ForbiddenPage() {
  const { claims } = useAuth()
  const home = claims ? roleLanding(claims.roles) : '/'
  return (
    <EmptyState
      title="403 — Access denied"
      description="Your account doesn't have access to this area."
      action={
        <Button asChild>
          <Link to={home}>Go home</Link>
        </Button>
      }
    />
  )
}

/** Stand-in for screens delivered in later issues, so deep links resolve today. */
export const ComingSoon = ({ title, issue }: { title: string; issue: string }) => (
  <EmptyState title={title} description={`This screen is delivered in ${issue}.`} />
)
