import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { isApiError } from '@/api/ApiError'
import { merchantApi } from '@/api/endpoints/merchant'
import { qk } from '@/api/queryKeys'
import { useAuth } from '@/auth/authContext'
import { tokenStore } from '@/auth/tokenStore'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

const COPY = {
  PENDING: {
    title: 'Application under review',
    body: 'An admin is reviewing your application. This page checks for updates every minute.',
  },
  APPROVED: { title: 'You are approved', body: 'Your merchant account is active.' },
  SUSPENDED: {
    title: 'Account suspended',
    body: 'Your merchant account is suspended. Contact SwiftDrop support.',
  },
  REJECTED: {
    title: 'Application not approved',
    body: 'Your application was not approved. Contact SwiftDrop support if you think this is a mistake.',
  },
} as const

export function MerchantPendingPage() {
  const { hasAnyRole } = useAuth()
  const navigate = useNavigate()
  const [activating, setActivating] = useState(false)
  const q = useQuery({
    queryKey: qk.merchant.me,
    queryFn: merchantApi.me,
    refetchInterval: 60_000,
    retry: false,
  })

  if (q.isPending) return <Skeleton className="h-40 max-w-lg" />
  if (q.isError) {
    if (isApiError(q.error) && q.error.status === 404) {
      return (
        <EmptyState
          title="No merchant application yet"
          action={
            <Button asChild>
              <Link to="/merchant/register">Apply now</Link>
            </Button>
          }
        />
      )
    }
    return <ErrorState error={q.error} onRetry={() => void q.refetch()} />
  }

  const m = q.data
  const copy = COPY[m.status]
  // The MERCHANT role arrives in the next access token, so approval needs a token refresh before the console opens.
  async function activate() {
    setActivating(true)
    await tokenStore.refresh()
    setActivating(false)
    navigate('/merchant/orders')
  }
  return (
    <div className="mx-auto flex max-w-lg flex-col gap-6">
      <PageHeader title={m.orgName} />
      <Card>
        <CardContent className="flex flex-col gap-3 pt-6" role="status">
          <h2 className="text-lg font-semibold">{copy.title}</h2>
          <p className="text-sm text-muted-foreground">{copy.body}</p>
          {m.status === 'APPROVED' &&
            (hasAnyRole(['MERCHANT']) ? (
              <Button asChild>
                <Link to="/merchant/orders">Open merchant console</Link>
              </Button>
            ) : (
              <Button disabled={activating} onClick={() => void activate()}>
                {activating ? 'Activating…' : 'Activate merchant features'}
              </Button>
            ))}
        </CardContent>
      </Card>
    </div>
  )
}
