import { useId, type ReactNode } from 'react'
import { Label } from '@/components/ui/label'

/** Label + control + inline error, wired with aria so screen readers announce the message. */
export function FormField({
  label,
  error,
  hint,
  children,
}: {
  label: string
  error?: string
  hint?: string
  children: (props: {
    id: string
    'aria-invalid': boolean
    'aria-describedby'?: string
  }) => ReactNode
}) {
  const id = useId()
  const msgId = `${id}-msg`
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children({
        id,
        'aria-invalid': !!error,
        'aria-describedby': error || hint ? msgId : undefined,
      })}
      {(error || hint) && (
        <p
          id={msgId}
          role={error ? 'alert' : undefined}
          className={error ? 'text-sm text-destructive' : 'text-sm text-muted-foreground'}
        >
          {error ?? hint}
        </p>
      )}
    </div>
  )
}
