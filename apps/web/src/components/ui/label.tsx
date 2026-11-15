import * as React from 'react'
import { cn } from '@/lib/cn'

export const Label = ({
  className,
  children,
  ...p
}: React.LabelHTMLAttributes<HTMLLabelElement>) => (
  <label className={cn('text-sm font-medium leading-none', className)} {...p}>
    {children}
  </label>
)
