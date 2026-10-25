import * as React from 'react'
import { cn } from '@/lib/cn'

export const Input = React.forwardRef<
  HTMLInputElement,
  React.InputHTMLAttributes<HTMLInputElement>
>(({ className, type, ...props }, ref) => (
  <input
    ref={ref}
    type={type}
    className={cn(
      // 44px tall on touch so it clears the minimum tap target; 16px text stops iOS zooming on focus.
      'flex h-11 w-full rounded-xl border border-input bg-background px-3 py-2 text-base md:h-10 md:text-sm',
      'placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50',
      className,
    )}
    {...props}
  />
))
Input.displayName = 'Input'
