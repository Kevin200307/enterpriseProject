import { useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ErrorState } from '@/components/common/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/cn'
import { useWorkload } from './useWorkload'

/** Searchable courier list; inactive or full couriers are shown but cannot be chosen. */
export function CourierPicker({
  open,
  trackingNumber,
  busy,
  onClose,
  onPick,
}: {
  open: boolean
  trackingNumber: string
  busy: boolean
  onClose: () => void
  onPick: (courierId: string) => void
}) {
  const [term, setTerm] = useState('')
  const q = useWorkload()
  const rows = useMemo(
    () => (q.data ?? []).filter((r) => r.name.toLowerCase().includes(term.trim().toLowerCase())),
    [q.data, term],
  )
  return (
    <Dialog
      open={open}
      onOpenChange={(o) => !o && onClose()}
      title={`Assign ${trackingNumber}`}
      description="Choose a courier."
    >
      <div className="flex flex-col gap-3">
        <Label htmlFor="courier-search">Search couriers</Label>
        <Input
          id="courier-search"
          value={term}
          onChange={(e) => setTerm(e.target.value)}
          autoComplete="off"
        />
        {q.isPending ? (
          <Skeleton className="h-32 w-full" />
        ) : q.isError ? (
          <ErrorState error={q.error} onRetry={() => void q.refetch()} />
        ) : (
          <ul className="flex max-h-72 flex-col gap-2 overflow-y-auto">
            {rows.map((r) => {
              const blocked = !r.active || r.remainingCapacity <= 0
              return (
                <li key={r.courierId}>
                  <button
                    type="button"
                    disabled={blocked || busy}
                    onClick={() => onPick(r.courierId)}
                    className={cn(
                      'flex w-full items-center justify-between rounded-md border p-3 text-left text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                      blocked ? 'cursor-not-allowed opacity-50' : 'hover:bg-accent',
                    )}
                  >
                    <span className="font-medium">{r.name}</span>
                    <span className="text-muted-foreground">
                      {!r.active
                        ? 'Inactive'
                        : r.remainingCapacity <= 0
                          ? 'Full'
                          : `${r.remainingCapacity} free`}
                    </span>
                  </button>
                </li>
              )
            })}
            {rows.length === 0 && (
              <li className="p-3 text-sm text-muted-foreground">No couriers match.</li>
            )}
          </ul>
        )}
        <Button variant="outline" onClick={onClose}>
          Cancel
        </Button>
      </div>
    </Dialog>
  )
}
