import { X } from 'lucide-react'
import { useState } from 'react'
import { Input } from '@/components/ui/input'

/** Type a value and press Enter or comma to add it; Backspace on an empty field removes the last one. */
export function TagInput({
  id,
  value,
  onChange,
  label,
}: {
  id: string
  value: string[]
  onChange: (v: string[]) => void
  label: string
}) {
  const [draft, setDraft] = useState('')
  const add = () => {
    const v = draft.trim()
    if (v && !value.some((x) => x.toLowerCase() === v.toLowerCase())) onChange([...value, v])
    setDraft('')
  }
  return (
    <div className="flex flex-col gap-2">
      <Input
        id={id}
        value={draft}
        placeholder="Type a city, press Enter"
        onChange={(e) => setDraft(e.target.value.replace(',', ''))}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ',') {
            e.preventDefault()
            add()
          } else if (e.key === 'Backspace' && !draft && value.length) onChange(value.slice(0, -1))
        }}
        onBlur={add}
      />
      <ul className="flex flex-wrap gap-2" aria-label={label}>
        {value.map((c) => (
          <li
            key={c}
            className="flex items-center gap-1 rounded-full bg-secondary px-2.5 py-1 text-xs"
          >
            {c}
            <button
              type="button"
              aria-label={`Remove ${c}`}
              onClick={() => onChange(value.filter((x) => x !== c))}
            >
              <X className="h-3 w-3" aria-hidden />
            </button>
          </li>
        ))}
      </ul>
    </div>
  )
}
