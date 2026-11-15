import type { ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'

export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  destructive,
  busy,
  onConfirm,
  onCancel,
  children,
}: {
  open: boolean
  title: string
  description?: string
  confirmLabel: string
  destructive?: boolean
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
  children?: ReactNode
}) {
  return (
    <Dialog
      open={open}
      onOpenChange={(o) => !o && !busy && onCancel()}
      title={title}
      description={description}
    >
      <div className="flex flex-col gap-4">
        {children}
        <div className="flex justify-end gap-2">
          <Button variant="outline" disabled={busy} onClick={onCancel}>
            Cancel
          </Button>
          <Button
            variant={destructive ? 'destructive' : 'default'}
            disabled={busy}
            onClick={onConfirm}
          >
            {busy ? 'Working…' : confirmLabel}
          </Button>
        </div>
      </div>
    </Dialog>
  )
}
