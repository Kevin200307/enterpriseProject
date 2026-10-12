import { ArrowLeft, Bell, LogOut, PanelLeftClose, PanelLeftOpen } from 'lucide-react'
import { lazy, Suspense, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate, useNavigation } from 'react-router-dom'
import { LogoLockup, LogoMark, LogoTile } from '@/components/brand/Logo'
import { LoadingScreen } from '@/components/common/LoadingScreen'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { useAuth } from '@/auth/authContext'
import { useSession } from '@/auth/session'
import { useMobile } from '@/hooks/useMobile'
import { env } from '@/config/env'
import { cn } from '@/lib/cn'
import { MobileTabBar } from './MobileTabBar'
import { mobileNavFor, navFor, navMatchFor } from './nav'

const ChatWidget = lazy(() =>
  import('@/features/assistant/ChatWidget').then((m) => ({ default: m.ChatWidget })),
)

const COLLAPSE_KEY = 'swiftdrop.sidebar.collapsed'

/** The lime wash's colour where it meets the top of the screen, flattened onto white. */
const HOME_THEME = '#edfcb0'
const PLAIN_THEME = '#ffffff'

/**
 * The concave corners above and below the active tab, so the white tab appears to flow out of
 * the page rather than sit on top of the rail. Each piece is a page-coloured square with a
 * sidebar-coloured square on top whose facing corner is rounded, carving out a quarter circle.
 */
function ActiveTabNotch() {
  return (
    <>
      <span
        aria-hidden
        className="pointer-events-none absolute -top-4 right-0 h-4 w-4 bg-background"
      >
        <span className="block h-full w-full rounded-br-2xl bg-sidebar" />
      </span>
      <span
        aria-hidden
        className="pointer-events-none absolute -bottom-4 right-0 h-4 w-4 bg-background"
      >
        <span className="block h-full w-full rounded-tr-2xl bg-sidebar" />
      </span>
    </>
  )
}

/** Initial-bearing stand-in for a profile picture, for the rail's account row. */
function Avatar({ email }: { email?: string }) {
  return (
    <span
      aria-hidden
      className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-white/10 text-xs font-semibold uppercase text-white"
    >
      {email?.[0] ?? '?'}
    </span>
  )
}

export function AppShell() {
  const { roles, email } = useSession()
  const { logout, status } = useAuth()
  const [open, setOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem(COLLAPSE_KEY) === '1')
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const navigation = useNavigation()
  const isMobile = useMobile()
  const navRef = useRef<HTMLElement>(null)
  const [indicator, setIndicator] = useState<{ top: number; height: number } | null>(null)
  const [indicatorReady, setIndicatorReady] = useState(false)
  const items = navFor(roles)
  const { tabs, overflow } = mobileNavFor(roles)
  // The persona's first bottom-bar tab is their home; only there does the header carry the
  // full lockup — every other screen trades it for the mark plus that screen's name.
  const homePath = tabs[0]?.to
  const onHome = pathname === homePath
  const onProfile = pathname === '/account'
  const section = navMatchFor(pathname, roles)
  const title = section?.label ?? 'SwiftDrop'
  // "Inside" means anywhere that is not one of the bar's own tabs: a detail screen reached by
  // tapping something, but also a section that only lives in the More sheet or the profile page.
  // Those all get a way back rather than a section badge, since the bar cannot return you.
  const inside = !onHome && !tabs.some((t) => t.to === pathname)

  /**
   * Prefer real history so back undoes the tap that got here, but a deep link has nothing behind
   * it in this app — then fall back to the section the route belongs to.
   */
  const goBack = () => {
    const idx = (window.history.state as { idx?: number } | null)?.idx ?? 0
    if (idx > 0) return navigate(-1)
    // A section that is its own page has nowhere above it but home.
    const up = section && section.to !== pathname ? section.to : homePath
    navigate(up ?? '/')
  }

  useEffect(() => {
    localStorage.setItem(COLLAPSE_KEY, collapsed ? '1' : '0')
  }, [collapsed])

  // Carries the wash up into the phone's own status bar, so the screen reads as one colour
  // rather than a lime page under a white strip.
  useEffect(() => {
    document
      .querySelector('meta[name="theme-color"]')
      ?.setAttribute('content', onHome ? HOME_THEME : PLAIN_THEME)
  }, [onHome])

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setOpen(false)
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  // Park the sliding indicator over whichever item is active. It only animates after the first
  // placement, so it doesn't fly in from the top on mount.
  useLayoutEffect(() => {
    const active = navRef.current?.querySelector<HTMLElement>('.is-active')
    if (!active) return setIndicator(null)
    setIndicator({ top: active.offsetTop, height: active.offsetHeight })
    const id = requestAnimationFrame(() => setIndicatorReady(true))
    return () => cancelAnimationFrame(id)
  }, [pathname, collapsed, isMobile, open, items.length])

  if (status === 'loading' || navigation.state === 'loading') return <LoadingScreen />

  const sidebarToggle = (
    <Button
      variant="ghost"
      size="icon"
      className={cn(
        'rounded-xl text-white/70 hover:bg-white/10 hover:text-white active:scale-90',
        collapsed && 'h-10 w-full', // spans the rail's inner width, centring on the icon axis
      )}
      aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
      aria-expanded={!collapsed}
      title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
      onClick={() => setCollapsed((c) => !c)}
    >
      {collapsed ? (
        <PanelLeftOpen key="open" className="h-[18px] w-[18px] animate-scale-in" />
      ) : (
        <PanelLeftClose key="close" className="h-[18px] w-[18px] animate-scale-in" />
      )}
    </Button>
  )
  /**
   * One active pill slides between items instead of each item painting its own, so switching tabs
   * is a continuous move rather than a jump. `rail` collapses labels to zero width (not unmounted)
   * so that change can animate too.
   */
  const sidebarNav = (rail = false) => (
    <nav ref={navRef} aria-label="Main" className="relative flex flex-col gap-1 py-3 pl-3">
      {indicator && (
        <div
          aria-hidden
          className={cn(
            'pointer-events-none absolute left-3 right-0 top-0 rounded-l-2xl bg-background',
            indicatorReady && 'transition-[transform,height] duration-300 ease-smooth',
          )}
          style={{ height: indicator.height, transform: `translateY(${indicator.top}px)` }}
        >
          <ActiveTabNotch />
        </div>
      )}
      {items.map(({ to, label, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          end
          onClick={() => setOpen(false)}
          title={rail ? label : undefined}
          className={({ isActive }) =>
            cn(
              'relative z-10 flex items-center gap-3 rounded-2xl py-2.5 text-sm',
              'transition-colors duration-300 ease-smooth',
              // Same 14px lead-in whether expanded or railed, so every icon — active row
              // included — sits on one axis and nothing shifts when the rail toggles.
              // (justify-center would push the active row right, since it has no end margin.)
              rail ? 'pl-[14px] pr-0' : 'pl-[14px] pr-3',
              isActive
                ? 'is-active font-semibold text-foreground'
                : 'mr-3 font-medium text-white/60 hover:bg-white/10 hover:text-white active:scale-[0.98]',
            )
          }
        >
          {({ isActive }) => (
            <>
              <Icon
                className={cn(
                  'h-4 w-4 shrink-0',
                  to === '/account/notifications' && isActive && 'fill-current',
                )}
                aria-hidden
              />
              <span
                className={cn(
                  'overflow-hidden whitespace-nowrap',
                  'transition-[max-width,opacity,transform] duration-300 ease-smooth',
                  rail
                    ? 'max-w-0 -translate-x-1 opacity-0'
                    : 'max-w-[160px] translate-x-0 opacity-100',
                )}
              >
                {label}
              </span>
            </>
          )}
        </NavLink>
      ))}
    </nav>
  )

  return (
    <div
      className={cn(
        'min-h-screen',
        // Animating the aside's width is far smoother (and better supported) than
        // interpolating grid-template-columns, so the column just tracks its content.
        !isMobile && 'md:grid md:grid-cols-[auto_1fr]',
      )}
    >
      {/* Desktop sidebar — width is driven solely by the toggle, never by hover */}
      {!isMobile && (
        <aside
          className={cn(
            'sticky top-0 hidden h-screen flex-col bg-sidebar text-white md:flex',
            'transition-[width] duration-300 ease-smooth will-change-[width]',
            collapsed ? 'md:w-[68px]' : 'md:w-[240px]',
          )}
        >
          {/* pl-[18px] puts the lockup's parcel on the same 34px axis as the nav icons, so
              expanding only reveals the wordmark — the mark itself never moves. */}
          <div className={cn('flex h-16 items-center pl-[18px]', collapsed ? 'pr-0' : 'pr-3')}>
            <Link to="/" aria-label="SwiftDrop home" className="flex items-center">
              <LogoLockup
                className={cn(
                  'h-9 shrink-0 text-white transition-[width] duration-300 ease-smooth',
                  collapsed ? 'w-[30px]' : 'w-[119px]',
                )}
              />
            </Link>
            {!collapsed && <div className="ml-auto">{sidebarToggle}</div>}
          </div>

          {/* Collapsed, the toggle tucks under the logo — the header row has no space beside it. */}
          {collapsed && <div className="flex px-3 pb-1">{sidebarToggle}</div>}

          <div className="flex-1 overflow-y-auto">{sidebarNav(collapsed)}</div>

          {/* The account row lives at the foot of the rail — the desktop layout has no top bar. */}
          <div className="border-t border-white/10 p-3">
            {roles.length === 0 ? (
              <Button asChild size="sm" className="w-full rounded-xl">
                <Link to="/login">{collapsed ? 'In' : 'Sign in'}</Link>
              </Button>
            ) : collapsed ? (
              <Link
                to="/account"
                title={email}
                aria-label="Account"
                className="flex h-10 w-full items-center justify-center rounded-xl hover:bg-white/10"
              >
                <Avatar email={email} />
              </Link>
            ) : (
              <div className="flex items-center gap-1">
                <Link
                  to="/account"
                  title={email}
                  className="flex min-w-0 flex-1 items-center gap-2 rounded-xl p-1.5 hover:bg-white/10"
                >
                  <Avatar email={email} />
                  <span className="truncate text-xs text-white/60">{email}</span>
                </Link>
                <Button
                  variant="ghost"
                  size="icon"
                  className="shrink-0 rounded-xl text-destructive hover:bg-destructive/10 hover:text-destructive active:!bg-destructive active:!text-destructive-foreground active:scale-90"
                  aria-label="Sign out"
                  title="Sign out"
                  onClick={() => void logout()}
                >
                  <LogOut className="h-[18px] w-[18px]" />
                </Button>
              </div>
            )}
          </div>
        </aside>
      )}

      <div className="relative flex min-w-0 flex-col">
        {/* Top header — mobile only; on desktop the rail carries the account row. */}
        {isMobile && (
          <header
            className={cn(
              'sticky top-0 z-20 flex h-14 items-center justify-between px-4',
              'h-[calc(3.5rem+env(safe-area-inset-top))] pt-[env(safe-area-inset-top)]',
              'bg-background',
            )}
          >
            {/* No hamburger — everything routes through the bottom bar. */}
            {onHome ? (
              // The flat SVG file cannot adapt its lime to a light bar, so the header uses the
              // component with its on-light tone instead.
              <LogoLockup className="h-8 w-[114px] text-sidebar dark:text-white" onLight />
            ) : (
              <span className="flex min-w-0 items-center gap-2">
                {inside ? (
                  <Button
                    variant="ghost"
                    size="icon"
                    className="-ml-2 shrink-0 rounded-xl"
                    aria-label={section ? `Back to ${section.label}` : 'Back'}
                    onClick={goBack}
                  >
                    <ArrowLeft className="h-5 w-5" />
                  </Button>
                ) : (
                  <LogoMark className="h-7 w-7 shrink-0 text-sidebar dark:text-white" onLight />
                )}
                <span className="truncate font-semibold">{title}</span>
              </span>
            )}
            {roles.length === 0 ? (
              <Button asChild size="sm" className="rounded-xl">
                <Link to="/login">Sign in</Link>
              </Button>
            ) : (
              <Button variant="ghost" size="icon" asChild className="rounded-xl">
                <Link to="/account/notifications" aria-label="Notifications">
                  <Bell className={cn('h-5 w-5', pathname === '/account/notifications' && 'fill-current')} />
                </Link>
              </Button>
            )}
          </header>
        )}

        {/* Page content */}
        <main
          className={cn(
            'mx-auto w-full max-w-6xl flex-1 p-4',
            isMobile ? 'pb-24 md:p-6' : 'md:p-8',
          )}
        >
          {/* Keyed on the path so each navigation replays the entrance animation. */}
          <div key={pathname} className="animate-fade-in-up">
            <Outlet />
          </div>
        </main>
      </div>

      {/* Mobile bottom nav */}
      {isMobile && (
        <MobileTabBar
          tabs={tabs}
          overflow={overflow}
          moreOpen={open}
          onMore={() => setOpen(true)}
        />
      )}

      {/* Overflow sheet */}
      {isMobile && (
        <Dialog open={open} onOpenChange={setOpen} title="More" side="bottom">
          <nav aria-label="More destinations" className="flex flex-col pb-2">
            {overflow.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                end
                onClick={() => setOpen(false)}
                className={({ isActive }) =>
                  cn(
                    'flex items-center gap-3 rounded-2xl px-3 py-3.5 text-sm font-medium transition-colors',
                    isActive ? 'bg-secondary font-semibold' : 'hover:bg-secondary',
                  )
                }
              >
                <Icon className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                {label}
              </NavLink>
            ))}
            <button
              type="button"
              onClick={() => void logout()}
              className="mt-2 flex items-center gap-3 rounded-2xl px-3 py-3.5 text-sm font-medium text-destructive transition-colors hover:bg-destructive/10 active:!bg-destructive active:!text-destructive-foreground"
            >
              <LogOut className="h-4 w-4 shrink-0" aria-hidden />
              Sign out
            </button>
          </nav>
        </Dialog>
      )}

      {/* On a phone the launcher belongs to the tab screens only — inside a section the thumb is
          busy with that screen's own actions, and the bubble would sit over them. */}
      {status === 'authenticated' && env.assistantEnabled && !onProfile && (!isMobile || !inside) && (
        <Suspense fallback={null}>
          <ChatWidget />
        </Suspense>
      )}
    </div>
  )
}

/** Chrome-free layout for the public tracking page. */
export function PublicShell() {
  const navigate = useNavigate()
  const navigation = useNavigation()
  // Deep-linked from a shared link there is nothing behind this page, so back lands on lookup.
  const goBack = () => {
    const idx = (window.history.state as { idx?: number } | null)?.idx ?? 0
    if (idx > 0) navigate(-1)
    else navigate('/track')
  }

  if (navigation.state === 'loading') return <LoadingScreen />

  return (
    <div className="min-h-screen bg-background">
      <header className="sticky top-0 z-20 flex h-14 items-center gap-2 bg-background px-2 pt-[env(safe-area-inset-top)] font-semibold">
        <Button
          variant="ghost"
          size="icon"
          className="rounded-xl"
          aria-label="Back"
          onClick={goBack}
        >
          <ArrowLeft className="h-5 w-5" />
        </Button>
        <LogoTile />
        SwiftDrop
      </header>
      <main className="mx-auto w-full max-w-xl p-4">
        <Outlet />
      </main>
    </div>
  )
}
