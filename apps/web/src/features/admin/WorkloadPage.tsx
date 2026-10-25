import { useMemo, useState } from 'react'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { PageHeader } from '@/components/common/PageHeader'
import { Skeleton } from '@/components/ui/skeleton'
import type { WorkloadRow } from '@/api/types'
import { CapacityBar } from './WorkloadPanel'
import { useWorkload } from './useWorkload'

type SortKey = 'name' | 'activeAssignments' | 'deliveredToday' | 'failedToday' | 'remainingCapacity'
const COLUMNS: { key: SortKey; label: string }[] = [
  { key: 'name', label: 'Courier' },
  { key: 'activeAssignments', label: 'Active' },
  { key: 'deliveredToday', label: 'Delivered today' },
  { key: 'failedToday', label: 'Failed today' },
  { key: 'remainingCapacity', label: 'Capacity left' },
]

export function WorkloadPage() {
  const q = useWorkload()
  const [sort, setSort] = useState<{ key: SortKey; dir: 1 | -1 }>({
    key: 'activeAssignments',
    dir: -1,
  })
  const rows = useMemo(
    () =>
      [...(q.data ?? [])].sort((a: WorkloadRow, b: WorkloadRow) => {
        const x = a[sort.key]
        const y = b[sort.key]
        return (
          (typeof x === 'string' ? x.localeCompare(y as string) : (x as number) - (y as number)) *
          sort.dir
        )
      }),
    [q.data, sort],
  )

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Courier workload" description="Today's load per courier." />
      {q.isPending ? (
        <Skeleton className="h-64 w-full" />
      ) : q.isError ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : rows.length === 0 ? (
        <EmptyState title="No couriers yet" />
      ) : (
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full text-sm">
            <thead className="bg-muted text-left">
              <tr>
                {COLUMNS.map((c) => (
                  <th
                    key={c.key}
                    scope="col"
                    aria-sort={
                      sort.key === c.key ? (sort.dir === 1 ? 'ascending' : 'descending') : 'none'
                    }
                    className="p-3 font-medium"
                  >
                    <button
                      type="button"
                      className="font-medium hover:underline"
                      onClick={() =>
                        setSort((s) => ({
                          key: c.key,
                          dir: s.key === c.key ? (s.dir === 1 ? -1 : 1) : -1,
                        }))
                      }
                    >
                      {c.label}
                    </button>
                  </th>
                ))}
                <th scope="col" className="w-40 p-3 font-medium">
                  Load
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.courierId} className={`border-t ${r.active ? '' : 'opacity-50'}`}>
                  <th scope="row" className="p-3 text-left font-medium">
                    {r.name}
                    {!r.active && (
                      <span className="ml-2 text-xs text-muted-foreground">(inactive)</span>
                    )}
                  </th>
                  <td className="p-3">{r.activeAssignments}</td>
                  <td className="p-3">{r.deliveredToday}</td>
                  <td className="p-3">{r.failedToday}</td>
                  <td className="p-3">{r.remainingCapacity}</td>
                  <td className="p-3">
                    <CapacityBar used={r.activeAssignments} free={r.remainingCapacity} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
