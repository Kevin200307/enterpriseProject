import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { merchantApi } from '@/api/endpoints/merchant'
import { qk } from '@/api/queryKeys'
import { ErrorState } from '@/components/common/ErrorState'
import { PageHeader } from '@/components/common/PageHeader'
import { StatusChip } from '@/components/common/StatusChip'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDate } from '@/lib/format'
import { MerchantGate } from './MerchantGate'

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="text-sm font-medium">{children}</dd>
    </div>
  )
}

export function MerchantOrderDetailPage() {
  const { id } = useParams<{ id: string }>()
  const q = useQuery({
    queryKey: qk.merchant.order(id ?? ''),
    queryFn: () => merchantApi.getOrder(id!),
    enabled: !!id,
    retry: false,
  })

  return (
    <MerchantGate error={q.error}>
      <div className="mx-auto flex max-w-2xl flex-col gap-6">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="sm" asChild>
            <Link to="/merchant/orders">← Orders</Link>
          </Button>
        </div>

        {q.isPending ? (
          <Skeleton className="h-64 w-full" />
        ) : q.isError ? (
          <ErrorState error={q.error} onRetry={() => void q.refetch()} />
        ) : (
          <>
            <PageHeader
              title={q.data.merchantRef}
              description={`Created ${formatDate(q.data.createdAt)}`}
            />

            <dl className="grid gap-4 rounded-lg border p-5 sm:grid-cols-2">
              <Field label="Status">
                <StatusChip status={q.data.status} />
              </Field>
              <Field label="Tracking number">
                <Link
                  to={`/track/${q.data.trackingNumber}`}
                  className="font-mono underline-offset-4 hover:underline"
                >
                  {q.data.trackingNumber}
                </Link>
              </Field>
              <Field label="Destination">{q.data.destinationCity}</Field>
              <Field label="Fee">
                {q.data.fee.toFixed(2)} {q.data.currency}
              </Field>
            </dl>
          </>
        )}
      </div>
    </MerchantGate>
  )
}
