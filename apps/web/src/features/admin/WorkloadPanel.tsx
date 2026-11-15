import type { WorkloadRow } from '@/api/types'
import { ErrorState } from '@/components/common/ErrorState'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/cn'
import { useWorkload } from './useWorkload'

export function CapacityBar({ used, free }: { used: number; free: number }) {
  const total = used + free
  const pct = total > 0 ? Math.min(100, Math.round((used / total) * 100)) : 100
  return (
    <div
      role="meter"
      aria-label="Load"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={pct}
      className="h-2 w-full overflow-hidden rounded-full bg-muted"
    >
      <div
        className={cn(
          'h-full',
          pct >= 100 ? 'bg-red-500' : pct >= 75 ? 'bg-amber-500' : 'bg-green-500',
        )}
        style={{ width: `${pct}%` }}
      />
    </div>
  )
}

/** Compact list used beside the dispatch board so capacity is visible while assigning. */
export function WorkloadPanel({ rows }: { rows?: WorkloadRow[] }) {
  const q = useWorkload()
  const data = rows ?? q.data
  if (!data && q.isPending) return <Skeleton className="h-40 w-full" />
  if (!data) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />
  return (
    <ul className="flex flex-col gap-3" aria-label="Courier capacity">
      {data.map((r) => (
        <li
          key={r.courierId}
          className={cn('flex flex-col gap-1 text-sm', !r.active && 'opacity-50')}
        >
          <div className="flex justify-between">
            <span className="font-medium">{r.name}</span>
            <span className="text-muted-foreground">
              {r.active ? `${r.remainingCapacity} free` : 'Inactive'}
            </span>
          </div>
          <CapacityBar used={r.activeAssignments} free={r.remainingCapacity} />
        </li>
      ))}
    </ul>
  )
}
