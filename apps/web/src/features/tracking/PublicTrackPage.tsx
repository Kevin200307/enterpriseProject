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

function Stepper({ status, onDark = false }: { status: string; onDark?: boolean }) {
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
                'h-3 w-3 shrink-0 rounded-full border-2 transition-colors',
                current === i && 'h-3.5 w-3.5 ring-4',
                done
                  ? onDark
                    ? 'border-accent bg-accent ring-accent/20'
                    : 'border-primary bg-primary ring-primary/15'
                  : onDark
                    ? 'border-white/30'
                    : 'border-muted-foreground/40',
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
                className={cn(
                  'h-0.5 flex-1',
                  current > i
                    ? onDark
                      ? 'bg-accent'
                      : 'bg-primary'
                    : onDark
                      ? 'bg-white/15'
                      : 'bg-muted',
                )}
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
    <article className="mx-auto flex w-full max-w-2xl flex-col gap-4">
      {/* Hero — the dark card the rest of the app uses for headline information */}
      <section className="flex flex-col gap-6 rounded-3xl bg-sidebar p-6 text-white sm:p-8">
        <div className="flex items-start justify-between gap-4">
          <div className="min-w-0">
            <p className="truncate font-mono text-xs text-white/50">{t.trackingNumber}</p>
            <h1 className="mt-1 text-3xl font-bold leading-tight tracking-tight sm:text-4xl">{label}</h1>
          </div>
          <span
            aria-hidden
            className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-white/10 sm:h-14 sm:w-14"
          >
            <Icon className={cn('h-6 w-6 sm:h-7 sm:w-7', terminalProblem ? 'text-destructive' : 'text-accent')} />
          </span>
        </div>

        {!terminalProblem && <Stepper status={t.status} onDark />}

        <p className="flex flex-wrap items-center gap-2 text-xs text-white/60" aria-live="polite">
          <span>Updated {timeAgo(t.updatedAt)}</span>
          <span aria-hidden>·</span>
          <span className="flex items-center gap-1.5">
            <span
              className={cn(
                'h-2 w-2 rounded-full',
                connection === 'live' || connection === 'sse'
                  ? 'bg-accent'
                  : connection === 'polling'
                    ? 'bg-amber-400'
                    : 'bg-white/40',
              )}
              aria-hidden
            />
            {CONNECTION_LABEL[connection]}
          </span>
        </p>
      </section>

      {terminalProblem && (
        <p
          role="status"
          className="rounded-2xl border border-destructive/20 bg-destructive/5 p-4 text-sm text-destructive"
        >
          {t.status === 'FAILED'
            ? 'Delivery was unsuccessful. The sender has been notified.'
            : 'This delivery was cancelled.'}
        </p>
      )}

      {/* Route and Progress side-by-side on tablet+ */}
      <div className="grid gap-4 sm:grid-cols-2">
        <section aria-label="Route" className="rounded-3xl border bg-card p-5">
          <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Route</p>
          <p className="mt-1 text-lg font-semibold tracking-tight">
            {t.originCity} <span aria-label="to">→</span> {t.destinationCity}
          </p>
          <dl className="mt-3 flex flex-col gap-1 text-sm">
            {t.eta && !terminalProblem && t.status !== 'DELIVERED' && (
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Estimated delivery</dt>
                <dd className="text-right font-medium">{formatDateTime(t.eta)}</dd>
              </div>
            )}
            {t.courierFirstName && (
              <div className="flex justify-between gap-4">
                <dt className="text-muted-foreground">Courier</dt>
                <dd className="text-right font-medium">{t.courierFirstName}</dd>
              </div>
            )}
          </dl>
        </section>

        <section aria-label="Progress" className="rounded-3xl border bg-card p-5">
          <h2 className="mb-3 text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Progress
          </h2>
          <StatusTimeline items={items} />
        </section>
      </div>

      <footer className="flex flex-col gap-3 sm:flex-row">
        <Button
          variant="outline"
          className="h-11 flex-1"
          onClick={async () => {
            if ((await shareLink(trackingUrl(t.trackingNumber))) === 'copied')
              toast.success('Link copied')
          }}
        >
          <Share2 className="h-4 w-4" aria-hidden /> Share
        </Button>
        <Button asChild variant="outline" className="h-11 flex-1">
          <Link to="/track">Track another</Link>
        </Button>
      </footer>
    </article>
  )
}
