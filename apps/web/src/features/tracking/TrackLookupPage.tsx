import { useQuery } from '@tanstack/react-query'
import { ChevronRight } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { parcelsApi } from '@/api/endpoints/parcels'
import { qk } from '@/api/queryKeys'
import { useSession } from '@/auth/session'
import { StatusChip } from '@/components/common/StatusChip'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { PageHeader } from '@/components/common/PageHeader'
import { TRACKING_NUMBER_RE } from '@/lib/format'

export function TrackLookupPage() {
  const [value, setValue] = useState('')
  const [error, setError] = useState<string>()
  const navigate = useNavigate()

  function submit(e: React.FormEvent) {
    e.preventDefault()
    const tn = value.trim().toUpperCase()
    if (!TRACKING_NUMBER_RE.test(tn))
      return setError("That doesn't look like a SwiftDrop tracking number (SD-XXXXXXXX).")
    navigate(`/track/${tn}`)
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6 px-2">
      <PageHeader title="Track a parcel" description="Enter the tracking number you were given." />
      <form onSubmit={submit} noValidate className="flex flex-col gap-3">
        <label htmlFor="tn" className="text-sm font-medium">
          Tracking number
        </label>
        <Input
          id="tn"
          value={value}
          onChange={(e) => {
            setValue(e.target.value.toUpperCase())
            setError(undefined)
          }}
          placeholder="SD-XXXXXXXX"
          autoCapitalize="characters"
          autoComplete="off"
          aria-invalid={!!error}
          aria-describedby={error ? 'tn-error' : undefined}
          className="h-12 font-mono text-lg"
        />
        {error && (
          <p id="tn-error" role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        <Button type="submit" size="lg">
          Track
        </Button>
      </form>

      <ActiveDeliveries />
    </div>
  )
}

/**
 * Signed in, the screen's dead space below the form is better spent on the parcels you are
 * actually waiting for — one tap beats typing a tracking number you have to find first.
 */
function ActiveDeliveries() {
  const { roles } = useSession()
  const signedIn = roles.length > 0
  const q = useQuery({
    queryKey: qk.parcels.list({ page: 0 }),
    queryFn: () => parcelsApi.list({ page: 0 }),
    enabled: signedIn,
  })

  if (!signedIn) return null
  if (q.isPending) return <Skeleton className="h-24 w-full rounded-2xl" />
  if (q.isError) return null

  const active = q.data.content.filter((p) => p.status !== 'DELIVERED' && p.status !== 'CANCELLED')
  if (active.length === 0) return null

  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-lg font-semibold tracking-tight">On the way</h2>
      <ul className="grid gap-3 md:grid-cols-2">
        {active.map((p) => (
          <li key={p.id}>
            {/* Straight into tracking, which is what the screen is for — not the booking detail. */}
            <Link
              to={`/track/${p.trackingNumber}`}
              className="relative flex items-center gap-3 rounded-2xl border bg-card p-4 pr-32 transition-colors hover:bg-secondary"
            >
              <div className="min-w-0 flex-1">
                <p className="truncate font-mono text-sm font-medium">{p.trackingNumber}</p>
                <p className="mt-0.5 truncate text-xs text-muted-foreground">
                  {p.pickup.city} → {p.dropoff.city} · {formatDate(p.createdAt)}
                </p>
              </div>
              <StatusChip status={p.status} className="absolute right-10 top-4" />
              <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
