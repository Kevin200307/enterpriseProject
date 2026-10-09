import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { notificationsApi } from '@/api/endpoints/notifications'
import { describeError } from '@/api/errorMessages'
import { qk } from '@/api/queryKeys'
import type { AdminNotification } from '@/api/types'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { PageHeader } from '@/components/common/PageHeader'
import { Pagination } from '@/components/common/Pagination'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDateTime, humanize } from '@/lib/format'
import { NotificationBadge } from '@/features/notifications/NotificationBadge'

const sel = 'h-10 rounded-md border border-input bg-background px-3 text-sm'

export function NotificationLogPage() {
  const qc = useQueryClient()
  const [params, setParams] = useSearchParams()
  const f = {
    status: params.get('status') ?? '',
    channel: params.get('channel') ?? '',
    from: params.get('from') ?? '',
    to: params.get('to') ?? '',
    page: Math.max(0, Number(params.get('page')) || 0),
  }
  const [resending, setResending] = useState<AdminNotification>()
  const q = useQuery({
    queryKey: qk.notifications.log(f),
    queryFn: () =>
      notificationsApi.log({
        status: f.status || undefined,
        channel: f.channel || undefined,
        from: f.from || undefined,
        to: f.to || undefined,
        page: f.page,
      }),
    placeholderData: (p) => p,
  })
  const resend = useMutation({
    mutationFn: (n: AdminNotification) => notificationsApi.resend(n.id),
    onSuccess: () => toast.success('Queued for resending'),
    onError: (e) => toast.error(describeError(e).message),
    onSettled: () => {
      setResending(undefined)
      void qc.invalidateQueries({ queryKey: qk.notifications.all })
    },
  })
  const set = (k: string, v: string) => {
    const next = new URLSearchParams(params)
    if (v) next.set(k, v)
    else next.delete(k)
    next.delete('page')
    setParams(next)
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Notification log"
        description="Delivery history. Recipients are shown by user id only."
      />
      <div className="flex flex-wrap gap-3">
        <div className="flex flex-col gap-1">
          <Label htmlFor="n-status" className="text-xs">
            Status
          </Label>
          <select
            id="n-status"
            className={sel}
            value={f.status}
            onChange={(e) => set('status', e.target.value)}
          >
            <option value="">All</option>
            {['PENDING', 'SENT', 'FAILED', 'SKIPPED'].map((s) => (
              <option key={s} value={s}>
                {humanize(s)}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="n-channel" className="text-xs">
            Channel
          </Label>
          <select
            id="n-channel"
            className={sel}
            value={f.channel}
            onChange={(e) => set('channel', e.target.value)}
          >
            <option value="">All</option>
            <option value="EMAIL">Email</option>
            <option value="SMS">SMS</option>
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="n-from" className="text-xs">
            From
          </Label>
          <Input
            id="n-from"
            type="date"
            value={f.from}
            max={f.to || undefined}
            onChange={(e) => set('from', e.target.value)}
          />
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="n-to" className="text-xs">
            To
          </Label>
          <Input
            id="n-to"
            type="date"
            value={f.to}
            min={f.from || undefined}
            onChange={(e) => set('to', e.target.value)}
          />
        </div>
      </div>

      {q.isPending ? (
        <Skeleton className="h-48 w-full" />
      ) : q.isError ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : q.data.content.length === 0 ? (
        <EmptyState title="No notifications match" />
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border">
            <table className="w-full text-sm">
              <thead className="bg-muted text-left">
                <tr>
                  {['When', 'Template', 'Channel', 'Recipient', 'Status', 'Attempts', ''].map(
                    (h) => (
                      <th key={h} scope="col" className="p-3 font-medium">
                        {h || <span className="sr-only">Actions</span>}
                      </th>
                    ),
                  )}
                </tr>
              </thead>
              <tbody>
                {q.data.content.map((n) => (
                  <tr key={n.id} className="border-t align-top">
                    <th scope="row" className="p-3 text-left font-normal">
                      {formatDateTime(n.sentAt ?? n.createdAt)}
                    </th>
                    <td className="p-3">{humanize(n.templateKey)}</td>
                    <td className="p-3">{n.channel === 'EMAIL' ? 'Email' : 'SMS'}</td>
                    <td className="p-3 font-mono text-xs">{n.recipientRef.slice(0, 8)}…</td>
                    <td className="p-3">
                      <NotificationBadge status={n.status} />
                      {n.lastError && (
                        <p className="mt-1 max-w-48 text-xs text-muted-foreground">{n.lastError}</p>
                      )}
                    </td>
                    <td className="p-3">{n.attempts}</td>
                    <td className="p-3 text-right">
                      {(n.status === 'FAILED' || n.status === 'SKIPPED') && (
                        <Button
                          size="sm"
                          variant="outline"
                          aria-label={`Resend ${humanize(n.templateKey)} to ${n.recipientRef.slice(0, 8)}`}
                          onClick={() => setResending(n)}
                        >
                          Resend
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
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
        open={!!resending}
        title="Resend this notification?"
        description="The recipient receives it again. Duplicates are possible if the first attempt actually arrived."
        confirmLabel="Resend"
        busy={resend.isPending}
        onCancel={() => setResending(undefined)}
        onConfirm={() => resending && resend.mutate(resending)}
      />
    </div>
  )
}
