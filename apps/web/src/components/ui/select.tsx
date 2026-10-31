import * as React from 'react'
import { Check, ChevronDown } from 'lucide-react'
import { cn } from '@/lib/cn'

type Option = {
  value: string
  label: React.ReactNode
}

function getOptions(children: React.ReactNode): Option[] {
  return React.Children.toArray(children).flatMap((child) => {
    if (
      !React.isValidElement<{ value?: string; children?: React.ReactNode }>(child) ||
      child.type !== 'option'
    )
      return []
    return [{ value: child.props.value ?? '', label: child.props.children }]
  })
}

/**
 * The shared application select. A visually-hidden native select remains in the DOM for form
 * registration and browser autofill, while the visible listbox keeps opened menus consistent
 * across browsers and themes.
 */
export const Select = React.forwardRef<
  HTMLSelectElement,
  React.SelectHTMLAttributes<HTMLSelectElement>
>(({ className, children, value, defaultValue, onChange, onBlur, id, ...props }, ref) => {
  const options = getOptions(children)
  const [open, setOpen] = React.useState(false)
  const rootRef = React.useRef<HTMLDivElement>(null)
  const selectRef = React.useRef<HTMLSelectElement>(null)
  const listboxId = React.useId()
  const isControlled = value !== undefined
  const [uncontrolledValue, setUncontrolledValue] = React.useState(() => {
    if (defaultValue !== undefined) return String(defaultValue)
    return options[0]?.value ?? ''
  })
  const selectedValue = isControlled ? String(value) : uncontrolledValue
  const selected = options.find((option) => option.value === selectedValue) ?? options[0]

  React.useImperativeHandle(ref, () => selectRef.current as HTMLSelectElement)

  React.useEffect(() => {
    if (!open) return
    const close = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    const escape = (event: KeyboardEvent) => event.key === 'Escape' && setOpen(false)
    document.addEventListener('mousedown', close)
    document.addEventListener('keydown', escape)
    return () => {
      document.removeEventListener('mousedown', close)
      document.removeEventListener('keydown', escape)
    }
  }, [open])

  const choose = (nextValue: string) => {
    if (!isControlled) setUncontrolledValue(nextValue)
    if (selectRef.current) {
      selectRef.current.value = nextValue
      selectRef.current.dispatchEvent(new Event('change', { bubbles: true }))
    }
    onChange?.({
      target: { value: nextValue, name: props.name },
      currentTarget: { value: nextValue, name: props.name },
    } as React.ChangeEvent<HTMLSelectElement>)
    setOpen(false)
  }

  return (
    <div ref={rootRef} className="relative">
      <select
        {...props}
        ref={selectRef}
        value={selectedValue}
        onChange={onChange}
        onBlur={onBlur}
        tabIndex={-1}
        aria-hidden
        className="sr-only"
      >
        {children}
      </select>
      <button
        type="button"
        role="combobox"
        disabled={props.disabled}
        aria-label={props['aria-label']}
        aria-labelledby={props['aria-labelledby']}
        aria-describedby={props['aria-describedby']}
        aria-invalid={props['aria-invalid']}
        aria-required={props['aria-required']}
        id={id}
        aria-controls={listboxId}
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((current) => !current)}
        className={cn(
          'flex h-11 w-full items-center justify-between rounded-xl border border-input bg-background px-3 text-base shadow-sm',
          'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
          'disabled:cursor-not-allowed disabled:opacity-50 md:h-10 md:text-sm',
          className,
        )}
      >
        <span className="truncate">{selected?.label ?? 'Select'}</span>
        <ChevronDown className="ml-3 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
      </button>
      {open && (
        <div
          id={listboxId}
          role="listbox"
          aria-label={props['aria-label']}
          className="absolute left-0 top-[calc(100%+0.35rem)] z-30 max-h-72 w-full min-w-[12rem] overflow-y-auto rounded-2xl border border-border bg-card p-2 shadow-xl shadow-foreground/10"
        >
          {options.map((option) => {
            const isSelected = option.value === selectedValue
            return (
              <button
                key={option.value}
                type="button"
                role="option"
                aria-selected={isSelected}
                onClick={() => choose(option.value)}
                className="flex w-full items-center gap-3 rounded-xl px-3 py-3 text-left text-base hover:bg-secondary md:text-sm"
              >
                <Check
                  className={cn('h-4 w-4 shrink-0', isSelected ? 'opacity-100' : 'opacity-0')}
                  aria-hidden
                />
                <span>{option.label}</span>
              </button>
            )
          })}
        </div>
      )}
    </div>
  )
})
Select.displayName = 'Select'
