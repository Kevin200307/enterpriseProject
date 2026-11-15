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
import { useMobile } from '@/hooks/useMobile'

const FILTERABLE = Object.keys(STATUS_META) as ParcelStatus[]

/** Filters live in the URL so they are shareable, survive reload and keep the back button honest. */
export function DeliveriesPage() {
  const [params, setParams] = useSearchParams()
  const status = params.getAll('status')
  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''
  const page = Math.max(0, Number(params.get('page')) || 0)
  const filtered = status.length > 0 || !!from || !!to
  const isMobile = useMobile()
  const [filtersOpen, setFiltersOpen] = useState(false)
  const [draftStatus, setDraftStatus] = useState<string[]>(status)
  const [draftFrom, setDraftFrom] = useState(from)
  const [draftTo, setDraftTo] = useState(to)

  const update = (mutate: (p: URLSearchParams) => void) => {
    const next = new URLSearchParams(params)
    mutate(next)
    if (!next.has('page') || next.get('page') === '0') next.delete('page')
    setParams(next)
  }
  const list = useQuery({
    queryKey: qk.parcels.list({ status, from, to, page }),
    queryFn: () => parcelsApi.list({ status, from: from || undefined, to: to || undefined, page }),
    placeholderData: (prev) => prev,
  })

  const openFilters = (open: boolean) => {
    if (open) {
      setDraftStatus(status)
      setDraftFrom(from)
      setDraftTo(to)
    }
    setFiltersOpen(open)
  }
  const toggleStatus = (s: string) =>
    setDraftStatus((current) =>
      current.includes(s) ? current.filter((x) => x !== s) : [...current, s],
    )
  const applyFilters = () => {
    const next = new URLSearchParams()
    draftStatus.forEach((s) => next.append('status', s))
    if (draftFrom) next.set('from', draftFrom)
    if (draftTo) next.set('to', draftTo)
    setParams(next)
    setFiltersOpen(false)
  }
  const clearFilters = () => {
    setDraftStatus([])
    setDraftFrom('')
    setDraftTo('')
    setParams(new URLSearchParams())
  }

  const filterContent = (
    <div className="flex flex-col gap-4">
      <fieldset className="flex flex-wrap gap-2">
        <legend className="sr-only">Status</legend>
        {FILTERABLE.map((s) => {
          const on = draftStatus.includes(s)
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
      <div className="grid grid-cols-2 gap-3 md:grid-cols-[minmax(0,12rem)_minmax(0,12rem)_auto] md:items-end">
        <div className="min-w-0 flex flex-col gap-1.5">
          <Label htmlFor="filter-from" className="text-xs">
            From
          </Label>
          <DatePicker
            id="filter-from"
            value={draftFrom}
            max={draftTo || undefined}
            onChange={setDraftFrom}
          />
        </div>
        <div className="min-w-0 flex flex-col gap-1.5">
          <Label htmlFor="filter-to" className="text-xs">
            To
          </Label>
          <DatePicker
            id="filter-to"
            value={draftTo}
            min={draftFrom || undefined}
            onChange={setDraftTo}
          />
        </div>
        <div className="col-span-2 mt-1 grid grid-cols-2 gap-3 md:col-span-1">
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={!draftStatus.length && !draftFrom && !draftTo}
            className="w-full"
            onClick={clearFilters}
          >
            Clear filters
          </Button>
          <Button
            type="button"
            size="sm"
            className="w-full bg-black text-white hover:bg-black/90 hover:text-white active:!bg-black active:!text-white"
            onClick={applyFilters}
          >
            Apply filters
          </Button>
        </div>
      </div>
    </div>
  )

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
              onClick={() => openFilters(!filtersOpen)}
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

      {!isMobile && filtersOpen && (
        <section
          aria-label="Filter deliveries"
          className="rounded-2xl border bg-card p-4 shadow-sm"
        >
          <div className="mb-3">
            <h2 className="text-sm font-semibold">Filter deliveries</h2>
            <p className="mt-1 text-xs text-muted-foreground">
              Narrow your deliveries by status or date.
            </p>
          </div>
          {filterContent}
        </section>
      )}

      {isMobile && (
        <Dialog
          open={filtersOpen}
          onOpenChange={openFilters}
          title="Filter deliveries"
          description="Narrow your deliveries by status or date."
          side="bottom"
        >
          {filterContent}
        </Dialog>
      )}

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
                  className="relative grid items-start gap-2 rounded-lg border p-4 pr-32 hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring md:grid-cols-[1.2fr_1.5fr_1fr_auto] md:items-center md:gap-4"
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
