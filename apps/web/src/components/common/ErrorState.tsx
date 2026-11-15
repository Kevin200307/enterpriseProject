import { TriangleAlert } from 'lucide-react'
import { isApiError } from '@/api/ApiError'
import { Button } from '@/components/ui/button'
import { describeError } from '@/api/errorMessages'

/** Renders any thrown value as a readable message — never a blank screen. */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { title, message } = describeError(error)
  const traceId = isApiError(error) ? error.traceId : undefined
  return (
    <div
      role="alert"
      className="flex flex-col items-center gap-3 rounded-lg border border-destructive/40 bg-destructive/5 p-8 text-center"
    >
      <TriangleAlert className="h-8 w-8 text-destructive" aria-hidden />
      <h2 className="text-lg font-semibold">{title}</h2>
      <p className="max-w-md text-sm text-muted-foreground">{message}</p>
      {traceId && <p className="font-mono text-xs text-muted-foreground">Reference: {traceId}</p>}
      {onRetry && (
        <Button variant="outline" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}
