import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { notificationsApi } from '@/api/endpoints/notifications'
import { describeError } from '@/api/errorMessages'
import { qk } from '@/api/queryKeys'
import type { NotificationChannel, NotificationPrefs } from '@/api/types'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { PageHeader } from '@/components/common/PageHeader'
import { Pagination } from '@/components/common/Pagination'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDateTime, humanize } from '@/lib/format'
import { STATUS_META, type ParcelStatus } from '@/lib/status'
import { NotificationBadge } from './NotificationBadge'

const CHANNELS: { id: NotificationChannel; label: string }[] = [
  { id: 'EMAIL', label: 'Email' },
  { id: 'SMS', label: 'SMS' },
]
const STATUSES: ParcelStatus[] = [
  'CREATED',
  'PICKED_UP',
  'IN_TRANSIT',
  'OUT_FOR_DELIVERY',
  'DELIVERED',
  'FAILED',
]

function Preferences() {
  const qc = useQueryClient()
  const q = useQuery({ queryKey: qk.notifications.prefs, queryFn: notificationsApi.preferences })
  const [draft, setDraft] = useState<NotificationPrefs>()
  useEffect(() => {
    if (q.data) setDraft({ EMAIL: q.data.EMAIL ?? [], SMS: q.data.SMS ?? [] })
  }, [q.data])

  const save = useMutation({
    mutationFn: (p: NotificationPrefs) => notificationsApi.savePreferences(p),
    onSuccess: (saved) => {
      qc.setQueryData(qk.notifications.prefs, saved)
      toast.success('Preferences saved')
    },
    onError: (e) => toast.error(describeError(e).message),
  })

  if (q.isPending || !draft)
    return q.isError ? (
      <ErrorState error={q.error} onRetry={() => void q.refetch()} />
    ) : (
      <Skeleton className="h-56 w-full" />
    )
  const dirty =
    JSON.stringify([...draft.EMAIL].sort()) !== JSON.stringify([...(q.data?.EMAIL ?? [])].sort()) ||
    JSON.stringify([...draft.SMS].sort()) !== JSON.stringify([...(q.data?.SMS ?? [])].sort())
  const toggle = (ch: NotificationChannel, s: ParcelStatus) =>
    setDraft(
      (d) => d && { ...d, [ch]: d[ch].includes(s) ? d[ch].filter((x) => x !== s) : [...d[ch], s] },
    )

  return (
    <Card>
      <CardHeader>
        <CardTitle>Notify me when a parcel is…</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="text-left">
                <th scope="col" className="py-2 font-medium">
                  Status
                </th>
                {CHANNELS.map((c) => (
                  <th key={c.id} scope="col" className="px-4 py-2 text-center font-medium">
                    {c.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {STATUSES.map((s) => (
                <tr key={s} className="border-t">
                  <th scope="row" className="py-2 text-left font-normal">
                    {STATUS_META[s].label}
                  </th>
                  {CHANNELS.map((c) => (
                    <td key={c.id} className="px-4 py-2 text-center">
                      <input
                        type="checkbox"
                        aria-label={`${c.label} when ${STATUS_META[s].label.toLowerCase()}`}
                        checked={draft[c.id].includes(s)}
                        onChange={() => toggle(c.id, s)}
                      />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="flex items-center gap-3">
          <Button disabled={!dirty || save.isPending} onClick={() => save.mutate(draft)}>
            {save.isPending ? 'Saving…' : 'Save preferences'}
          </Button>
          {dirty && (
            <Button
              variant="ghost"
              onClick={() => setDraft({ EMAIL: q.data?.EMAIL ?? [], SMS: q.data?.SMS ?? [] })}
            >
              Discard changes
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  )
}

function History() {
  const [params, setParams] = useSearchParams()
  const page = Math.max(0, Number(params.get('page')) || 0)
  const q = useQuery({
    queryKey: qk.notifications.mine(page),
    queryFn: () => notificationsApi.mine(page),
    placeholderData: (p) => p,
  })
  return (
    <Card>
      <CardHeader>
        <CardTitle>Recent notifications</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-col gap-4">
        {q.isPending ? (
          <Skeleton className="h-32 w-full" />
        ) : q.isError ? (
          <ErrorState error={q.error} onRetry={() => void q.refetch()} />
        ) : q.data.content.length === 0 ? (
          <EmptyState
            title="Nothing sent yet"
            description="Updates about your parcels will show up here."
          />
        ) : (
          <>
            <ul className="flex flex-col divide-y">
              {q.data.content.map((n) => (
                <li
                  key={n.id}
                  className="flex flex-wrap items-center justify-between gap-2 py-3 text-sm"
                >
                  <div>
                    <p className="font-medium">
                      {humanize(n.templateKey)}
                      {n.trackingNumber && (
                        <span className="ml-2 font-mono text-xs text-muted-foreground">
                          {n.trackingNumber}
                        </span>
                      )}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {n.channel === 'EMAIL' ? 'Email' : 'SMS'} ·{' '}
                      {formatDateTime(n.sentAt ?? n.createdAt)}
                    </p>
                  </div>
                  <NotificationBadge status={n.status} />
                </li>
              ))}
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
      </CardContent>
    </Card>
  )
}

export function NotificationsPage() {
  return (
    <div className="mx-auto flex max-w-2xl flex-col gap-6">
      <PageHeader
        title="Notifications"
        description="Choose how we keep you posted, and see what we sent."
      />
      <Preferences />
      <History />
    </div>
  )
}
