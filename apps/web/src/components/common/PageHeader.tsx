import type { ReactNode } from 'react'

export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string
  description?: string
  actions?: ReactNode
}) {
  return (
    <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-start sm:justify-between">
      <div>
        <h1 className="text-xl font-semibold tracking-tight sm:text-2xl">{title}</h1>
        {description && <p className="mt-1 text-sm text-muted-foreground">{description}</p>}
      </div>
      {/* Full-bleed on phones so the primary action is an easy thumb target */}
      {actions && <div className="flex shrink-0 gap-2 [&>*]:flex-1 sm:[&>*]:flex-none">{actions}</div>}
    </div>
  )
}
