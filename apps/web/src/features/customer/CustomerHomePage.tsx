import { useQuery } from '@tanstack/react-query'
import { ArrowRight, Boxes, PackagePlus, Search } from 'lucide-react'
import { Link } from 'react-router-dom'
import { parcelsApi } from '@/api/endpoints/parcels'
import { qk } from '@/api/queryKeys'
import { usersApi } from '@/api/endpoints/auth'
import { EmptyState } from '@/components/common/EmptyState'
import { StatusChip } from '@/components/common/StatusChip'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'

export function CustomerHomePage() {
  const me = useQuery({ queryKey: qk.me, queryFn: usersApi.me })
  const recent = useQuery({
    queryKey: qk.parcels.list({ page: 0 }),
    queryFn: () => parcelsApi.list({ page: 0 }),
  })

  const firstName = me.data?.fullName?.split(' ')[0]
  const parcels = recent.data?.content.slice(0, 3) ?? []
  const active = (recent.data?.content ?? []).filter(
    (p) => p.status !== 'DELIVERED' && p.status !== 'CANCELLED',
  ).length

  return (
    <div className="flex flex-col gap-6">
      <div>
        <p className="text-sm text-muted-foreground">Welcome back</p>
        <h1 className="text-2xl font-bold tracking-tight">{firstName ?? 'there'}</h1>
      </div>

      {/* Headline figure, styled like the dark cards in the design language */}
      <div className="flex flex-col gap-5 rounded-3xl bg-sidebar p-6 text-white">
        <div className="flex items-start justify-between gap-4">
          <div>
            <p className="text-sm text-white/60">Active deliveries</p>
            <p className="mt-1 text-5xl font-bold leading-none tracking-tight">
              {recent.isPending ? '—' : active}
            </p>
          </div>
          <span
            aria-hidden
            className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-white/10"
          >
            <Boxes className="h-5 w-5" />
          </span>
        </div>
        <Link
          to="/deliveries"
          className="inline-flex w-fit self-end items-center gap-1.5 rounded-full bg-accent px-4 py-2 text-sm font-semibold text-accent-foreground transition-transform active:scale-95"
        >
          View all <ArrowRight className="h-4 w-4" aria-hidden />
        </Link>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <QuickAction to="/deliveries/new" icon={PackagePlus} label="New delivery" primary />
        <QuickAction to="/track" icon={Search} label="Track a parcel" />
      </div>

      <section className="flex flex-col gap-3">
        <div className="flex items-baseline justify-between">
          <h2 className="text-lg font-semibold tracking-tight">Recent</h2>
          <Link to="/deliveries" className="text-sm text-muted-foreground hover:underline">
            See all
          </Link>
        </div>

        {recent.isPending ? (
          <div aria-busy="true" className="flex flex-col gap-3">
            <Skeleton className="h-20 w-full rounded-2xl" />
            <Skeleton className="h-20 w-full rounded-2xl" />
          </div>
        ) : parcels.length === 0 ? (
          <EmptyState
            title="No deliveries yet"
            description="Book your first one and it will show up here."
          />
        ) : (
          <ul className="flex flex-col gap-3">
            {parcels.map((p) => (
              <li key={p.id}>
                <Link
                  to={`/deliveries/${p.id}`}
                  className="flex items-center justify-between gap-3 rounded-2xl border bg-card p-4 transition-colors hover:bg-secondary"
                >
                  <div className="min-w-0">
                    <p className="truncate font-mono text-sm font-medium">{p.trackingNumber}</p>
                    <p className="mt-0.5 truncate text-xs text-muted-foreground">
                      {p.pickup.city} → {p.dropoff.city} · {formatDate(p.createdAt)}
                    </p>
                  </div>
                  <StatusChip status={p.status} />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}

function QuickAction({
  to,
  icon: Icon,
  label,
  primary,
}: {
  to: string
  icon: typeof PackagePlus
  label: string
  primary?: boolean
}) {
  return (
    <Link
      to={to}
      className={
        primary
          ? 'flex flex-col gap-3 rounded-2xl bg-accent p-4 text-accent-foreground transition-opacity hover:opacity-90'
          : 'flex flex-col gap-3 rounded-2xl border bg-card p-4 transition-colors hover:bg-secondary'
      }
    >
      <Icon className="h-5 w-5" aria-hidden />
      <span className="text-sm font-semibold leading-tight">{label}</span>
    </Link>
  )
}
