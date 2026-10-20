import { cn } from '@/lib/cn'

export const Skeleton = ({ className, ...p }: React.HTMLAttributes<HTMLDivElement>) => (
  <div aria-hidden className={cn('animate-pulse rounded-md bg-muted', className)} {...p} />
)
