import { Bell, LogOut, MoreHorizontal, PanelLeftClose, PanelLeftOpen } from 'lucide-react'
import { lazy, Suspense, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'
import { LogoLockup, LogoTile } from '@/components/brand/Logo'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { useAuth } from '@/auth/authContext'
import { useSession } from '@/auth/session'
import { useMobile } from '@/hooks/useMobile'
import { env } from '@/config/env'
import { cn } from '@/lib/cn'
import { mobileNavFor, navFor } from './nav'

const ChatWidget = lazy(() =>
  import('@/features/assistant/ChatWidget').then((m) => ({ default: m.ChatWidget })),
)

const COLLAPSE_KEY = 'swiftdrop.sidebar.collapsed'

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

export function AppShell() {
  const { roles, email } = useSession()
  const { logout, status } = useAuth()
  const [open, setOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(
    () => localStorage.getItem(COLLAPSE_KEY) === '1',
  )
  const { pathname } = useLocation()
  const isMobile = useMobile()
  const navRef = useRef<HTMLElement>(null)
  const [indicator, setIndicator] = useState<{ top: number; height: number } | null>(null)
  const [indicatorReady, setIndicatorReady] = useState(false)
  const items = navFor(roles)
  const { tabs, overflow } = mobileNavFor(roles)

  useEffect(() => {
    localStorage.setItem(COLLAPSE_KEY, collapsed ? '1' : '0')
  }, [collapsed])

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
   * One white pill slides between items instead of each item painting its own, so switching tabs
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
          <Icon className="h-4 w-4 shrink-0" aria-hidden />
          <span
            className={cn(
              'overflow-hidden whitespace-nowrap',
              'transition-[max-width,opacity,transform] duration-300 ease-smooth',
              rail ? 'max-w-0 -translate-x-1 opacity-0' : 'max-w-[160px] translate-x-0 opacity-100',
            )}
          >
            {label}
          </span>
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
            'hidden overflow-y-auto bg-sidebar text-white md:block',
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

          {sidebarNav(collapsed)}
        </aside>
      )}

      <div className="flex min-w-0 flex-col">
        {/* Top header */}
        <header className="flex h-14 items-center justify-between border-b border-border/40 bg-white/80 px-4 backdrop-blur-md">
          {/* No hamburger on mobile — everything routes through the bottom bar. */}
          {isMobile ? (
            <Link to="/" aria-label="SwiftDrop home" className="flex items-center gap-2">
              <LogoTile />
              <span className="font-semibold">SwiftDrop</span>
            </Link>
          ) : (
            <span />
          )}
          <div className="flex items-center gap-2">
            {roles.length === 0 ? (
              <Button asChild size="sm" className="rounded-xl">
                <Link to="/login">Sign in</Link>
              </Button>
            ) : isMobile ? (
              <Button variant="ghost" size="icon" asChild className="rounded-xl">
                <Link to="/account/notifications" aria-label="Notifications">
                  <Bell className="h-5 w-5" />
                </Link>
              </Button>
            ) : (
              <>
                <Link
                  to="/account"
                  className="hidden text-sm text-muted-foreground hover:underline sm:inline"
                >
                  {email}
                </Link>
                <Button
                  variant="outline"
                  size="sm"
                  className="rounded-xl"
                  onClick={() => void logout()}
                >
                  Sign out
                </Button>
              </>
            )}
          </div>
        </header>

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
        <nav
          aria-label="Main"
          className="fixed bottom-0 left-0 right-0 z-30 flex items-center justify-around border-t border-border/40 bg-white/90 px-2 pt-2 backdrop-blur-md"
          style={{ paddingBottom: 'max(0.5rem, env(safe-area-inset-bottom))' }}
        >
          {tabs.map(({ to, label, short, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              end
              className={({ isActive }) =>
                cn(
                  'flex flex-1 flex-col items-center gap-0.5 rounded-xl py-1 text-[10px] font-medium text-muted-foreground',
                  'duration-300 ease-smooth active:scale-90',
                  isActive && 'text-foreground',
                )
              }
            >
              {({ isActive }) => (
                <>
                  <span
                    className={cn(
                      'flex h-9 w-9 items-center justify-center rounded-full duration-300 ease-smooth',
                      isActive ? 'bg-foreground text-background' : 'scale-95',
                    )}
                  >
                    <Icon className="h-4 w-4" aria-hidden />
                  </span>
                  <span className="max-w-full truncate px-0.5">{short ?? label}</span>
                </>
              )}
            </NavLink>
          ))}

          {/* Anything that does not fit becomes the fifth slot. */}
          {overflow.length > 0 && (
            <button
              type="button"
              onClick={() => setOpen(true)}
              aria-haspopup="dialog"
              aria-expanded={open}
              className={cn(
                'flex flex-1 flex-col items-center gap-0.5 rounded-xl py-1 text-[10px] font-medium text-muted-foreground',
                'duration-300 ease-smooth active:scale-90',
                open && 'text-foreground',
              )}
            >
              <span
                className={cn(
                  'flex h-9 w-9 items-center justify-center rounded-full duration-300 ease-smooth',
                  open ? 'bg-foreground text-background' : 'scale-95',
                )}
              >
                <MoreHorizontal className="h-4 w-4" aria-hidden />
              </span>
              <span className="max-w-full truncate px-0.5">More</span>
            </button>
          )}
        </nav>
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
              className="mt-2 flex items-center gap-3 rounded-2xl px-3 py-3.5 text-sm font-medium text-destructive transition-colors hover:bg-destructive/5"
            >
              <LogOut className="h-4 w-4 shrink-0" aria-hidden />
              Sign out
            </button>
          </nav>
        </Dialog>
      )}

      {status === 'authenticated' && env.assistantEnabled && (
        <Suspense fallback={null}>
          <ChatWidget />
        </Suspense>
      )}

    </div>
  )
}

/** Chrome-free layout for the public tracking page. */
export function PublicShell() {
  return (
    <div className="min-h-screen bg-background">
      <header className="flex h-12 items-center gap-2 px-4 font-semibold">
        <LogoTile />
        SwiftDrop
      </header>
      <main className="mx-auto w-full max-w-xl p-4">
        <Outlet />
      </main>
    </div>
  )
}
