import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { toast } from 'sonner'
import { describeError } from '@/api/errorMessages'
import { couriersApi } from '@/api/endpoints/couriers'
import { dispatchApi } from '@/api/endpoints/dispatch'
import { qk } from '@/api/queryKeys'
import type { Assignment, AssignmentCommand, WorkloadRow } from '@/api/types'
import { ErrorState } from '@/components/common/ErrorState'
import { StatusChip } from '@/components/common/StatusChip'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'
import type { ParcelStatus } from '@/lib/status'

const TERMINAL_STATUSES: ParcelStatus[] = ['DELIVERED', 'FAILED', 'CANCELLED']
const isActive = (status: ParcelStatus) => !TERMINAL_STATUSES.includes(status)

/** Polls a dispatch command until it reaches a terminal state, then fires a callback. */
function useCommandPoller(
  commandId: string | null,
  onDone: () => void,
  onFailed: (detail?: string) => void,
) {
  const q = useQuery({
    queryKey: qk.dispatch.command(commandId ?? ''),
    queryFn: () => dispatchApi.getCommand(commandId!),
    enabled: commandId !== null,
    refetchInterval: (q) => {
      if (!q.state.data) return 2000
      return q.state.data.state === 'PENDING' ? 2000 : false
    },
  })
  useEffect(() => {
    if (!q.data) return
    if (q.data.state === 'DONE') onDone()
    else if (q.data.state === 'FAILED') onFailed(q.data.detail)
  }, [q.data?.state])
}

export function CourierManifestSheet({
  courier,
  date,
  onClose,
}: {
  courier: WorkloadRow | null
  date: string
  onClose: () => void
}) {
  const qc = useQueryClient()
  const [unassignTarget, setUnassignTarget] = useState<Assignment | null>(null)
  const [reason, setReason] = useState('')
  const [pendingCommandId, setPendingCommandId] = useState<string | null>(null)

  const manifest = useQuery({
    queryKey: qk.dispatch.manifest(courier?.courierId ?? '', date),
    queryFn: () => couriersApi.manifest(courier!.courierId, date),
    enabled: courier !== null,
  })

  const unassign = useMutation<AssignmentCommand | undefined, unknown, Assignment>({
    mutationFn: (a) => dispatchApi.unassign(a.id, reason.trim()),
    onSuccess: (cmd, a) => {
      setUnassignTarget(null)
      setReason('')
      if (cmd?.state === 'PENDING') {
        setPendingCommandId(cmd.id)
        toast.info(`Unassignment for ${a.trackingNumber} is in progress…`)
      } else {
        toast.success(`${a.trackingNumber} unassigned`)
        void qc.invalidateQueries({ queryKey: qk.dispatch.all })
      }
    },
    onError: (e) => toast.error(describeError(e).message),
  })

  useCommandPoller(
    pendingCommandId,
    () => {
      setPendingCommandId(null)
      toast.success('Unassignment complete')
      void qc.invalidateQueries({ queryKey: qk.dispatch.all })
      void manifest.refetch()
    },
    (detail) => {
      setPendingCommandId(null)
      toast.error(`Unassignment failed${detail ? `: ${detail}` : ''}`)
    },
  )

  const open = courier !== null

  return (
    <>
      <Dialog
        open={open}
        onOpenChange={(o) => !o && onClose()}
        title={courier ? `${courier.name}'s assignments` : ''}
        description={`Today's active and completed deliveries — ${formatDate(new Date().toISOString())}`}
        side="bottom"
      >
        {manifest.isPending ? (
          <div className="flex flex-col gap-2" aria-busy="true">
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-16 w-full" />
          </div>
        ) : manifest.isError ? (
          <ErrorState error={manifest.error} onRetry={() => void manifest.refetch()} />
        ) : manifest.data.length === 0 ? (
          <p className="py-4 text-center text-sm text-muted-foreground">
            No assignments for today.
          </p>
        ) : (
          <ul className="flex flex-col gap-2">
            {manifest.data.map((a) => (
              <li
                key={a.id}
                className="flex items-center justify-between gap-3 rounded-lg border p-3"
              >
                <div className="min-w-0 flex-1">
                  <p className="font-mono text-sm font-semibold">{a.trackingNumber}</p>
                  <p className="truncate text-xs text-muted-foreground">
                    {a.pickup.city} → {a.dropoff.city} · {a.size}
                  </p>
                </div>
                <div className="flex shrink-0 items-center gap-2">
                  <StatusChip status={a.status} />
                  {isActive(a.status) && (
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => {
                        setReason('')
                        setUnassignTarget(a)
                      }}
                    >
                      Unassign
                    </Button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </Dialog>

      <Dialog
        open={unassignTarget !== null}
        onOpenChange={(o) => !o && setUnassignTarget(null)}
        title="Unassign courier?"
        description={`Parcel ${unassignTarget?.trackingNumber ?? ''} will return to the dispatch queue.`}
      >
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="unassign-reason">Reason</Label>
            <Input
              id="unassign-reason"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. Courier unavailable"
              autoComplete="off"
            />
          </div>
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => setUnassignTarget(null)}>
              Cancel
            </Button>
            <Button
              variant="destructive"
              disabled={!reason.trim() || unassign.isPending}
              onClick={() => unassignTarget && unassign.mutate(unassignTarget)}
            >
              {unassign.isPending ? 'Unassigning…' : 'Unassign'}
            </Button>
          </div>
        </div>
      </Dialog>
    </>
  )
}
