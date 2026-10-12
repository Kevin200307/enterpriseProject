import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { merchantApi } from '@/api/endpoints/merchant'
import { qk } from '@/api/queryKeys'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { PageHeader } from '@/components/common/PageHeader'
import { Pagination } from '@/components/common/Pagination'
import { StatusChip } from '@/components/common/StatusChip'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { useMobile } from '@/hooks/useMobile'
import { formatDate } from '@/lib/format'
import { MerchantGate } from './MerchantGate'

export function MerchantOrdersPage() {
  const isMobile = useMobile()
  const [params, setParams] = useSearchParams()
  const term = params.get('q') ?? ''
  const page = Math.max(0, Number(params.get('page')) || 0)
  const [draft, setDraft] = useState(term)
  const q = useQuery({
    queryKey: qk.merchant.orders({ term, page }),
    queryFn: () => merchantApi.orders({ q: term || undefined, page }),
    placeholderData: (prev) => prev,
    retry: false,
  })
  const search = (e: React.FormEvent) => {
    e.preventDefault()
    const next = new URLSearchParams()
    if (draft.trim()) next.set('q', draft.trim())
    setParams(next)
  }

  return (
    <MerchantGate error={q.error}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Orders"
          actions={
            <Button asChild>
              <Link to="/merchant/orders/new">New order</Link>
            </Button>
          }
        />
        <form onSubmit={search} className="flex items-end gap-2">
          <div className="flex min-w-0 flex-1 flex-col gap-1">
            <Label htmlFor="o-ref" className="text-xs">
              Search by reference
            </Label>
            <Input id="o-ref" value={draft} onChange={(e) => setDraft(e.target.value)} />
          </div>
          <Button type="submit" variant="secondary" size="sm" className="h-11 shrink-0">
            Search
          </Button>
        </form>

        {q.isPending ? (
          <Skeleton className="h-48 w-full" />
        ) : q.isError ? (
          <ErrorState error={q.error} onRetry={() => void q.refetch()} />
        ) : q.data.content.length === 0 ? (
          <EmptyState
            title={term ? 'No orders match that reference' : 'No orders yet'}
            description={term ? undefined : 'Create an order here or through the API.'}
          />
        ) : (
          <>
            {/* Phones get a card per order; the table needs more width than they have.
                Branching in JS rather than CSS keeps one copy of each order in the DOM. */}
            {isMobile ? (
            <ul className="flex flex-col gap-3">
              {q.data.content.map((o) => (
                <li key={o.trackingNumber} className="rounded-2xl border bg-card p-4">
                  <div className="flex items-start justify-between gap-3">
                    <p className="font-semibold">{o.merchantRef}</p>
                    <StatusChip status={o.status} />
                  </div>
                  <Link
                    className="mt-1 inline-block font-mono text-sm underline-offset-4 hover:underline"
                    to={`/track/${o.trackingNumber}`}
                  >
                    {o.trackingNumber}
                  </Link>
                  <dl className="mt-3 flex justify-between text-sm">
                    <div>
                      <dt className="text-xs text-muted-foreground">Destination</dt>
                      <dd>{o.destinationCity}</dd>
                    </div>
                    <div className="text-right">
                      <dt className="text-xs text-muted-foreground">Created</dt>
                      <dd>{formatDate(o.createdAt)}</dd>
                    </div>
                  </dl>
                </li>
              ))}
            </ul>
            ) : (
            <div className="overflow-x-auto rounded-lg border">
              <table className="w-full text-sm">
                <thead className="bg-muted text-left">
                  <tr>
                    {['Reference', 'Tracking', 'Destination', 'Created', 'Status'].map((h) => (
                      <th key={h} scope="col" className="p-3 font-medium">
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {q.data.content.map((o) => (
                    <tr key={o.trackingNumber} className="border-t">
                      <th scope="row" className="p-3 text-left font-medium">
                        {o.merchantRef}
                      </th>
                      <td className="p-3 font-mono">
                        <Link
                          className="underline-offset-4 hover:underline"
                          to={`/track/${o.trackingNumber}`}
                        >
                          {o.trackingNumber}
                        </Link>
                      </td>
                      <td className="p-3">{o.destinationCity}</td>
                      <td className="p-3">{formatDate(o.createdAt)}</td>
                      <td className="p-3">
                        <StatusChip status={o.status} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            )}
            <Pagination
              page={q.data.number}
              totalPages={q.data.totalPages}
              onChange={(n) => {
                const next = new URLSearchParams(params)
                next.set('page', String(n))
                setParams(next)
              }}
            />
          </>
        )}
      </div>
    </MerchantGate>
  )
}
