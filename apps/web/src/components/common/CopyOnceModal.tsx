import { TriangleAlert } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { CopyField } from './CopyField'

/**
 * Shows a secret exactly once. It cannot be dismissed (Esc, outside click, Done) until the user confirms they
 * copied it. The parent must hold the secret in component state only and clear it in `onClose`.
 */
export function CopyOnceModal({
  secret,
  title,
  label,
  onClose,
}: {
  secret: string | undefined
  title: string
  label: string
  onClose: () => void
}) {
  const [acknowledged, setAcknowledged] = useState(false)
  useEffect(() => setAcknowledged(false), [secret])
  return (
    <Dialog
      open={!!secret}
      onOpenChange={(o) => !o && acknowledged && onClose()}
      title={title}
      description="Copy it now and store it somewhere safe."
    >
      {secret && (
        <div className="flex flex-col gap-4">
          <CopyField value={secret} label={label} />
          <p
            role="alert"
            className="flex gap-2 rounded-md bg-red-50 p-3 text-sm font-medium text-red-900"
          >
            <TriangleAlert className="h-4 w-4 shrink-0" aria-hidden />
            You will not see this {label} again.
          </p>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={acknowledged}
              onChange={(e) => setAcknowledged(e.target.checked)}
            />
            I have copied this {label}
          </label>
          <Button disabled={!acknowledged} onClick={onClose}>
            Done
          </Button>
        </div>
      )}
    </Dialog>
  )
}
