import type { NotificationStatus } from '@/api/types'
import { cn } from '@/lib/cn'

const TONE: Record<NotificationStatus, string> = {
  SENT: 'bg-green-100 text-green-900',
  PENDING: 'bg-amber-100 text-amber-900',
  FAILED: 'bg-red-100 text-red-900',
  SKIPPED: 'bg-muted text-muted-foreground',
}

export function NotificationBadge({ status }: { status: NotificationStatus }) {
  return (
    <span className={cn('rounded-full px-2.5 py-1 text-xs font-medium', TONE[status])}>
      {status.charAt(0) + status.slice(1).toLowerCase()}
    </span>
  )
}
