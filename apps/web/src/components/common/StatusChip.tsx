import { statusMeta } from '@/lib/status'
import { cn } from '@/lib/cn'

export function StatusChip({ status, className }: { status: string; className?: string }) {
  const { label, icon: Icon, className: tone } = statusMeta(status)
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium',
        tone,
        className,
      )}
    >
      <Icon className="h-3.5 w-3.5" aria-hidden />
      {label}
    </span>
  )
}
