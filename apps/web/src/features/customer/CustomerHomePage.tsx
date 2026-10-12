import { useQuery } from '@tanstack/react-query'
import { ArrowRight, PackagePlus, Search } from 'lucide-react'
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

      <div
        className="relative flex min-h-48 overflow-hidden rounded-3xl bg-card p-6 text-foreground sm:min-h-56"
      >
        <div className="relative z-10 flex h-full w-[58%] flex-col items-start">
          <div className="absolute left-0 top-1/2 -translate-y-1/2">
            <p className="text-sm text-muted-foreground">Active deliveries</p>
            <p className="mt-1 text-5xl font-bold leading-none tracking-tight">
              {recent.isPending ? '—' : active}
            </p>
          </div>
          <Link
            to="/deliveries"
            className="absolute bottom-0 left-0 inline-flex items-center gap-1.5 rounded-full bg-accent px-4 py-2 text-sm font-semibold text-accent-foreground transition-transform active:scale-95"
          >
            View all <ArrowRight className="h-4 w-4" aria-hidden />
          </Link>
        </div>
        <div className="absolute inset-y-0 right-0 w-[48%]">
          <img
            src="/active-deliveries-card.webp"
            alt=""
            aria-hidden
            className="h-full w-full object-cover object-center"
          />
          <div className="absolute inset-y-0 left-0 w-1/2 bg-gradient-to-r from-card to-transparent" />
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <QuickAction
          to="/deliveries/new"
          icon={PackagePlus}
          label="New delivery"
          background="/new-delivery-background.webp"
          primary
        />
        <QuickAction
          to="/track"
          icon={Search}
          label="Track a parcel"
          background="/track-parcel-background.webp"
        />
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
  background,
}: {
  to: string
  icon: typeof PackagePlus
  label: string
  primary?: boolean
  background: string
}) {
  return (
    <Link
      to={to}
      className={`relative min-h-36 overflow-hidden rounded-2xl bg-cover bg-center p-4 transition-transform hover:scale-[1.01] active:scale-[0.99] ${
        primary ? 'text-accent-foreground' : 'text-white'
      }`}
      style={{ backgroundImage: `url('${background}')` }}
    >
      <span
        className={`absolute inset-0 ${
          primary
            ? 'bg-gradient-to-r from-lime-300/90 via-lime-300/45 to-transparent'
            : 'bg-gradient-to-r from-black/75 via-black/30 to-transparent'
        }`}
      />
      <span className="relative z-10 flex h-full flex-col justify-between gap-3">
        <Icon className="h-6 w-6" aria-hidden />
        <span className="text-base font-semibold leading-tight">{label}</span>
      </span>
    </Link>
  )
}
