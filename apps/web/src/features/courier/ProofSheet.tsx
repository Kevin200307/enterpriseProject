import { Camera } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { prepareImage, validateProof } from '@/lib/image'

export interface DeliveredPayload {
  photo: File
  note?: string
}

/** Delivered: requires proof photo (camera on phones) with preview, plus an optional note. */
export function ProofSheet({
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
  onConfirm: (p: DeliveredPayload) => void
}) {
  const [photo, setPhoto] = useState<File>()
  const [preview, setPreview] = useState<string>()
  const [error, setError] = useState<string>()
  const [note, setNote] = useState('')

  useEffect(() => {
    if (!photo) return setPreview(undefined)
    const url = URL.createObjectURL(photo)
    setPreview(url)
    return () => URL.revokeObjectURL(url)
  }, [photo])

  useEffect(() => {
    if (open) {
      setPhoto(undefined)
      setError(undefined)
      setNote('')
    }
  }, [open])

  async function pick(file: File | undefined) {
    if (!file) return
    const problem = validateProof(file)
    setError(problem ?? undefined)
    setPhoto(problem ? undefined : await prepareImage(file))
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(o) => !o && !busy && onCancel()}
      title={`Deliver ${trackingNumber}`}
      description="Take a photo of the parcel at the door as proof of delivery."
      side="bottom"
    >
      <div className="flex flex-col gap-4">
        <div className="flex flex-col gap-2">
          <Label htmlFor="proof-photo">Proof photo (required)</Label>
          <Input
            id="proof-photo"
            type="file"
            accept="image/jpeg,image/png"
            capture="environment"
            required
            className="h-auto py-3"
            onChange={(e) => void pick(e.target.files?.[0])}
          />
          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}
          {preview ? (
            <img
              src={preview}
              alt="Proof preview"
              className="max-h-56 rounded-md border object-contain"
            />
          ) : (
            <p className="flex items-center gap-2 text-sm text-muted-foreground">
              <Camera className="h-4 w-4" aria-hidden /> JPEG or PNG, up to 5 MB.
            </p>
          )}
        </div>
        <div className="flex flex-col gap-2">
          <Label htmlFor="proof-note">Note (optional)</Label>
          <Input
            id="proof-note"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Left with security desk"
          />
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="lg" className="flex-1" disabled={busy} onClick={onCancel}>
            Back
          </Button>
          <Button
            size="lg"
            className="flex-1"
            disabled={busy || !photo}
            onClick={() => photo && onConfirm({ photo, note: note.trim() || undefined })}
          >
            {busy ? 'Saving…' : 'Confirm delivered'}
          </Button>
        </div>
      </div>
    </Dialog>
  )
}
