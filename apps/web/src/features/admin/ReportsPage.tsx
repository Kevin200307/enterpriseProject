import { useQuery } from '@tanstack/react-query'
import { Download } from 'lucide-react'
import { useSearchParams } from 'react-router-dom'
import { dispatchApi } from '@/api/endpoints/dispatch'
import { qk } from '@/api/queryKeys'
import type { DailyStat } from '@/api/types'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { downloadCsv, statsCsv } from '@/lib/csv'

const iso = (d: Date) => d.toISOString().slice(0, 10)
const daysAgo = (n: number) => iso(new Date(Date.now() - n * 86_400_000))

/** Stacked bars drawn in CSS: no chart library, and the table below is the accessible source of truth. */
function Bars({ rows }: { rows: DailyStat[] }) {
  const max = Math.max(
    1,
    ...rows.map((r) => r.created, ...[]),
    ...rows.map((r) => r.completed + r.failed),
  )
  return (
    <div
      role="img"
      aria-label="Daily created, completed and failed parcels"
      className="flex h-48 items-end gap-1 overflow-x-auto rounded-lg border p-4"
    >
      {rows.map((r) => (
        <div
          key={r.date}
          className="flex h-full min-w-6 flex-1 flex-col justify-end"
          title={`${r.date}: ${r.created} created, ${r.completed} completed, ${r.failed} failed`}
        >
          <div
            className="flex w-full flex-col justify-end"
            style={{ height: `${(Math.max(r.created, r.completed + r.failed) / max) * 100}%` }}
          >
            <div className="bg-red-500" style={{ flexGrow: r.failed }} />
            <div className="bg-green-500" style={{ flexGrow: r.completed }} />
            <div
              className="bg-primary/30"
              style={{ flexGrow: Math.max(0, r.created - r.completed - r.failed) }}
            />
          </div>
        </div>
      ))}
    </div>
  )
}

export function ReportsPage() {
  const [params, setParams] = useSearchParams()
  const from = params.get('from') ?? daysAgo(13)
  const to = params.get('to') ?? daysAgo(0)
  const q = useQuery({
    queryKey: qk.admin.stats({ from, to }),
    queryFn: () => dispatchApi.dailyStats(from, to),
  })

  const set = (k: 'from' | 'to', v: string) => {
    const next = new URLSearchParams(params)
    next.set('from', k === 'from' ? v : from)
    next.set('to', k === 'to' ? v : to)
    setParams(next)
  }
  const totals = (q.data ?? []).reduce(
    (t, r) => ({
      created: t.created + r.created,
      completed: t.completed + r.completed,
      failed: t.failed + r.failed,
    }),
    { created: 0, completed: 0, failed: 0 },
  )

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Daily report"
        actions={
          <Button
            variant="outline"
            disabled={!q.data?.length}
            onClick={() =>
              downloadCsv(`swiftdrop-daily-${from}-to-${to}.csv`, statsCsv(q.data ?? []))
            }
          >
            <Download className="h-4 w-4" aria-hidden /> Export CSV
          </Button>
        }
      />
      <div className="flex flex-wrap gap-3">
        <div className="flex flex-col gap-1">
          <Label htmlFor="r-from" className="text-xs">
            From
          </Label>
          <Input
            id="r-from"
            type="date"
            value={from}
            max={to}
            onChange={(e) => set('from', e.target.value)}
          />
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="r-to" className="text-xs">
            To
          </Label>
          <Input
            id="r-to"
            type="date"
            value={to}
            min={from}
            onChange={(e) => set('to', e.target.value)}
          />
        </div>
      </div>

      {q.isPending ? (
        <Skeleton className="h-64 w-full" />
      ) : q.isError ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : q.data.length === 0 ? (
        <EmptyState title="No activity in this range" />
      ) : (
        <>
          <Bars rows={q.data} />
          <p className="flex flex-wrap gap-4 text-xs text-muted-foreground">
            <span>
              <span className="mr-1 inline-block h-2 w-2 bg-primary/30" aria-hidden />
              Created (open)
            </span>
            <span>
              <span className="mr-1 inline-block h-2 w-2 bg-green-500" aria-hidden />
              Completed
            </span>
            <span>
              <span className="mr-1 inline-block h-2 w-2 bg-red-500" aria-hidden />
              Failed
            </span>
          </p>
          <div className="overflow-x-auto rounded-lg border">
            <table className="w-full text-sm">
              <thead className="bg-muted text-left">
                <tr>
                  {['Date', 'Created', 'Completed', 'Failed'].map((h) => (
                    <th key={h} scope="col" className="p-3 font-medium">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {q.data.map((r) => (
                  <tr key={r.date} className="border-t">
                    <th scope="row" className="p-3 text-left font-normal">
                      {r.date}
                    </th>
                    <td className="p-3">{r.created}</td>
                    <td className="p-3">{r.completed}</td>
                    <td className="p-3">{r.failed}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot className="border-t bg-muted font-semibold">
                <tr>
                  <th scope="row" className="p-3 text-left">
                    Total
                  </th>
                  <td className="p-3">{totals.created}</td>
                  <td className="p-3">{totals.completed}</td>
                  <td className="p-3">{totals.failed}</td>
                </tr>
              </tfoot>
            </table>
          </div>
        </>
      )}
    </div>
  )
}
