import { Phone } from 'lucide-react'
import type { Assignment } from '@/api/types'
import { StatusChip } from '@/components/common/StatusChip'
import { Button } from '@/components/ui/button'
import { COURIER_ACTIONS, type CourierAction } from '@/lib/transitions'

/** Before pickup the courier works from the pickup address; afterwards from the drop-off. */
export function AssignmentCard({
  a,
  busy,
  onAction,
}: {
  a: Assignment
  busy: boolean
  onAction: (a: Assignment, action: CourierAction) => void
}) {
  const beforePickup = a.status === 'CREATED'
  const place = beforePickup ? a.pickup : a.dropoff
  const actions = COURIER_ACTIONS[a.status]
  return (
    <li className="flex flex-col gap-3 rounded-xl border bg-card p-4 shadow-sm">
      <div className="flex items-start justify-between gap-2">
        <span className="font-mono text-base font-semibold">{a.trackingNumber}</span>
        <StatusChip status={a.status} />
      </div>
      <div>
        <p className="text-xs uppercase tracking-wide text-muted-foreground">
          {beforePickup ? 'Pick up from' : 'Deliver to'}
        </p>
        <p className="font-medium">{place.contactName}</p>
        <p className="text-sm">
          {place.line1}
          {place.line2 ? `, ${place.line2}` : ''}, {place.city}
        </p>
        <a
          href={`tel:${place.phone.replace(/[^\d+]/g, '')}`}
          className="mt-1 inline-flex min-h-11 items-center gap-2 text-sm font-medium text-primary underline-offset-4 hover:underline"
        >
          <Phone className="h-4 w-4" aria-hidden /> {place.phone}
        </a>
      </div>
      <p className="text-xs text-muted-foreground">
        {a.size} · {a.weightKg} kg
      </p>
      {actions && (
        <div className="flex flex-col gap-2">
          <Button
            size="lg"
            className="h-14 text-base"
            disabled={busy}
            onClick={() => onAction(a, actions.primary)}
          >
            {actions.primary.label}
          </Button>
          {actions.secondary && (
            <Button
              size="lg"
              variant="outline"
              className="h-12"
              disabled={busy}
              onClick={() => onAction(a, actions.secondary!)}
            >
              {actions.secondary.label}
            </Button>
          )}
        </div>
      )}
    </li>
  )
}
