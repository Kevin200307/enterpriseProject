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
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { TRACKING_NUMBER_RE, formatDateTime, humanize } from '@/lib/format'
import { NotificationBadge } from '@/features/notifications/NotificationBadge'

export function NotificationLogPage() {
  const qc = useQueryClient()
  const [params, setParams] = useSearchParams()
  const tn = (params.get('tn') ?? '').toUpperCase()
  const [draft, setDraft] = useState(tn)
  const [tnError, setTnError] = useState<string>()
  const f = {
    status: params.get('status') ?? '',
    channel: params.get('channel') ?? '',
    from: params.get('from') ?? '',
    to: params.get('to') ?? '',
    page: Math.max(0, Number(params.get('page')) || 0),
  }
  const [resending, setResending] = useState<AdminNotification>()

  const byTracking = useQuery({
    queryKey: qk.notifications.byTracking(tn),
    queryFn: () => notificationsApi.byTracking(tn),
    enabled: !!tn,
    retry: false,
  })
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
    enabled: !tn,
    placeholderData: (p) => p,
  })
  const rows: AdminNotification[] = tn ? (byTracking.data ?? []) : (q.data?.content ?? [])
  const active = tn ? byTracking : q

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
  const submitTn = (e: React.FormEvent) => {
    e.preventDefault()
    const v = draft.trim().toUpperCase()
    if (v && !TRACKING_NUMBER_RE.test(v))
      return setTnError('Tracking numbers look like SD-ABCD2345.')
    setTnError(undefined)
    const next = new URLSearchParams()
    if (v) next.set('tn', v)
    setParams(next)
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Notification log"
        description="Delivery history. Recipients are shown by user id only."
      />
      <form onSubmit={submitTn} noValidate className="flex items-end gap-2">
        <div className="flex flex-col gap-1">
          <Label htmlFor="n-tn" className="text-xs">Tracking number</Label>
          <Input
            id="n-tn"
            value={draft}
            onChange={(e) => setDraft(e.target.value.toUpperCase())}
            placeholder="SD-XXXXXXXX"
            className="font-mono w-44"
          />
          {tnError && <p role="alert" className="text-xs text-destructive">{tnError}</p>}
        </div>
        <Button type="submit" variant="secondary">Search</Button>
        {tn && (
          <Button type="button" variant="outline" onClick={() => { setDraft(''); setParams(new URLSearchParams()) }}>
            Clear
          </Button>
        )}
      </form>
      <div className={`flex flex-wrap gap-3 ${tn ? 'opacity-50 pointer-events-none' : ''}`}>
        <div className="flex flex-col gap-1">
          <Label htmlFor="n-status" className="text-xs">
            Status
          </Label>
          <Select
            id="n-status"

            value={f.status}
            onChange={(e) => set('status', e.target.value)}
          >
            <option value="">All</option>
            {['PENDING', 'SENT', 'FAILED', 'SKIPPED'].map((s) => (
              <option key={s} value={s}>
                {humanize(s)}
              </option>
            ))}
          </Select>
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="n-channel" className="text-xs">
            Channel
          </Label>
          <Select
            id="n-channel"

            value={f.channel}
            onChange={(e) => set('channel', e.target.value)}
          >
            <option value="">All</option>
            <option value="EMAIL">Email</option>
            <option value="SMS">SMS</option>
          </Select>
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

      {active.isPending ? (
        <Skeleton className="h-48 w-full" />
      ) : active.isError ? (
        <ErrorState error={active.error} onRetry={() => void active.refetch()} />
      ) : rows.length === 0 ? (
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
                {rows.map((n) => (
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
          {!tn && q.data && (
            <Pagination
              page={q.data.number}
              totalPages={q.data.totalPages}
              onChange={(n) => {
                const next = new URLSearchParams(params)
                next.set('page', String(n))
                setParams(next)
              }}
            />
          )}
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
