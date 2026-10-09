import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { PageHeader } from '@/components/common/PageHeader'
import { TRACKING_NUMBER_RE } from '@/lib/format'

export function TrackLookupPage() {
  const [value, setValue] = useState('')
  const [error, setError] = useState<string>()
  const navigate = useNavigate()

  function submit(e: React.FormEvent) {
    e.preventDefault()
    const tn = value.trim().toUpperCase()
    if (!TRACKING_NUMBER_RE.test(tn))
      return setError("That doesn't look like a SwiftDrop tracking number (SD-XXXXXXXX).")
    navigate(`/track/${tn}`)
  }

  return (
    <div className="mx-auto flex max-w-md flex-col gap-6">
      <PageHeader title="Track a parcel" description="Enter the tracking number you were given." />
      <form onSubmit={submit} noValidate className="flex flex-col gap-3">
        <label htmlFor="tn" className="text-sm font-medium">
          Tracking number
        </label>
        <Input
          id="tn"
          value={value}
          onChange={(e) => {
            setValue(e.target.value.toUpperCase())
            setError(undefined)
          }}
          placeholder="SD-XXXXXXXX"
          autoCapitalize="characters"
          autoComplete="off"
          aria-invalid={!!error}
          aria-describedby={error ? 'tn-error' : undefined}
          className="h-12 font-mono text-lg"
        />
        {error && (
          <p id="tn-error" role="alert" className="text-sm text-destructive">
            {error}
          </p>
        )}
        <Button type="submit" size="lg">
          Track
        </Button>
      </form>
    </div>
  )
}
