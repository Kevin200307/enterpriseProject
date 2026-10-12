import * as DialogPrimitive from '@radix-ui/react-dialog'
import { X } from 'lucide-react'
import type { ReactNode } from 'react'

export function Dialog({
  open,
  onOpenChange,
  title,
  description,
  children,
  side = 'center',
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  children: ReactNode
  /** `bottom` anchors the dialog like a sheet — easier to reach one-handed on a phone. */
  side?: 'center' | 'bottom'
}) {
  return (
    <DialogPrimitive.Root open={open} onOpenChange={onOpenChange}>
      <DialogPrimitive.Portal>
        <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/50 data-[state=open]:animate-fade-in data-[state=closed]:animate-fade-out motion-reduce:animate-none" />
        <DialogPrimitive.Content
          className={
            // No `relative` here: it would beat `fixed` in Tailwind's output and drop the sheet
            // into normal flow. `fixed` is already a containing block for the close button.
            side === 'bottom'
              ? 'fixed inset-x-0 bottom-0 z-50 overflow-x-hidden max-h-[90vh] overflow-y-auto rounded-t-2xl border bg-background p-6 shadow-lg focus:outline-none data-[state=open]:animate-slide-in-from-bottom data-[state=closed]:animate-slide-out-to-bottom motion-reduce:animate-none sm:inset-x-auto sm:left-1/2 sm:max-w-md sm:-translate-x-1/2'
              : 'fixed left-1/2 top-1/2 z-50 w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 rounded-lg border bg-background p-6 shadow-lg focus:outline-none'
          }
        >
          {/* A way out in the corner — on a sheet the overlay is easy to miss, and Escape is no
              use on a phone. */}
          <DialogPrimitive.Close
            aria-label="Close"
            className="absolute right-4 top-4 flex h-9 w-9 items-center justify-center rounded-full text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground active:scale-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <X className="h-[18px] w-[18px]" aria-hidden />
          </DialogPrimitive.Close>
          <DialogPrimitive.Title className="pr-10 text-lg font-semibold">
            {title}
          </DialogPrimitive.Title>
          <DialogPrimitive.Description
            className={description ? 'mt-1 text-sm text-muted-foreground' : 'sr-only'}
          >
            {description ?? title}
          </DialogPrimitive.Description>
          <div className="mt-4">{children}</div>
        </DialogPrimitive.Content>
      </DialogPrimitive.Portal>
    </DialogPrimitive.Root>
  )
}
