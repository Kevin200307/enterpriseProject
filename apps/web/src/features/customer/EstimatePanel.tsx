import { isApiError } from '@/api/ApiError'
import { describeError } from '@/api/errorMessages'
import type { Estimate } from '@/api/types'
import { MoneyLKR } from '@/components/common/MoneyLKR'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/skeleton'

interface Props {
  ready: boolean
  loading: boolean
  data?: Estimate
  error: unknown
  toCity: string
}

const Row = ({
  label,
  value,
  strong,
}: {
  label: string
  value: number | string
  strong?: boolean
}) => (
  <div
    className={`flex justify-between ${strong ? 'border-t pt-2 text-base font-semibold' : 'text-sm'}`}
  >
    <dt className={strong ? '' : 'text-muted-foreground'}>{label}</dt>
    <dd>
      <MoneyLKR value={value} />
    </dd>
  </div>
)

function unservedCity(error: unknown) {
  return isApiError(error) && error.status === 404
}

export function EstimatePanel({ ready, loading, data, error, toCity }: Props) {
  let body
  if (!ready) {
    body = (
      <p className="text-sm text-muted-foreground">
        Enter both cities, the size and the weight to see your price.
      </p>
    )
  } else if (loading) {
    body = (
      <div aria-busy="true" className="flex flex-col gap-2">
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-4 w-full" />
        <Skeleton className="h-6 w-2/3" />
      </div>
    )
  } else if (unservedCity(error)) {
    body = (
      <p role="alert" className="text-sm text-destructive">
        We don&apos;t deliver to {toCity || 'that city'} yet.
      </p>
    )
  } else if (error) {
    const { message } = describeError(error)
    body = (
      <p role="alert" className="text-sm text-destructive">
        {isApiError(error) && error.errorCode === 'UPSTREAM_UNAVAILABLE'
          ? "Pricing is unavailable, so you can't book right now. Please try again shortly."
          : message}
      </p>
    )
  } else if (data) {
    body = (
      <dl className="flex flex-col gap-2">
        <p className="text-xs text-muted-foreground">
          {data.zoneFrom} → {data.zoneTo}
        </p>
        <Row label="Base fee" value={data.baseFee} />
        <Row label="Weight surcharge" value={data.weightSurcharge} />
        <Row label="Express surcharge" value={data.expressSurcharge} />
        <Row label="Total" value={data.total} strong />
      </dl>
    )
  }
  return (
    <Card aria-live="polite">
      <CardHeader>
        <CardTitle>Price estimate</CardTitle>
      </CardHeader>
      <CardContent>{body}</CardContent>
    </Card>
  )
}
