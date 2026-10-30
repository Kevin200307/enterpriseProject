import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { isApiError } from '@/api/ApiError'
import { dispatchApi } from '@/api/endpoints/dispatch'
import { pricingApi } from '@/api/endpoints/pricing'
import { describeError } from '@/api/errorMessages'
import { qk } from '@/api/queryKeys'
import type { AssignmentCommand, QueueItem } from '@/api/types'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { useInView } from '@/hooks/useInView'
import { ageTone, timeAgo } from '@/lib/format'
import { cn } from '@/lib/cn'
import { CourierPicker } from './CourierPicker'
import { WorkloadPanel } from './WorkloadPanel'

const TONE = {
  ok: 'bg-muted text-muted-foreground',
  warn: 'bg-amber-100 text-amber-900',
  late: 'bg-red-100 text-red-900',
}

interface Target {
  item: QueueItem
  courierId: string
}

function SuggestChips({
  item,
  disabled,
  onPick,
}: {
  item: QueueItem
  disabled: boolean
  onPick: (courierId: string) => void
}) {
  const [ref, seen] = useInView<HTMLDivElement>()
  const q = useQuery({
    queryKey: qk.dispatch.suggest(item.parcelId),
    queryFn: () => dispatchApi.suggest(item.parcelId),
    enabled: seen,
    staleTime: 60_000,
  })
  return (
    <div
      ref={ref}
      className="flex flex-wrap gap-2"
      aria-label={`Suggested couriers for ${item.trackingNumber}`}
    >
      {q.isPending && seen && <Skeleton className="h-8 w-24" />}
      {q.data?.map((s) => (
        <button
          key={s.courierId}
          type="button"
          disabled={disabled}
          title={s.reason}
          onClick={() => onPick(s.courierId)}
          className="rounded-full border px-3 py-1 text-xs font-medium hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50"
        >
          {s.name} · {s.remainingCapacity} free
        </button>
      ))}
      {q.data?.length === 0 && (
        <span className="text-xs text-muted-foreground">No suitable courier</span>
      )}
    </div>
  )
}

export function DispatchBoardPage() {
  const qc = useQueryClient()
  const [zone, setZone] = useState('')
  const [picking, setPicking] = useState<QueueItem>()
  const [conflict, setConflict] = useState<Target>()
  const [pendingCommandId, setPendingCommandId] = useState<string | null>(null)

  // Poll an async command if the assign returned 202 instead of 200.
  const commandPoll = useQuery({
    queryKey: qk.dispatch.command(pendingCommandId ?? ''),
    queryFn: () => dispatchApi.getCommand(pendingCommandId!),
    enabled: pendingCommandId !== null,
    refetchInterval: (q) => {
      if (!q.state.data) return 2000
      return q.state.data.state === 'PENDING' ? 2000 : false
    },
  })
  useEffect(() => {
    if (!commandPoll.data) return
    if (commandPoll.data.state === 'DONE') {
      setPendingCommandId(null)
      toast.success('Assignment confirmed')
      void qc.invalidateQueries({ queryKey: qk.dispatch.all })
    } else if (commandPoll.data.state === 'FAILED') {
      setPendingCommandId(null)
      toast.error(`Assignment failed${commandPoll.data.detail ? `: ${commandPoll.data.detail}` : ''}`)
    }
  }, [commandPoll.data?.state])

  const zones = useQuery({
    queryKey: qk.pricing.zones,
    queryFn: () => pricingApi.zones(),
    staleTime: 5 * 60_000,
  })
  const queue = useQuery({
    queryKey: qk.dispatch.queue(zone),
    queryFn: () => dispatchApi.queue(zone || undefined),
    refetchInterval: 15_000, // paused automatically while the tab is hidden
  })

  const assign = useMutation<AssignmentCommand | undefined, unknown, Target & { reassign?: boolean }>({
    mutationFn: ({ item, courierId, reassign }) =>
      dispatchApi.assign(item.parcelId, courierId, reassign),
    onSuccess: (cmd, v) => {
      setPicking(undefined)
      setConflict(undefined)
      if (cmd?.state === 'PENDING') {
        setPendingCommandId(cmd.id)
        toast.info(`${v.item.trackingNumber} assignment in progress…`)
      } else {
        toast.success(`${v.item.trackingNumber} assigned`)
        void qc.invalidateQueries({ queryKey: qk.dispatch.all })
      }
    },
    onError: (e, v) => {
      if (isApiError(e) && e.errorCode === 'CONFLICT' && !v.reassign) {
        setPicking(undefined)
        return setConflict({ item: v.item, courierId: v.courierId })
      }
      toast.error(describeError(e).message)
    },
  })

  const order = [...(queue.data ?? [])].sort((a, b) => a.createdAt.localeCompare(b.createdAt))

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Dispatch board"
        description="Unassigned parcels, oldest first. Refreshes every 15 seconds."
      />
      <div className="grid gap-6 lg:grid-cols-[1fr_300px]">
        <section className="flex min-w-0 flex-col gap-4" aria-label="Unassigned parcels">
          <div className="flex items-end gap-3">
            <div className="flex flex-col gap-1">
              <Label htmlFor="zone-filter" className="text-xs">
                Pickup zone
              </Label>
              <Select id="zone-filter" value={zone} onChange={(e) => setZone(e.target.value)}>
                <option value="">All zones</option>
                {zones.data?.map((z) => (
                  <option key={z.id} value={z.code}>
                    {z.name}
                  </option>
                ))}
              </Select>
            </div>
          </div>

          {queue.isPending ? (
            <div aria-busy="true" className="flex flex-col gap-3">
              {[0, 1, 2].map((i) => (
                <Skeleton key={i} className="h-28 w-full" />
              ))}
            </div>
          ) : queue.isError ? (
            <ErrorState error={queue.error} onRetry={() => void queue.refetch()} />
          ) : order.length === 0 ? (
            <EmptyState title="All caught up" description="No parcels are waiting for a courier." />
          ) : (
            <ul className="flex flex-col gap-3">
              {order.map((item) => (
                <li key={item.parcelId} className="flex flex-col gap-3 rounded-lg border p-4">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="font-mono text-sm font-semibold">{item.trackingNumber}</span>
                    <span
                      className={cn(
                        'rounded-full px-2.5 py-1 text-xs font-medium',
                        TONE[ageTone(item.createdAt)],
                      )}
                    >
                      Waiting {timeAgo(item.createdAt).replace(' ago', '')}
                    </span>
                  </div>
                  <p className="text-sm">
                    Pickup {item.pickupCity} · {item.size} · {item.weightKg} kg
                  </p>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <SuggestChips
                      item={item}
                      disabled={assign.isPending}
                      onPick={(courierId) => assign.mutate({ item, courierId })}
                    />
                    <Button size="sm" variant="outline" onClick={() => setPicking(item)}>
                      Assign…
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <Card className="h-fit">
          <CardHeader>
            <CardTitle>Courier capacity</CardTitle>
          </CardHeader>
          <CardContent>
            <WorkloadPanel />
          </CardContent>
        </Card>
      </div>

      <CourierPicker
        open={!!picking}
        trackingNumber={picking?.trackingNumber ?? ''}
        busy={assign.isPending}
        onClose={() => setPicking(undefined)}
        onPick={(courierId) => picking && assign.mutate({ item: picking, courierId })}
      />

      <Dialog
        open={!!conflict}
        onOpenChange={(o) => !o && setConflict(undefined)}
        title="Already assigned"
        description={`${conflict?.item.trackingNumber ?? 'This parcel'} is assigned to another courier. Reassign it?`}
      >
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={() => setConflict(undefined)}>
            Keep current courier
          </Button>
          <Button
            disabled={assign.isPending}
            onClick={() => conflict && assign.mutate({ ...conflict, reassign: true })}
          >
            Reassign
          </Button>
        </div>
      </Dialog>
    </div>
  )
}
