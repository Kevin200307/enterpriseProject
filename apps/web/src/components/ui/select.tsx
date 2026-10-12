import * as React from 'react'
import { cn } from '@/lib/cn'

// Chevron as a background rather than an overlay element, so Select stays a single node and
// drops into existing flex/grid layouts exactly as a bare <select> did. Stroke matches
// --muted-foreground; the theme is light-only, so one fixed value is enough.
const CHEVRON =
  "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='16' height='16' viewBox='0 0 24 24' fill='none' stroke='%236b6b6b' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E\")"

/**
 * Native select wearing the same shell as Input, with the OS chevron swapped for our own.
 *
 * Deliberately still a native <select>: it stays compatible with react-hook-form's uncontrolled
 * `register()` (which reads the value off the element ref), and keeps the platform picker,
 * type-ahead and screen-reader behaviour. The open option list is drawn by the OS and cannot be
 * themed from CSS — restyling it would mean a custom listbox and moving every form to Controller.
 */
export const Select = React.forwardRef<
  HTMLSelectElement,
  React.SelectHTMLAttributes<HTMLSelectElement>
>(({ className, children, ...props }, ref) => (
  <select
    ref={ref}
    style={{
      backgroundImage: CHEVRON,
      backgroundPosition: 'right 0.75rem center',
      backgroundRepeat: 'no-repeat',
    }}
    className={cn(
      // 44px on touch with 16px text, so taps land and iOS does not zoom on focus.
      'h-11 appearance-none rounded-xl border border-input bg-background pl-3 pr-9 text-base md:h-10 md:text-sm',
      'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
      'disabled:cursor-not-allowed disabled:opacity-50',
      className,
    )}
    {...props}
  >
    {children}
  </select>
))
Select.displayName = 'Select'
