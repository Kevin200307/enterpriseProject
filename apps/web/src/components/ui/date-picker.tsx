import {
  addMonths,
  addDays,
  eachDayOfInterval,
  format,
  getYear,
  isAfter,
  isBefore,
  isSameDay,
  isValid,
  parseISO,
  startOfMonth,
  startOfWeek,
  subMonths,
} from 'date-fns'
import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Select } from '@/components/ui/select'
import { cn } from '@/lib/cn'

const WEEKDAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

function parseDate(value: string) {
  const date = parseISO(value)
  return isValid(date) ? date : undefined
}

export function DatePicker({
  id,
  value,
  onChange,
  min,
  max,
  placeholder = 'Select date',
}: {
  id: string
  value: string
  onChange: (value: string) => void
  min?: string
  max?: string
  placeholder?: string
}) {
  const selected = parseDate(value)
  const minimum = parseDate(min ?? '')
  const maximum = parseDate(max ?? '')
  const [open, setOpen] = useState(false)
  const [month, setMonth] = useState(() => startOfMonth(selected ?? new Date()))

  useEffect(() => {
    const nextSelected = parseDate(value)
    if (nextSelected) setMonth(startOfMonth(nextSelected))
  }, [value])

  const calendarStart = startOfWeek(startOfMonth(month), { weekStartsOn: 1 })
  const days = eachDayOfInterval({ start: calendarStart, end: addDays(calendarStart, 41) })
  const selectedYear = getYear(month)
  const currentYear = new Date().getFullYear()
  const firstYear = Math.min(currentYear - 20, minimum ? getYear(minimum) : currentYear - 20)
  const lastYear = Math.max(currentYear + 20, maximum ? getYear(maximum) : currentYear + 20)
  const years = Array.from({ length: lastYear - firstYear + 1 }, (_, index) => firstYear + index)
  const isDisabled = (day: Date) =>
    (minimum ? isBefore(day, minimum) : false) || (maximum ? isAfter(day, maximum) : false)

  return (
    <>
      <button
        id={id}
        type="button"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen((isOpen) => !isOpen)}
        className="flex h-10 w-full min-w-0 items-center justify-between gap-2 rounded-md border border-input bg-background px-3 text-left text-sm transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <span className={cn(!selected && 'text-muted-foreground')}>
          {selected ? format(selected, 'dd MMM yyyy') : placeholder}
        </span>
        <CalendarDays className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
      </button>
      <Dialog
        open={open}
        onOpenChange={setOpen}
        title={`${placeholder} calendar`}
        description="Choose a date"
      >
        <div className="w-full min-w-0">
          <div className="mb-3 flex items-center justify-between">
            <Button
              type="button"
              variant="ghost"
              size="icon"
              aria-label="Previous month"
              onClick={() => setMonth((current) => subMonths(current, 1))}
            >
              <ChevronLeft className="h-4 w-4" aria-hidden />
            </Button>
            <div className="flex items-center gap-1">
              <p className="text-sm font-semibold">{format(month, 'MMMM')}</p>
              <label className="sr-only" htmlFor={`${id}-year`}>
                Year
              </label>
              <Select
                id={`${id}-year`}
                aria-label="Year"
                value={selectedYear}
                onChange={(event) =>
                  setMonth((current) => new Date(Number(event.target.value), current.getMonth(), 1))
                }
                className="h-8 min-w-[5.5rem] rounded-lg px-2 py-1 text-sm font-semibold"
              >
                {years.map((year) => (
                  <option key={year} value={year}>
                    {year}
                  </option>
                ))}
              </Select>
            </div>
            <Button
              type="button"
              variant="ghost"
              size="icon"
              aria-label="Next month"
              onClick={() => setMonth((current) => addMonths(current, 1))}
            >
              <ChevronRight className="h-4 w-4" aria-hidden />
            </Button>
          </div>
          <div className="grid grid-cols-7 gap-1 text-center text-xs font-medium text-muted-foreground">
            {WEEKDAYS.map((weekday) => (
              <span key={weekday} className="py-1">
                {weekday}
              </span>
            ))}
            {days.map((day) => {
              const disabled = isDisabled(day)
              const inMonth = day.getMonth() === month.getMonth()
              return (
                <button
                  key={day.toISOString()}
                  type="button"
                  disabled={disabled || !inMonth}
                  aria-label={format(day, 'dd MMMM yyyy')}
                  aria-pressed={selected ? isSameDay(day, selected) : false}
                  onClick={() => {
                    onChange(format(day, 'yyyy-MM-dd'))
                    setOpen(false)
                  }}
                  className={cn(
                    'h-9 rounded-md text-sm transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-30',
                    selected && isSameDay(day, selected) && 'bg-primary text-primary-foreground hover:bg-primary',
                    inMonth && !selected && isSameDay(day, new Date()) && 'font-semibold text-primary',
                  )}
                >
                  {format(day, 'd')}
                </button>
              )
            })}
          </div>
          {selected && (
            <button
              type="button"
              className="mt-3 w-full rounded-md border px-3 py-2 text-sm font-medium transition-colors hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              onClick={() => {
                onChange('')
                setOpen(false)
              }}
            >
              Clear date
            </button>
          )}
        </div>
      </Dialog>
    </>
  )
}
