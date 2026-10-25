import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'
import { BatteryIcon, SignalIcon, WifiIcon } from './Illustrations'

export function StatusBar({ className }: { className?: string }) {
  return (
    <div
      className={cn(
        'relative z-20 flex items-center justify-between px-7 pt-3 text-[#1b1b1b]',
        className,
      )}
    >
      <span className="text-[14px] font-semibold tracking-tight">9:41</span>
      <div className="flex items-center gap-1.5">
        <SignalIcon className="h-[11px] w-[17px]" />
        <WifiIcon className="h-[11px] w-[15px]" />
        <BatteryIcon level={78} />
      </div>
    </div>
  )
}

export function HomeIndicator({ className }: { className?: string }) {
  return (
    <div className={cn('relative z-20 flex justify-center pb-2 pt-2', className)}>
      <div className="h-[5px] w-[122px] rounded-full bg-[#1b1b1b]" />
    </div>
  )
}

/** Glassy device shell matching the mockup: deep soft shadow, frosted bezel, 46px corners. */
export function PhoneFrame({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={cn(
        'relative w-[320px] shrink-0 rounded-[52px] p-[6px]',
        'bg-gradient-to-b from-white/50 to-white/10 backdrop-blur-sm',
        'shadow-[0_50px_90px_-24px_rgba(0,0,0,0.5)]',
        className,
      )}
    >
      <div className="relative h-[660px] overflow-hidden rounded-[46px] bg-[#f3f1ef]">
        {children}
      </div>
    </div>
  )
}
