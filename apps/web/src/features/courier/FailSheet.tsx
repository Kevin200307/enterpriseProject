import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { FAILURE_REASONS } from '@/lib/transitions'

const OTHER = 'Other'

/** FAILED always needs a reason (the server rejects a blank one too). */
export function FailSheet({
  open,
  trackingNumber,
  busy,
  onCancel,
  onConfirm,
}: {
  open: boolean
  trackingNumber: string
  busy: boolean
  onCancel: () => void
  onConfirm: (reason: string) => void
}) {
  const [choice, setChoice] = useState<string>()
  const [other, setOther] = useState('')
  const [touched, setTouched] = useState(false)

  useEffect(() => {
    if (open) {
      setChoice(undefined)
      setOther('')
      setTouched(false)
    }
  }, [open])

  const reason = choice === OTHER ? other.trim() : (choice ?? '')

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => !o && !busy && onCancel()}
      title={`Could not deliver ${trackingNumber}`}
      description="Choose the reason. The sender is notified."
      side="bottom"
    >
      <div className="flex flex-col gap-4">
        <fieldset className="flex flex-col gap-2">
          <legend className="sr-only">Failure reason</legend>
          {[...FAILURE_REASONS, OTHER].map((r) => (
            <label
              key={r}
              className="flex min-h-12 cursor-pointer items-center gap-3 rounded-md border p-3 has-[:checked]:border-primary has-[:checked]:bg-primary/5"
            >
              <input
                type="radio"
                name="reason"
                value={r}
                checked={choice === r}
                onChange={() => setChoice(r)}
              />
              {r}
            </label>
          ))}
        </fieldset>
        {choice === OTHER && (
          <div className="flex flex-col gap-2">
            <Label htmlFor="fail-other">Describe the problem</Label>
            <Input id="fail-other" value={other} onChange={(e) => setOther(e.target.value)} />
          </div>
        )}
        {touched && !reason && (
          <p role="alert" className="text-sm text-destructive">
            A reason is required.
          </p>
        )}
        <div className="flex gap-2">
          <Button variant="outline" size="lg" className="flex-1" disabled={busy} onClick={onCancel}>
            Back
          </Button>
          <Button
            variant="destructive"
            size="lg"
            className="flex-1"
            disabled={busy}
            onClick={() => (reason ? onConfirm(reason) : setTouched(true))}
          >
            {busy ? 'Saving…' : 'Mark failed'}
          </Button>
        </div>
      </div>
    </Dialog>
  )
}
