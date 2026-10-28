import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { isApiError } from '@/api/ApiError'
import { parcelsApi } from '@/api/endpoints/parcels'
import { describeError } from '@/api/errorMessages'
import { qk } from '@/api/queryKeys'
import type { Parcel } from '@/api/types'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { MoneyLKR } from '@/components/common/MoneyLKR'
import { PageHeader } from '@/components/common/PageHeader'
import { Pagination } from '@/components/common/Pagination'
import { StatusChip } from '@/components/common/StatusChip'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select } from '@/components/ui/select'
import { Skeleton } from '@/components/ui/skeleton'
import { TRACKING_NUMBER_RE, formatDate } from '@/lib/format'
import { STATUS_META, statusMeta, type ParcelStatus } from '@/lib/status'
import { allowedNext } from '@/lib/transitions'

function StatusDialog({ parcel, onClose }: { parcel: Parcel; onClose: () => void }) {
  const qc = useQueryClient()
  const options = allowedNext(parcel.status)
  const [to, setTo] = useState<ParcelStatus | ''>(options[0] ?? '')
  const [reason, setReason] = useState('')
  const [touched, setTouched] = useState(false)
  const needsReason = to === 'FAILED'
  const save = useMutation({
    mutationFn: () =>
      parcelsApi.updateStatus(parcel.id, {
        status: to as ParcelStatus,
        failedReason: needsReason ? reason.trim() : undefined,
      }),
    onSuccess: () => {
      toast.success(`${parcel.trackingNumber} is now ${statusMeta(to).label.toLowerCase()}`)
      onClose()
    },
    onError: (e) => toast.error(describeError(e).message),
    onSettled: () => void qc.invalidateQueries({ queryKey: qk.admin.all }),
  })
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={`Change status of ${parcel.trackingNumber}`}
      description={`Currently ${statusMeta(parcel.status).label}.`}
    >
      {options.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          This parcel is in a final state and can&apos;t be changed.
        </p>
      ) : (
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="to-status">New status</Label>
            <Select
              id="to-status"
              value={to}
              onChange={(e) => setTo(e.target.value as ParcelStatus)}
            >
              {options.map((o) => (
                <option key={o} value={o}>
                  {STATUS_META[o].label}
                </option>
              ))}
            </Select>
          </div>
          {needsReason && (
            <div className="flex flex-col gap-2">
              <Label htmlFor="fail-reason">Failure reason</Label>
              <Input id="fail-reason" value={reason} onChange={(e) => setReason(e.target.value)} />
              {touched && !reason.trim() && (
                <p role="alert" className="text-sm text-destructive">
                  A reason is required.
                </p>
              )}
            </div>
          )}
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button
              disabled={save.isPending || !to}
              onClick={() => (needsReason && !reason.trim() ? setTouched(true) : save.mutate())}
            >
              {save.isPending ? 'Saving…' : 'Update status'}
            </Button>
          </div>
        </div>
      )}
    </Dialog>
  )
}

export function AdminParcelsPage() {
  const [params, setParams] = useSearchParams()
  const status = params.get('status') ?? ''
  const tn = (params.get('tn') ?? '').toUpperCase()
  const page = Math.max(0, Number(params.get('page')) || 0)
  const [draft, setDraft] = useState(tn)
  const [searchError, setSearchError] = useState<string>()
  const [editing, setEditing] = useState<Parcel>()

  const single = useQuery({
    queryKey: qk.admin.parcels({ tn }),
    queryFn: () => parcelsApi.byTracking(tn),
    enabled: !!tn,
    retry: false,
  })
  const list = useQuery({
    queryKey: qk.admin.parcels({ status, page }),
    queryFn: () => parcelsApi.list({ status: status ? [status] : undefined, page }),
    enabled: !tn,
    placeholderData: (prev) => prev,
  })
  const rows: Parcel[] = tn ? (single.data ? [single.data] : []) : (list.data?.content ?? [])
  const active = tn ? single : list

  const submitSearch = (e: React.FormEvent) => {
    e.preventDefault()
    const v = draft.trim().toUpperCase()
    if (v && !TRACKING_NUMBER_RE.test(v))
      return setSearchError('Tracking numbers look like SD-ABCD2345.')
    setSearchError(undefined)
    const next = new URLSearchParams(params)
    if (v) next.set('tn', v)
    else next.delete('tn')
    next.delete('page')
    setParams(next)
  }
  const notFound = tn && single.isError && isApiError(single.error) && single.error.status === 404

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="All parcels" />
      <div className="flex flex-wrap items-end gap-3">
        <form onSubmit={submitSearch} noValidate className="flex items-end gap-2">
          <div className="flex flex-col gap-1">
            <Label htmlFor="p-tn" className="text-xs">
              Tracking number
            </Label>
            <Input
              id="p-tn"
              value={draft}
              onChange={(e) => setDraft(e.target.value.toUpperCase())}
              placeholder="SD-XXXXXXXX"
              className="font-mono"
            />
          </div>
          <Button type="submit" variant="secondary">
            Search
          </Button>
          {tn && (
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                setDraft('')
                setParams(new URLSearchParams())
              }}
            >
              Clear
            </Button>
          )}
        </form>
        {!tn && (
          <div className="flex flex-col gap-1">
            <Label htmlFor="p-status" className="text-xs">
              Status
            </Label>
            <Select
              id="p-status"
              value={status}
              onChange={(e) => {
                const n = new URLSearchParams()
                if (e.target.value) n.set('status', e.target.value)
                setParams(n)
              }}
            >
              <option value="">All</option>
              {(Object.keys(STATUS_META) as ParcelStatus[]).map((s) => (
                <option key={s} value={s}>
                  {STATUS_META[s].label}
                </option>
              ))}
            </Select>
          </div>
        )}
      </div>
      {searchError && (
        <p role="alert" className="text-sm text-destructive">
          {searchError}
        </p>
      )}

      {active.isPending && (tn || !list.data) ? (
        <Skeleton className="h-48 w-full" />
      ) : notFound ? (
        <EmptyState title="No parcel with that tracking number" />
      ) : active.isError ? (
        <ErrorState error={active.error} onRetry={() => void active.refetch()} />
      ) : rows.length === 0 ? (
        <EmptyState title="No parcels match" />
      ) : (
        <>
          <ul className="flex flex-col gap-3">
            {rows.map((p) => (
              <li
                key={p.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-4"
              >
                <div className="min-w-0">
                  <p className="font-mono text-sm font-semibold">{p.trackingNumber}</p>
                  <p className="text-sm text-muted-foreground">
                    {p.pickup.city} → {p.dropoff.city} · {formatDate(p.createdAt)} ·{' '}
                    <MoneyLKR value={p.fee} />
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  <StatusChip status={p.status} />
                  <Button
                    size="sm"
                    variant="outline"
                    aria-label={`Change status of ${p.trackingNumber}`}
                    onClick={() => setEditing(p)}
                  >
                    Change status
                  </Button>
                </div>
              </li>
            ))}
          </ul>
          {!tn && list.data && (
            <Pagination
              page={list.data.number}
              totalPages={list.data.totalPages}
              onChange={(n) => {
                const next = new URLSearchParams(params)
                next.set('page', String(n))
                setParams(next)
              }}
            />
          )}
        </>
      )}
      {editing && <StatusDialog parcel={editing} onClose={() => setEditing(undefined)} />}
    </div>
  )
}
