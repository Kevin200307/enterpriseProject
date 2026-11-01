import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Download, Share2 } from 'lucide-react'
import { useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { isApiError } from '@/api/ApiError'
import { describeError } from '@/api/errorMessages'
import { parcelsApi } from '@/api/endpoints/parcels'
import { qk } from '@/api/queryKeys'
import type { Address, HistoryEntry, Parcel } from '@/api/types'
import { CopyField } from '@/components/common/CopyField'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { MoneyLKR } from '@/components/common/MoneyLKR'
import { PageHeader } from '@/components/common/PageHeader'
import { StatusChip } from '@/components/common/StatusChip'
import { StatusTimeline, type TimelineItem } from '@/components/common/StatusTimeline'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { shareLink, trackingUrl } from '@/lib/share'
import { useParcelLiveStatus } from '@/realtime/useParcelLiveStatus'

const AddressCard = ({ title, a }: { title: string; a: Address }) => (
  <Card>
    <CardHeader>
      <CardTitle>{title}</CardTitle>
    </CardHeader>
    <CardContent className="text-sm">
      <p className="font-medium">{a.contactName}</p>
      <p>{a.phone}</p>
      <p>{a.line1}</p>
      {a.line2 && <p>{a.line2}</p>}
      <p>
        {a.city} {a.postalCode}
      </p>
    </CardContent>
  </Card>
)

function toTimeline(history: HistoryEntry[]): TimelineItem[] {
  return [...history]
    .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt))
    .map((h) => ({
      status: h.toStatus,
      occurredAt: h.occurredAt,
      note: h.note,
      extra: h.failedReason ? (
        <p className="mt-1 rounded-md bg-red-50 p-2 text-sm text-red-900">
          Reason: {h.failedReason}
        </p>
      ) : undefined,
    }))
}

export function DeliveryDetailPage() {
  const { id = '' } = useParams()
  const qc = useQueryClient()
  const [confirmCancel, setConfirmCancel] = useState(false)
  const [downloading, setDownloading] = useState(false)

  const parcel = useQuery({
    queryKey: qk.parcels.detail(id),
    queryFn: () => parcelsApi.get(id),
    retry: false,
  })
  const history = useQuery({
    queryKey: [...qk.parcels.detail(id), 'history'],
    queryFn: () => parcelsApi.history(id),
    enabled: parcel.isSuccess,
  })
  const proofId = history.data?.content.find((entry) => entry.proofId)?.proofId
  const proofUrl = useQuery({
    queryKey: [...qk.parcels.detail(id), 'proof-url', proofId],
    queryFn: () => {
      if (!proofId) throw new Error('Proof is not available')
      return parcelsApi.proofUrl(id, proofId)
    },
    enabled: parcel.data?.status === 'DELIVERED' && !!proofId,
    staleTime: 4 * 60_000,
  })

  // Live updates: the pushed payload is the masked public shape, so only use it as a "something changed"
  // signal and refetch the authoritative (owner-scoped) data.
  const lastStatus = useRef<string>()
  lastStatus.current ??= parcel.data?.status
  useParcelLiveStatus(parcel.data?.trackingNumber, {
    onMessage: (t) => {
      if (t.status === lastStatus.current) return
      lastStatus.current = t.status
      void qc.invalidateQueries({ queryKey: qk.parcels.all })
    },
  })

  const cancel = useMutation({
    mutationFn: () => parcelsApi.cancel(id),
    onSuccess: () => toast.success('Delivery cancelled'),
    onError: (e) => toast.error(describeError(e).message),
    onSettled: () => {
      setConfirmCancel(false)
      void qc.invalidateQueries({ queryKey: qk.parcels.all })
    },
  })

  async function downloadReceipt(p: Parcel) {
    setDownloading(true)
    try {
      const blob = await parcelsApi.receipt(p.id)
      const url = URL.createObjectURL(blob)
      const a = Object.assign(document.createElement('a'), {
        href: url,
        download: `receipt-${p.trackingNumber}.pdf`,
      })
      a.click()
      URL.revokeObjectURL(url)
    } catch (e) {
      toast.error(describeError(e).message)
    } finally {
      setDownloading(false)
    }
  }

  if (parcel.isPending) return <Skeleton className="h-96 w-full" />
  if (parcel.isError) {
    // 404 deliberately covers both "does not exist" and "not yours" so ids cannot be probed.
    if (isApiError(parcel.error) && parcel.error.status === 404) {
      return (
        <EmptyState
          title="Delivery not found"
          description="This delivery doesn't exist or isn't yours."
          action={
            <Button asChild>
              <Link to="/deliveries">Back to my deliveries</Link>
            </Button>
          }
        />
      )
    }
    return <ErrorState error={parcel.error} onRetry={() => void parcel.refetch()} />
  }

  const p = parcel.data
  const failureReason = history.data?.content.find((entry) => entry.toStatus === 'FAILED')?.failedReason
  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Delivery details" />

      <div className="flex flex-wrap items-center gap-3">
        <div className="min-w-[14rem] max-w-sm flex-1">
          <CopyField value={p.trackingNumber} label="tracking number" />
        </div>
        <StatusChip status={p.status} />
      </div>

      {p.status === 'FAILED' && failureReason && (
        <p
          role="alert"
          className="rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-900"
        >
          Delivery failed: {failureReason}
        </p>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_320px]">
        <div className="flex flex-col gap-6">
          <Card>
            <CardHeader>
              <CardTitle>Progress</CardTitle>
            </CardHeader>
            <CardContent>
              {history.isPending ? (
                <Skeleton className="h-32 w-full" />
              ) : history.isError ? (
                <ErrorState error={history.error} onRetry={() => void history.refetch()} />
              ) : (
                <StatusTimeline items={toTimeline(history.data.content)} />
              )}
            </CardContent>
          </Card>
          {p.status === 'DELIVERED' && proofUrl.isError && (
            <ErrorState error={proofUrl.error} onRetry={() => void proofUrl.refetch()} />
          )}
          {p.status === 'DELIVERED' && proofUrl.data && (
            <Card>
              <CardHeader>
                <CardTitle>Proof of delivery</CardTitle>
              </CardHeader>
              <CardContent>
                <a href={proofUrl.data.url} target="_blank" rel="noopener noreferrer">
                  <img
                    src={proofUrl.data.url}
                    alt="Proof of delivery"
                    className="max-h-80 rounded-md border object-contain"
                  />
                </a>
              </CardContent>
            </Card>
          )}
        </div>

        <div className="flex flex-col gap-6">
          <Card>
            <CardHeader>
              <CardTitle>Parcel</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="grid grid-cols-2 gap-2 text-sm">
                <dt className="text-muted-foreground">Size</dt>
                <dd>{p.size}</dd>
                <dt className="text-muted-foreground">Weight</dt>
                <dd>{p.weightKg} kg</dd>
                <dt className="border-t pt-2 font-semibold">Fee</dt>
                <dd className="border-t pt-2 font-semibold">
                  <MoneyLKR value={p.fee} />
                </dd>
              </dl>
            </CardContent>
          </Card>
          <AddressCard title="Pickup" a={p.pickup} />
          <AddressCard title="Drop-off" a={p.dropoff} />
        </div>
      </div>

      {/* The actions live at the foot of the page, where the thumb is, and split the width
          evenly rather than hugging their labels. */}
      <div className="flex flex-col gap-3">
        <div className="flex gap-3">
          <Button
            variant="outline"
            className="h-11 flex-1"
            onClick={async () => {
              if ((await shareLink(trackingUrl(p.trackingNumber))) === 'copied')
                toast.success('Tracking link copied')
            }}
          >
            <Share2 className="h-4 w-4" aria-hidden /> Share
          </Button>
          <Button
            variant="outline"
            className="h-11 flex-1"
            disabled={downloading}
            onClick={() => void downloadReceipt(p)}
          >
            <Download className="h-4 w-4" aria-hidden />{' '}
            {downloading ? 'Preparing…' : 'Download receipt'}
          </Button>
        </div>
        {p.status === 'CREATED' && (
          <Button
            variant="destructive"
            className="h-11 w-full"
            onClick={() => setConfirmCancel(true)}
          >
            Cancel delivery
          </Button>
        )}
      </div>

      <Dialog
        open={confirmCancel}
        onOpenChange={setConfirmCancel}
        title="Cancel this delivery?"
        description="This can only be done before the courier picks it up."
      >
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={() => setConfirmCancel(false)}>
            Keep delivery
          </Button>
          <Button variant="destructive" disabled={cancel.isPending} onClick={() => cancel.mutate()}>
            {cancel.isPending ? 'Cancelling…' : 'Yes, cancel'}
          </Button>
        </div>
      </Dialog>
    </div>
  )
}
