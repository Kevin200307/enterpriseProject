import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { adminApi } from '@/api/endpoints/admin'
import { qk } from '@/api/queryKeys'
import { ErrorState } from '@/components/common/ErrorState'
import { PageHeader } from '@/components/common/PageHeader'
import { Card, CardContent } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

const chartColors = ['bg-primary', 'bg-accent', 'bg-sky-500', 'bg-destructive']

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

function BarChart({
  title,
  description,
  items,
}: {
  title: string
  description: string
  items: { label: string; value: number }[]
}) {
  const maximum = Math.max(...items.map((item) => item.value), 1)
  return (
    <Card>
      <CardContent className="p-5">
        <h2 className="font-semibold">{title}</h2>
        <p className="mt-1 text-sm text-muted-foreground">{description}</p>
        <div className="mt-5 flex flex-col gap-4" role="img" aria-label={`${title}: ${items.map((item) => `${item.label} ${item.value}`).join(', ')}`}>
          {items.map((item, index) => (
            <div key={item.label} className="grid grid-cols-[7rem_1fr_2rem] items-center gap-3 text-sm">
              <span className="truncate text-muted-foreground">{item.label}</span>
              <div className="h-2.5 overflow-hidden rounded-full bg-secondary">
                <div
                  className={`h-full rounded-full ${chartColors[index % chartColors.length]}`}
                  style={{ width: `${(item.value / maximum) * 100}%` }}
                />
              </div>
              <span className="text-right font-semibold tabular-nums">{item.value}</span>
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}

function StatusChart({
  delivered,
  outForDelivery,
  waiting,
  failed,
}: {
  delivered: number
  outForDelivery: number
  waiting: number
  failed: number
}) {
  const items = [
    { label: 'Delivered', value: delivered, color: 'bg-primary' },
    { label: 'Out for delivery', value: outForDelivery, color: 'bg-accent' },
    { label: 'Waiting', value: waiting, color: 'bg-sky-500' },
    { label: 'Failed', value: failed, color: 'bg-destructive' },
  ]
  const total = items.reduce((sum, item) => sum + item.value, 0)
  const gradient = total
    ? `conic-gradient(${items
        .map((_, index) => {
          const start = index === 0 ? 0 : (items.slice(0, index).reduce((sum, entry) => sum + entry.value, 0) / total) * 100
          const end = (items.slice(0, index + 1).reduce((sum, entry) => sum + entry.value, 0) / total) * 100
          const color = ['hsl(var(--primary))', 'hsl(var(--accent))', 'rgb(14 165 233)', 'hsl(var(--destructive))'][index]
          return `${color} ${start}% ${end}%`
        })
        .join(', ')})`
    : 'hsl(var(--secondary))'

  return (
    <Card>
      <CardContent className="p-5">
        <h2 className="font-semibold">Parcel status</h2>
        <p className="mt-1 text-sm text-muted-foreground">Today&apos;s operational mix</p>
        <div className="mt-5 flex items-center gap-6">
          <div
            role="img"
            aria-label={`Parcel status: ${items.map((item) => `${item.label} ${item.value}`).join(', ')}`}
            className="relative h-32 w-32 shrink-0 rounded-full"
            style={{ background: gradient }}
          >
            <div className="absolute inset-5 flex items-center justify-center rounded-full bg-card text-center">
              <span>
                <strong className="block text-2xl tabular-nums">{total}</strong>
                <span className="text-xs text-muted-foreground">parcels</span>
              </span>
            </div>
          </div>
          <div className="grid min-w-0 gap-3 text-sm">
            {items.map((item) => (
              <div key={item.label} className="flex items-center gap-2">
                <span className={`h-2.5 w-2.5 shrink-0 rounded-full ${item.color}`} />
                <span className="truncate text-muted-foreground">{item.label}</span>
                <span className="ml-auto font-semibold tabular-nums">{item.value}</span>
              </div>
            ))}
          </div>
        </div>
      </CardContent>
    </Card>
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
          {d?.parcels && d.dispatch && (
            <div className="grid gap-4 lg:grid-cols-2">
              <StatusChart
                delivered={d.parcels.deliveredToday}
                outForDelivery={d.parcels.outForDelivery}
                waiting={d.dispatch.unassigned}
                failed={d.parcels.failedToday}
              />
              <BarChart
                title="Today at a glance"
                description="Volume across the main operations"
                items={[
                  { label: 'All parcels', value: d.parcels.today },
                  { label: 'Delivered', value: d.parcels.deliveredToday },
                  { label: 'Out for delivery', value: d.parcels.outForDelivery },
                  { label: 'Waiting', value: d.dispatch.unassigned },
                  { label: 'Failed', value: d.parcels.failedToday },
                ]}
              />
            </div>
          )}
          {(!d?.parcels || !d?.dispatch || !d?.merchants) && (
            <p role="status" className="rounded-md bg-amber-100 p-3 text-sm text-amber-900">
              Part of SwiftDrop is temporarily unavailable, so some figures are missing.
            </p>
          )}
        </>
      )}
    </div>
  )
}
