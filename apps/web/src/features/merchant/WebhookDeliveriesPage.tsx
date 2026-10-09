import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { merchantApi } from '@/api/endpoints/merchant'
import { describeError } from '@/api/errorMessages'
import { qk } from '@/api/queryKeys'
import type { WebhookDelivery, WebhookState } from '@/api/types'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { PageHeader } from '@/components/common/PageHeader'
import { Pagination } from '@/components/common/Pagination'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/cn'
import { formatDateTime } from '@/lib/format'
import { MerchantGate } from './MerchantGate'

const STATE_TONE: Record<WebhookState, string> = {
  DELIVERED: 'bg-green-100 text-green-900 dark:bg-green-950 dark:text-green-100',
  PENDING: 'bg-amber-100 text-amber-900 dark:bg-amber-950 dark:text-amber-100',
  DEAD: 'bg-red-100 text-red-900 dark:bg-red-950 dark:text-red-100',
}

export function WebhookDeliveriesPage() {
  const qc = useQueryClient()
  const [params, setParams] = useSearchParams()
  const page = Math.max(0, Number(params.get('page')) || 0)
  const [open, setOpen] = useState<string>()
  const [replaying, setReplaying] = useState<WebhookDelivery>()
  const q = useQuery({
    queryKey: qk.merchant.deliveries({ page }),
    queryFn: () => merchantApi.deliveries(page),
    placeholderData: (p) => p,
    retry: false,
  })
  const replay = useMutation({
    mutationFn: (d: WebhookDelivery) => merchantApi.replay(d.id),
    onSuccess: () => toast.success('Delivery queued again'),
    onError: (e) => toast.error(describeError(e).message),
    onSettled: () => {
      setReplaying(undefined)
      void qc.invalidateQueries({ queryKey: qk.merchant.all })
    },
  })

  return (
    <MerchantGate error={q.error}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Webhook deliveries"
          description="Every event we tried to send to your endpoint."
        />
        {q.isPending ? (
          <Skeleton className="h-48 w-full" />
        ) : q.isError ? (
          <ErrorState error={q.error} onRetry={() => void q.refetch()} />
        ) : q.data.content.length === 0 ? (
          <EmptyState
            title="No deliveries yet"
            description="Events appear here once your parcels change status."
          />
        ) : (
          <>
            <ul className="flex flex-col gap-3">
              {q.data.content.map((d) => {
                const expanded = open === d.id
                return (
                  <li key={d.id} className="rounded-lg border p-4">
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div className="min-w-0">
                        <p className="font-mono text-sm font-semibold">{d.trackingNumber}</p>
                        <p className="truncate text-xs text-muted-foreground">
                          {d.attempts} attempt{d.attempts === 1 ? '' : 's'} · last HTTP{' '}
                          {d.lastStatus ?? '—'}
                          {d.state === 'PENDING' && d.nextRetryAt
                            ? ` · next retry ${formatDateTime(d.nextRetryAt)}`
                            : ''}
                        </p>
                      </div>
                      <div className="flex items-center gap-2">
                        <span
                          className={cn(
                            'rounded-full px-2.5 py-1 text-xs font-medium',
                            STATE_TONE[d.state],
                          )}
                        >
                          {d.state}
                        </span>
                        <Button
                          size="sm"
                          variant="outline"
                          aria-expanded={expanded}
                          aria-label={`${expanded ? 'Hide' : 'Show'} attempts for ${d.trackingNumber}`}
                          onClick={() => setOpen(expanded ? undefined : d.id)}
                        >
                          {expanded ? 'Hide' : 'Attempts'}
                        </Button>
                        <Button
                          size="sm"
                          variant="secondary"
                          aria-label={`Replay ${d.trackingNumber}`}
                          onClick={() => setReplaying(d)}
                        >
                          Replay
                        </Button>
                      </div>
                    </div>
                    {expanded && (
                      <ol
                        className="mt-3 flex flex-col gap-1 border-t pt-3 text-sm"
                        aria-label="Attempts"
                      >
                        {(d.history ?? []).length === 0 && (
                          <li className="text-muted-foreground">No attempt details recorded.</li>
                        )}
                        {(d.history ?? []).map((h) => (
                          <li key={h.at}>
                            {formatDateTime(h.at)} —{' '}
                            {h.status ? `HTTP ${h.status}` : (h.error ?? 'no response')}
                          </li>
                        ))}
                      </ol>
                    )}
                  </li>
                )
              })}
            </ul>
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
        <ConfirmDialog
          open={!!replaying}
          title={`Replay ${replaying?.trackingNumber}?`}
          description="We will send this event to your endpoint again. Your server should handle duplicates using the event id."
          confirmLabel="Replay"
          busy={replay.isPending}
          onCancel={() => setReplaying(undefined)}
          onConfirm={() => replaying && replay.mutate(replaying)}
        />
      </div>
    </MerchantGate>
  )
}
