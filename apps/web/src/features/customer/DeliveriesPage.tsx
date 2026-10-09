import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router-dom'
import { parcelsApi } from '@/api/endpoints/parcels'
import { qk } from '@/api/queryKeys'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { MoneyLKR } from '@/components/common/MoneyLKR'
import { PageHeader } from '@/components/common/PageHeader'
import { Pagination } from '@/components/common/Pagination'
import { StatusChip } from '@/components/common/StatusChip'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'
import { STATUS_META, type ParcelStatus } from '@/lib/status'
import { cn } from '@/lib/cn'

const FILTERABLE = Object.keys(STATUS_META) as ParcelStatus[]

/** Filters live in the URL so they are shareable, survive reload and keep the back button honest. */
export function DeliveriesPage() {
  const [params, setParams] = useSearchParams()
  const status = params.getAll('status')
  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''
  const page = Math.max(0, Number(params.get('page')) || 0)
  const filtered = status.length > 0 || !!from || !!to

  const update = (mutate: (p: URLSearchParams) => void) => {
    const next = new URLSearchParams(params)
    mutate(next)
    if (!next.has('page') || next.get('page') === '0') next.delete('page')
    setParams(next)
  }
  const resetPage = (p: URLSearchParams) => p.delete('page')

  const list = useQuery({
    queryKey: qk.parcels.list({ status, from, to, page }),
    queryFn: () => parcelsApi.list({ status, from: from || undefined, to: to || undefined, page }),
    placeholderData: (prev) => prev,
  })

  const toggleStatus = (s: string) =>
    update((p) => {
      const current = p.getAll('status')
      p.delete('status')
      ;(current.includes(s) ? current.filter((x) => x !== s) : [...current, s]).forEach((x) =>
        p.append('status', x),
      )
      resetPage(p)
    })
  const setDate = (key: 'from' | 'to', v: string) =>
    update((p) => {
      if (v) p.set(key, v)
      else p.delete(key)
      resetPage(p)
    })

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="My deliveries"
        actions={
          <Button asChild>
            <Link to="/deliveries/new">New delivery</Link>
          </Button>
        }
      />

      <section aria-label="Filters" className="flex flex-col gap-3 rounded-lg border p-4">
        <fieldset className="flex flex-wrap gap-2">
          <legend className="sr-only">Status</legend>
          {FILTERABLE.map((s) => {
            const on = status.includes(s)
            return (
              <button
                key={s}
                type="button"
                aria-pressed={on}
                onClick={() => toggleStatus(s)}
                className={cn(
                  'rounded-full border px-3 py-1 text-xs font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                  on ? 'border-primary bg-primary text-primary-foreground' : 'hover:bg-accent',
                )}
              >
                {STATUS_META[s].label}
              </button>
            )
          })}
        </fieldset>
        <div className="flex flex-wrap items-end gap-3">
          <div className="flex flex-col gap-1">
            <Label htmlFor="filter-from" className="text-xs">
              From
            </Label>
            <Input
              id="filter-from"
              type="date"
              value={from}
              max={to || undefined}
              onChange={(e) => setDate('from', e.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1">
            <Label htmlFor="filter-to" className="text-xs">
              To
            </Label>
            <Input
              id="filter-to"
              type="date"
              value={to}
              min={from || undefined}
              onChange={(e) => setDate('to', e.target.value)}
            />
          </div>
          {filtered && (
            <Button variant="ghost" size="sm" onClick={() => setParams(new URLSearchParams())}>
              Clear filters
            </Button>
          )}
        </div>
      </section>

      {list.isPending ? (
        <div className="flex flex-col gap-3" aria-busy="true">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} className="h-20 w-full" />
          ))}
        </div>
      ) : list.isError ? (
        <ErrorState error={list.error} onRetry={() => void list.refetch()} />
      ) : list.data.content.length === 0 ? (
        filtered ? (
          <EmptyState
            title="No deliveries match your filters"
            action={
              <Button variant="outline" onClick={() => setParams(new URLSearchParams())}>
                Clear filters
              </Button>
            }
          />
        ) : (
          <EmptyState
            title="No deliveries yet"
            description="Book your first delivery and track it here."
            action={
              <Button asChild>
                <Link to="/deliveries/new">Book your first delivery</Link>
              </Button>
            }
          />
        )
      ) : (
        <>
          <ul className={cn('flex flex-col gap-3', list.isPlaceholderData && 'opacity-60')}>
            {list.data.content.map((p) => (
              <li key={p.id}>
                <Link
                  to={`/deliveries/${p.id}`}
                  className="grid gap-2 rounded-lg border p-4 hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring md:grid-cols-[1.2fr_1.5fr_1fr_auto_auto] md:items-center md:gap-4"
                >
                  <span className="font-mono text-sm font-semibold">{p.trackingNumber}</span>
                  <span className="text-sm">
                    {p.pickup.city} → {p.dropoff.city}
                  </span>
                  <span className="text-sm text-muted-foreground">{formatDate(p.createdAt)}</span>
                  <MoneyLKR value={p.fee} />
                  <StatusChip status={p.status} />
                </Link>
              </li>
            ))}
          </ul>
          <Pagination
            page={list.data.number}
            totalPages={list.data.totalPages}
            onChange={(n) => update((p) => p.set('page', String(n)))}
          />
        </>
      )}
    </div>
  )
}
