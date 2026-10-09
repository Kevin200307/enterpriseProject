import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { adminApi } from '@/api/endpoints/admin'
import { qk } from '@/api/queryKeys'
import { ErrorState } from '@/components/common/ErrorState'
import { PageHeader } from '@/components/common/PageHeader'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

function Tile({ label, value, to }: { label: string; value: number | undefined; to?: string }) {
  const body = (
    <Card className="h-full hover:bg-accent">
      <CardContent className="flex flex-col gap-1 p-5">
        <span className="text-sm text-muted-foreground">{label}</span>
        {value === undefined ? (
          <span
            className="text-lg font-medium text-muted-foreground"
            title="This part of SwiftDrop is not responding"
          >
            Unavailable
          </span>
        ) : (
          <span className="text-3xl font-bold tabular-nums">{value}</span>
        )}
      </CardContent>
    </Card>
  )
  return to ? (
    <Link
      to={to}
      className="rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
    >
      {body}
    </Link>
  ) : (
    body
  )
}

export function DashboardPage() {
  const q = useQuery({
    queryKey: qk.admin.dashboard,
    queryFn: adminApi.dashboard,
    refetchInterval: 30_000,
  })
  if (q.isError) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />
  const d = q.data
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Dashboard" description="What needs attention right now." />
      {q.isPending ? (
        <div aria-busy="true" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <Skeleton key={i} className="h-24" />
          ))}
        </div>
      ) : (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            <Tile label="Parcels today" value={d?.parcels?.today} to="/admin/parcels" />
            <Tile
              label="Waiting for a courier"
              value={d?.dispatch?.unassigned}
              to="/admin/dispatch"
            />
            <Tile label="Out for delivery" value={d?.parcels?.outForDelivery} />
            <Tile label="Delivered today" value={d?.parcels?.deliveredToday} />
            <Tile label="Failed today" value={d?.parcels?.failedToday} />
            <Tile
              label="Merchants pending approval"
              value={d?.merchants?.pending}
              to="/admin/merchants"
            />
          </div>
          {(!d?.parcels || !d?.dispatch || !d?.merchants) && (
            <p
              role="status"
              className="rounded-md bg-amber-100 p-3 text-sm text-amber-900 dark:bg-amber-950 dark:text-amber-100"
            >
              Part of SwiftDrop is temporarily unavailable, so some figures are missing.
            </p>
          )}
        </>
      )}
    </div>
  )
}
