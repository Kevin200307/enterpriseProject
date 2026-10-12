import { SlidersHorizontal } from 'lucide-react'
import { useState } from 'react'
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
import { DatePicker } from '@/components/ui/date-picker'
import { Dialog } from '@/components/ui/dialog'
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
  const [filtersOpen, setFiltersOpen] = useState(false)

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
          <>
            <Button
              type="button"
              variant="outline"
              aria-expanded={filtersOpen}
              onClick={() => setFiltersOpen((open) => !open)}
            >
              <SlidersHorizontal className="h-4 w-4" aria-hidden />
              Filter
              {filtered && <span className="sr-only"> (active filters)</span>}
            </Button>
            <Button asChild>
              <Link to="/deliveries/new">New delivery</Link>
            </Button>
          </>
        }
      />

      <Dialog
        open={filtersOpen}
        onOpenChange={setFiltersOpen}
        title="Filter deliveries"
        description="Narrow your deliveries by status or date."
        side="bottom"
      >
        <div className="flex flex-col gap-4">
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
          <div className="grid grid-cols-2 gap-3">
            <div className="min-w-0 flex flex-col gap-1.5">
              <Label htmlFor="filter-from" className="text-xs">
                From
              </Label>
              <DatePicker
                id="filter-from"
                value={from}
                max={to || undefined}
                onChange={(value) => setDate('from', value)}
              />
            </div>
            <div className="min-w-0 flex flex-col gap-1.5">
              <Label htmlFor="filter-to" className="text-xs">
                To
              </Label>
              <DatePicker
                id="filter-to"
                value={to}
                min={from || undefined}
                onChange={(value) => setDate('to', value)}
              />
            </div>
            <Button
              variant="outline"
              size="sm"
              disabled={!filtered}
              className="col-span-2 mt-1 w-full border-black bg-black text-white transition-opacity hover:bg-black/90 hover:text-white disabled:border-black disabled:bg-black disabled:text-white disabled:opacity-40"
              onClick={() => setParams(new URLSearchParams())}
            >
              Clear filters
            </Button>
          </div>
        </div>
      </Dialog>

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
                  className="relative grid gap-2 rounded-lg border p-4 pr-32 hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring md:grid-cols-[1.2fr_1.5fr_1fr_auto] md:items-center md:gap-4"
                >
                  <span className="font-mono text-sm font-semibold">{p.trackingNumber}</span>
                  <span className="text-sm">
                    {p.pickup.city} → {p.dropoff.city}
                  </span>
                  <span className="text-sm text-muted-foreground">{formatDate(p.createdAt)}</span>
                  <MoneyLKR value={p.fee} />
                  <StatusChip
                    status={p.status}
                    className="absolute right-4 top-4"
                  />
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
