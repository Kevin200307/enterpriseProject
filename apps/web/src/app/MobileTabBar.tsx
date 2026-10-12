import { MoreHorizontal, Plus } from 'lucide-react'
import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, matchPath, useLocation } from 'react-router-dom'
import { cn } from '@/lib/cn'
import type { NavItem } from './nav'

/**
 * Geometry of the island, kept in JS so the sliding pill and the tabs it sits behind are driven
 * by the exact same numbers. Both interpolate those numbers with the same duration and curve, so
 * the pill never drifts off its tab mid-flight — neither when switching tabs nor when the bar
 * compacts under a scroll.
 */
const DURATION = 380
const GAP = 2
const PAD = 6
const TAB_W_MAX = 82 // a tab never grows past this, however much room the screen has
const TAB_W_MIN = 52
const TAB_W_COMPACT = 44
const TAB_H = 52
const TAB_H_COMPACT = 38
const LABEL_H = 14
const SIDE_MARGIN = 24 // the px-3 gutter either side of the island
/** How long the bar waits after the last scroll tick before unfurling again. */
const SETTLE_MS = 220

interface Slot {
  key: string
  label: string
  icon: NavItem['icon']
  action?: boolean
  to?: string
  onClick?: () => void
}

/** True while the page is being scrolled, flipping back a beat after it stops. */
function useScrolling(settle = SETTLE_MS) {
  const [scrolling, setScrolling] = useState(false)

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>
    const onScroll = () => {
      setScrolling(true)
      clearTimeout(timer)
      timer = setTimeout(() => setScrolling(false), settle)
    }
    // Captured on the document so a scrollable panel inside a page counts too, not just the
    // window's own scroll.
    document.addEventListener('scroll', onScroll, { passive: true, capture: true })
    return () => {
      document.removeEventListener('scroll', onScroll, { capture: true })
      clearTimeout(timer)
    }
  }, [settle])

  return scrolling
}

export function MobileTabBar({
  tabs,
  overflow,
  moreOpen,
  onMore,
}: {
  tabs: NavItem[]
  overflow: NavItem[]
  moreOpen: boolean
  onMore: () => void
}) {
  const { pathname } = useLocation()
  const labelRefs = useRef<(HTMLSpanElement | null)[]>([])
  const [labelWidth, setLabelWidth] = useState(0)
  const [viewport, setViewport] = useState(() => window.innerWidth)
  const [ready, setReady] = useState(false)
  const compact = useScrolling()

  const slots: Slot[] = [
    ...tabs.map(({ to, label, short, icon, action }) => ({
      key: to,
      to,
      label: short ?? label,
      icon,
      action,
    })),
    ...(overflow.length > 0
      ? [{ key: 'more', label: 'More', icon: MoreHorizontal, onClick: onMore }]
      : []),
  ]
  const slotKeys = slots.map((s) => s.key).join()

  const active = slots.findIndex((s) =>
    s.to ? Boolean(matchPath({ path: s.to, end: true }, pathname)) : moreOpen,
  )

  // Labels are static text, so one measurement is enough. The widest one sizes every tab, which
  // keeps the island evenly divided instead of ragged — and lets the pill be a single number.
  useLayoutEffect(() => {
    const widest = labelRefs.current.reduce((w, el) => Math.max(w, el?.scrollWidth ?? 0), 0)
    setLabelWidth(Math.ceil(widest))
    const id = requestAnimationFrame(() => setReady(true))
    return () => cancelAnimationFrame(id)
  }, [slotKeys])

  useEffect(() => {
    const onResize = () => setViewport(window.innerWidth)
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  const room = (viewport - SIDE_MARGIN - PAD * 2 - GAP * (slots.length - 1)) / slots.length
  const tabW = compact
    ? TAB_W_COMPACT
    : Math.max(TAB_W_MIN, Math.min(TAB_W_MAX, Math.floor(room), labelWidth + 16))
  const tabH = compact ? TAB_H_COMPACT : TAB_H

  const move = ready ? `${DURATION}ms` : '0ms'

  return (
    <nav
      aria-label="Main"
      className="pointer-events-none fixed inset-x-0 bottom-0 z-30 flex justify-center px-3"
      style={{ paddingBottom: 'max(0.75rem, env(safe-area-inset-bottom))' }}
    >
      <div
        className={cn(
          'pointer-events-auto relative flex max-w-full items-center overflow-hidden rounded-full',
          'border border-border/60 bg-white/80 shadow-lg shadow-foreground/10 backdrop-blur-xl',
        )}
        // The island's own size just tracks its children, which are the things being animated.
        style={{ padding: PAD, gap: GAP }}
      >
        {/* One pill for the whole island: it glides between tabs rather than each tab lighting up
            on its own, so a tab change reads as a single continuous move. */}
        {active >= 0 && (
          <span
            aria-hidden
            className="absolute left-0 top-1/2 rounded-full bg-foreground"
            style={{
              width: tabW,
              height: tabH,
              transform: `translate3d(${PAD + active * (tabW + GAP)}px, -50%, 0)`,
              transition: `transform ${move} cubic-bezier(0.32,0.72,0,1), width ${move} cubic-bezier(0.32,0.72,0,1), height ${move} cubic-bezier(0.32,0.72,0,1)`,
            }}
          />
        )}

        {slots.map((slot, i) => {
          const isActive = i === active
          // The create action is a lime disc as tall as the bar's inner height — one button
          // rather than a fifth destination. On its own screen it drops that disc and takes the
          // same black pill, icon and label as every other selected tab.
          const content =
            slot.action && !isActive ? (
              <span
                className="flex items-center justify-center rounded-full bg-accent text-accent-foreground"
                style={{
                  width: tabH,
                  height: tabH,
                  transition: `width ${move} cubic-bezier(0.32,0.72,0,1), height ${move} cubic-bezier(0.32,0.72,0,1)`,
                }}
              >
                <Plus className="h-[22px] w-[22px]" strokeWidth={2.5} aria-hidden />
              </span>
            ) : slot.action ? (
              // Selected, it is the same black pill as any other tab — but a bare plus, since
              // the glyph already says what it does.
              <Plus className="h-[22px] w-[22px] shrink-0" strokeWidth={2.5} aria-hidden />
            ) : (
              <>
                <slot.icon className="h-[18px] w-[18px] shrink-0" aria-hidden />
                {/* Collapsed to zero height rather than unmounted, so the label can fold away and
                    unfold with the same curve the island resizes on. */}
                <span
                  ref={(el) => {
                    labelRefs.current[i] = el
                  }}
                  className="w-full overflow-hidden whitespace-nowrap px-1 text-center text-[11px] font-semibold leading-[14px] tracking-tight"
                  style={{
                    height: compact ? 0 : LABEL_H,
                    opacity: compact ? 0 : 1,
                    marginTop: compact ? 0 : 3,
                    transition: `height ${move} cubic-bezier(0.32,0.72,0,1), opacity ${move} cubic-bezier(0.32,0.72,0,1), margin-top ${move} cubic-bezier(0.32,0.72,0,1)`,
                  }}
                >
                  {slot.label}
                </span>
              </>
            )

          const className = cn(
            'relative z-10 flex shrink-0 flex-col items-center justify-center rounded-full active:scale-95',
            isActive ? 'text-background' : 'text-muted-foreground',
          )
          const style = {
            width: tabW,
            height: tabH,
            transition: `width ${move} cubic-bezier(0.32,0.72,0,1), height ${move} cubic-bezier(0.32,0.72,0,1), color 200ms cubic-bezier(0.32,0.72,0,1), transform 180ms cubic-bezier(0.32,0.72,0,1)`,
          }

          return slot.to ? (
            <Link
              key={slot.key}
              to={slot.to}
              aria-current={isActive ? 'page' : undefined}
              aria-label={slot.label}
              className={className}
              style={style}
            >
              {content}
            </Link>
          ) : (
            <button
              key={slot.key}
              type="button"
              onClick={slot.onClick}
              aria-haspopup="dialog"
              aria-expanded={moreOpen}
              aria-label="More"
              className={className}
              style={style}
            >
              {content}
            </button>
          )
        })}
      </div>
    </nav>
  )
}
