import { useRouteError } from 'react-router-dom'
import { ErrorState } from '@/components/common/ErrorState'

export function RouteError() {
  const error = useRouteError()
  return (
    <div className="mx-auto max-w-lg p-8">
      <ErrorState error={error} onRetry={() => location.reload()} />
    </div>
  )
}
