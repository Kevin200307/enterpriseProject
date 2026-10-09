import { useQuery } from '@tanstack/react-query'
import { Share2 } from 'lucide-react'
import { useEffect } from 'react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { isApiError } from '@/api/ApiError'
import { trackingApi } from '@/api/endpoints/tracking'
import { qk } from '@/api/queryKeys'
import { ErrorState } from '@/components/common/ErrorState'
import { StatusTimeline } from '@/components/common/StatusTimeline'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { TRACKING_NUMBER_RE, formatDateTime, timeAgo } from '@/lib/format'
import { cn } from '@/lib/cn'
import { statusMeta } from '@/lib/status'
import { shareLink, trackingUrl } from '@/lib/share'
import type { ConnectionState } from '@/realtime/ladder'
import { useParcelLiveStatus } from '@/realtime/useParcelLiveStatus'

const STEPS = ['CREATED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED'] as const

const CONNECTION_LABEL: Record<ConnectionState, string> = {
  live: 'Live',
  sse: 'Live',
  polling: 'Updating every 15 s',
  offline: 'Offline',
}

function Notice({ title, message }: { title: string; message: string }) {
  return (
    <div role="alert" className="flex flex-col items-center gap-4 py-10 text-center">
      <h1 className="text-xl font-semibold">{title}</h1>
      <p className="max-w-sm text-sm text-muted-foreground">{message}</p>
      <Button asChild>
        <Link to="/track">Track another parcel</Link>
      </Button>
    </div>
  )
}

function Stepper({ status }: { status: string }) {
  const current = STEPS.indexOf(status as (typeof STEPS)[number])
  return (
    <ol className="flex items-center" aria-label="Delivery progress">
      {STEPS.map((s, i) => {
        const done = current >= i
        return (
          <li
            key={s}
            className="flex flex-1 items-center last:flex-none"
            aria-current={current === i ? 'step' : undefined}
          >
            <span
              className={cn(
                'h-3 w-3 shrink-0 rounded-full border-2',
                done ? 'border-primary bg-primary' : 'border-muted-foreground/40',
              )}
              title={statusMeta(s).label}
            >
              <span className="sr-only">
                {statusMeta(s).label}
                {done ? ' (reached)' : ''}
              </span>
            </span>
            {i < STEPS.length - 1 && (
              <span
                className={cn('h-0.5 flex-1', current > i ? 'bg-primary' : 'bg-muted')}
                aria-hidden
              />
            )}
          </li>
        )
      })}
    </ol>
  )
}

export function PublicTrackPage() {
  const { trackingNumber = '' } = useParams()
  const tn = trackingNumber.toUpperCase()
  const valid = TRACKING_NUMBER_RE.test(tn)

  const query = useQuery({
    queryKey: qk.tracking.public(tn),
    queryFn: ({ signal }) => trackingApi.get(tn, signal),
    enabled: valid,
    retry: false,
  })
  const connection = useParcelLiveStatus(tn, { enabled: valid && query.isSuccess })

  useEffect(() => {
    document.title = valid ? `Tracking ${tn} · SwiftDrop` : 'Track a parcel · SwiftDrop'
  }, [tn, valid])

  if (!valid) {
    return (
      <Notice
        title="Invalid tracking number"
        message="That doesn't look like a SwiftDrop tracking number. It looks like SD-ABCD2345."
      />
    )
  }
  if (query.isPending) return <Skeleton className="h-72 w-full" aria-busy="true" />
  if (query.isError) {
    const e = query.error
    if (isApiError(e) && e.status === 404) {
      return (
        <Notice
          title="We couldn't find that parcel"
          message="Check the tracking number and try again."
        />
      )
    }
    if (isApiError(e) && e.errorCode === 'RATE_LIMITED') {
      return (
        <Notice
          title="Too many lookups"
          message={`Please wait ${e.retryAfter ?? 30} seconds and try again.`}
        />
      )
    }
    return <ErrorState error={e} onRetry={() => void query.refetch()} />
  }

  const t = query.data
  const { label, icon: Icon } = statusMeta(t.status)
  const terminalProblem = t.status === 'FAILED' || t.status === 'CANCELLED'
  const items = [...t.timeline].sort((a, b) => b.occurredAt.localeCompare(a.occurredAt))

  return (
    <article className="flex flex-col gap-6">
      <header className="flex flex-col items-center gap-2 text-center">
        <p className="font-mono text-sm text-muted-foreground">{t.trackingNumber}</p>
        <Icon
          className={cn('h-12 w-12', terminalProblem ? 'text-destructive' : 'text-primary')}
          aria-hidden
        />
        <h1 className="text-3xl font-bold">{label}</h1>
        <p className="flex items-center gap-2 text-sm text-muted-foreground" aria-live="polite">
          <span>Updated {timeAgo(t.updatedAt)}</span>
          <span aria-hidden>·</span>
          <span className="flex items-center gap-1.5">
            <span
              className={cn(
                'h-2 w-2 rounded-full',
                connection === 'live' || connection === 'sse'
                  ? 'bg-green-500'
                  : connection === 'polling'
                    ? 'bg-amber-500'
                    : 'bg-zinc-400',
              )}
              aria-hidden
            />
            {CONNECTION_LABEL[connection]}
          </span>
        </p>
      </header>

      <section aria-label="Route" className="rounded-lg border p-4 text-center">
        <p className="text-lg font-semibold">
          {t.originCity} <span aria-label="to">→</span> {t.destinationCity}
        </p>
        {t.eta && !terminalProblem && t.status !== 'DELIVERED' && (
          <p className="mt-1 text-sm text-muted-foreground">
            Estimated delivery {formatDateTime(t.eta)}
          </p>
        )}
        {t.courierFirstName && (
          <p className="mt-1 text-sm text-muted-foreground">Your courier: {t.courierFirstName}</p>
        )}
      </section>

      {terminalProblem ? (
        <p role="status" className="rounded-md bg-red-50 p-3 text-center text-sm text-red-900">
          {t.status === 'FAILED'
            ? 'Delivery was unsuccessful. The sender has been notified.'
            : 'This delivery was cancelled.'}
        </p>
      ) : (
        <Stepper status={t.status} />
      )}

      <StatusTimeline items={items} />

      <footer className="flex flex-wrap justify-center gap-2">
        <Button
          variant="outline"
          onClick={async () => {
            if ((await shareLink(trackingUrl(t.trackingNumber))) === 'copied')
              toast.success('Link copied')
          }}
        >
          <Share2 className="h-4 w-4" aria-hidden /> Share
        </Button>
        <Button asChild variant="ghost">
          <Link to="/track">Track another</Link>
        </Button>
      </footer>
    </article>
  )
}
