import { MoreHorizontal, Plus } from 'lucide-react'
import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, matchPath, useLocation } from 'react-router-dom'
import { cn } from '@/lib/cn'
import type { NavItem } from './nav'

/**
 * Geometry of the island, kept in JS so everything that moves is driven by the same few numbers
 * and the same curve. Nothing here is laid out by the browser's own flow — the pill would then
 * have to guess where a tab is going to end up mid-flight.
 */
const EASE = 'cubic-bezier(0.32,0.72,0,1)'
const DURATION = 420
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
  // Whichever persona has a create action, it sits dead centre of the bar — the one slot both
  // thumbs reach. Personas without one (courier, dispatcher, admin) are left in nav order.
  const actionAt = slots.findIndex((s) => s.action)
  if (actionAt >= 0) {
    const middle = Math.floor(slots.length / 2)
    slots.splice(middle, 0, ...slots.splice(actionAt, 1))
  }

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
  const barW = PAD * 2 + slots.length * tabW + (slots.length - 1) * GAP
  const pillX = PAD + Math.max(active, 0) * (tabW + GAP)

  const ms = ready ? DURATION : 0
  const tr = (...props: string[]) => props.map((p) => `${p} ${ms}ms ${EASE}`).join(', ')

  /**
   * The row is rendered twice: once in the resting colour, and once in the on-pill colour clipped
   * to the pill itself. Nothing recolours on a tab change — the pill simply reveals the second
   * copy as it travels, so a label is the right colour at every pixel of the journey instead of
   * flipping to white before the black has arrived under it.
   */
  const row = (onPill: boolean) =>
    slots.map((slot, i) => {
      const isActive = i === active
      const content =
        slot.action && !(onPill && isActive) ? (
          // The create action is a lime disc as tall as the bar's inner height — one button
          // rather than a fifth destination. Under the pill it is a bare plus like any glyph.
          <span
            className="flex items-center justify-center rounded-full bg-accent text-accent-foreground"
            style={{ width: tabH, height: tabH, transition: tr('width', 'height') }}
          >
            <Plus className="h-[22px] w-[22px]" strokeWidth={2.5} aria-hidden />
          </span>
        ) : slot.action ? (
          <Plus className="h-[22px] w-[22px] shrink-0" strokeWidth={2.5} aria-hidden />
        ) : (
          <>
            <slot.icon className="h-[18px] w-[18px] shrink-0" aria-hidden />
            {/* Collapsed to zero height rather than unmounted, so the label can fold away and
                unfold with the same curve the island resizes on. */}
            <span
              ref={
                onPill
                  ? undefined
                  : (el) => {
                      labelRefs.current[i] = el
                    }
              }
              className="w-full overflow-hidden whitespace-nowrap px-1 text-center text-[11px] font-semibold leading-[14px] tracking-tight"
              style={{
                height: compact ? 0 : LABEL_H,
                opacity: compact ? 0 : 1,
                marginTop: compact ? 0 : 3,
                transition: tr('height', 'opacity', 'margin-top'),
              }}
            >
              {slot.label}
            </span>
          </>
        )

      const className = cn(
        'flex shrink-0 flex-col items-center justify-center rounded-full',
        onPill ? 'text-background' : 'text-muted-foreground',
      )
      const style = { width: tabW, height: tabH, transition: tr('width', 'height') }

      if (onPill) {
        return (
          <span key={slot.key} className={className} style={style}>
            {content}
          </span>
        )
      }

      return slot.to ? (
        <Link
          key={slot.key}
          to={slot.to}
          aria-current={isActive ? 'page' : undefined}
          aria-label={slot.label}
          className={cn(className, 'transition-transform duration-150 active:scale-90')}
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
          className={cn(className, 'transition-transform duration-150 active:scale-90')}
          style={style}
        >
          {content}
        </button>
      )
    })

  const rowStyle = { padding: PAD, gap: GAP }

  return (
    <nav
      aria-label="Main"
      className="pointer-events-none fixed inset-x-0 bottom-0 z-30 flex justify-center px-3"
      style={{ paddingBottom: 'max(0.75rem, env(safe-area-inset-bottom))' }}
    >
      <div
        className={cn(
          'pointer-events-auto relative max-w-full overflow-hidden rounded-full',
          'border border-border/60 bg-white/80 shadow-lg shadow-foreground/10 backdrop-blur-xl',
        )}
      >
        {/* Resting layer: the real links, always in the resting colour. */}
        <div className="flex items-center" style={rowStyle}>
          {row(false)}
        </div>

        {/* One pill for the whole island, gliding between tabs over the resting layer. */}
        {active >= 0 && (
          <span
            aria-hidden
            className="pointer-events-none absolute left-0 top-1/2 rounded-full bg-foreground"
            style={{
              width: tabW,
              height: tabH,
              transform: `translate3d(${pillX}px, -50%, 0)`,
              transition: tr('transform', 'width', 'height'),
              willChange: 'transform, width',
            }}
          />
        )}

        {/* On-pill layer, clipped to exactly where the pill is. */}
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 flex items-center"
          style={{
            ...rowStyle,
            clipPath:
              active >= 0
                ? `inset(${PAD}px ${barW - pillX - tabW}px ${PAD}px ${pillX}px round 9999px)`
                : `inset(50% round 9999px)`,
            transition: tr('clip-path'),
          }}
        >
          {row(true)}
        </div>
      </div>
    </nav>
  )
}
