import type { ReactNode } from 'react'
import { formatDateTime } from '@/lib/format'
import { statusMeta } from '@/lib/status'
import { cn } from '@/lib/cn'

export interface TimelineItem {
  status: string
  occurredAt: string
  note?: string
  actor?: string
  extra?: ReactNode
}

/** Vertical timeline. Pass items newest-first; the first item is highlighted as the current state. */
export function StatusTimeline({ items }: { items: TimelineItem[] }) {
  return (
    <ol className="relative flex flex-col gap-5 border-l pl-6" aria-label="Status history">
      {items.map((it, i) => {
        const { label, icon: Icon } = statusMeta(it.status)
        return (
          <li key={`${it.status}-${it.occurredAt}-${i}`} className="relative">
            <span
              className={cn(
                'absolute -left-[37px] flex h-6 w-6 items-center justify-center rounded-full border bg-background',
                i === 0 && 'border-primary bg-primary text-primary-foreground',
              )}
            >
              <Icon className="h-3.5 w-3.5" aria-hidden />
            </span>
            <p className={cn('text-sm', i === 0 ? 'font-semibold' : 'font-medium')}>{label}</p>
            <p className="text-xs text-muted-foreground">
              <time dateTime={it.occurredAt}>{formatDateTime(it.occurredAt)}</time>
              {it.actor && ` · ${it.actor}`}
            </p>
            {it.note && <p className="mt-1 text-sm">{it.note}</p>}
            {it.extra}
          </li>
        )
      })}
    </ol>
  )
}
