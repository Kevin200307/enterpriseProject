import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { RefreshCw } from 'lucide-react'
import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { isApiError } from '@/api/ApiError'
import { couriersApi } from '@/api/endpoints/couriers'
import { parcelsApi } from '@/api/endpoints/parcels'
import { describeError } from '@/api/errorMessages'
import { qk } from '@/api/queryKeys'
import type { Assignment, StatusChange } from '@/api/types'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'
import { groupManifest } from '@/lib/manifest'
import type { CourierAction } from '@/lib/transitions'
import { AssignmentCard } from './AssignmentCard'
import { FailSheet } from './FailSheet'
import { ProofSheet, type DeliveredPayload } from './ProofSheet'

type Pending = { a: Assignment; kind: 'DELIVERED' | 'FAILED' }

export function CourierTodayPage() {
  const qc = useQueryClient()
  const [pending, setPending] = useState<Pending>()
  const list = useQuery({
    queryKey: qk.courier.today,
    queryFn: couriersApi.myAssignments,
    refetchOnWindowFocus: true,
  })

  const advance = useMutation({
    mutationFn: async ({
      a,
      change,
      photo,
    }: {
      a: Assignment
      change: StatusChange
      photo?: File
    }) => {
      // Proof first: if the upload fails nothing has changed and the courier can simply retry.
      const proofUrl = photo
        ? (await parcelsApi.uploadProof(a.parcelId, photo)).proofUrl
        : undefined
      return parcelsApi.updateStatus(a.parcelId, { ...change, proofUrl })
    },
    onMutate: async ({ a, change }) => {
      await qc.cancelQueries({ queryKey: qk.courier.today })
      const previous = qc.getQueryData<Assignment[]>(qk.courier.today)
      qc.setQueryData<Assignment[]>(qk.courier.today, (old) =>
        old?.map((x) => (x.id === a.id ? { ...x, status: change.toStatus } : x)),
      )
      return { previous }
    },
    onError: (e, _vars, ctx) => {
      qc.setQueryData(qk.courier.today, ctx?.previous)
      toast.error(
        isApiError(e) && (e.status === 409 || e.errorCode === 'INVALID_STATUS_TRANSITION')
          ? 'This parcel was updated elsewhere. Refreshed.'
          : describeError(e).message,
      )
    },
    onSuccess: () => setPending(undefined),
    onSettled: () => void qc.invalidateQueries({ queryKey: qk.courier.today }),
  })

  const onAction = (a: Assignment, action: CourierAction) => {
    if (action.to === 'DELIVERED' || action.to === 'FAILED')
      return setPending({ a, kind: action.to })
    advance.mutate({ a, change: { toStatus: action.to } })
  }
  const onDelivered = ({ photo, note }: DeliveredPayload) =>
    pending && advance.mutate({ a: pending.a, change: { toStatus: 'DELIVERED', note }, photo })
  const onFailed = (failureReason: string) =>
    pending && advance.mutate({ a: pending.a, change: { toStatus: 'FAILED', failureReason } })

  const groups = useMemo(() => groupManifest(list.data ?? []), [list.data])
  const counts = useMemo(() => {
    const all = list.data ?? []
    const done = all.filter((a) => a.status === 'DELIVERED').length
    const failed = all.filter((a) => a.status === 'FAILED').length
    return { todo: all.length - done - failed, done, failed }
  }, [list.data])

  return (
    <div className="mx-auto flex max-w-xl flex-col gap-5">
      <PageHeader
        title="Today"
        description={formatDate(new Date().toISOString())}
      />

      {list.isPending ? (
        <div aria-busy="true" className="flex flex-col gap-3">
          <Skeleton className="h-40 w-full" />
          <Skeleton className="h-40 w-full" />
        </div>
      ) : list.isError ? (
        <ErrorState error={list.error} onRetry={() => void list.refetch()} />
      ) : list.data.length === 0 ? (
        <EmptyState
          title="Nothing assigned yet"
          description="New parcels will appear here when dispatch assigns them."
        />
      ) : (
        <>
          <div className="flex items-center gap-3">
            <p
              role="status"
              className="min-w-0 flex-1 rounded-lg bg-muted p-3 text-center text-sm font-medium"
            >
              <span className="text-sky-700 dark:text-sky-300">
                {counts.todo} to deliver
              </span>
              <span className="text-muted-foreground"> · </span>
              <span className="text-emerald-700 dark:text-emerald-300">{counts.done} done</span>
              <span className="text-muted-foreground"> · </span>
              <span className="text-destructive">{counts.failed} failed</span>
            </p>
            <Button
              variant="outline"
              size="sm"
              className="shrink-0"
              onClick={() => void list.refetch()}
              disabled={list.isFetching}
              aria-label="Refresh"
            >
              <RefreshCw
                className={`h-4 w-4 ${list.isFetching ? 'animate-spin' : ''}`}
                aria-hidden
              />
            </Button>
          </div>
          {groups.map(([city, items]) => (
            <section key={city} aria-label={`Pickup in ${city}`} className="flex flex-col gap-3">
              <h2 className="text-sm font-semibold uppercase tracking-wide text-muted-foreground">
                {city}
              </h2>
              <ul className="flex flex-col gap-3">
                {items.map((a) => (
                  <AssignmentCard
                    key={a.id}
                    a={a}
                    busy={advance.isPending && advance.variables?.a.id === a.id}
                    onAction={onAction}
                  />
                ))}
              </ul>
            </section>
          ))}
        </>
      )}

      <ProofSheet
        open={pending?.kind === 'DELIVERED'}
        trackingNumber={pending?.a.trackingNumber ?? ''}
        busy={advance.isPending}
        onCancel={() => setPending(undefined)}
        onConfirm={onDelivered}
      />
      <FailSheet
        open={pending?.kind === 'FAILED'}
        trackingNumber={pending?.a.trackingNumber ?? ''}
        busy={advance.isPending}
        onCancel={() => setPending(undefined)}
        onConfirm={onFailed}
      />
    </div>
  )
}
