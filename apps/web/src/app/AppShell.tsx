import { Menu, Package, X } from 'lucide-react'
import { lazy, Suspense, useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/auth/authContext'
import { useSession } from '@/auth/session'
import { env } from '@/config/env'
import { cn } from '@/lib/cn'
import { navFor } from './nav'

// The assistant is never needed for first paint, so it ships as its own chunk.
const ChatWidget = lazy(() =>
  import('@/features/assistant/ChatWidget').then((m) => ({ default: m.ChatWidget })),
)

export function AppShell() {
  const { roles, email } = useSession()
  const { logout, status } = useAuth()
  const [open, setOpen] = useState(false)
  const items = navFor(roles)

  const nav = (
    <nav aria-label="Main" className="flex flex-col gap-1 p-3">
      {items.map(({ to, label, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          end
          onClick={() => setOpen(false)}
          className={({ isActive }) =>
            cn(
              'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium hover:bg-accent',
              isActive && 'bg-accent text-accent-foreground',
            )
          }
        >
          <Icon className="h-4 w-4" aria-hidden />
          {label}
        </NavLink>
      ))}
    </nav>
  )

  return (
    <div className="min-h-screen md:grid md:grid-cols-[240px_1fr]">
      <aside className="hidden border-r md:block">
        <Link to="/" className="flex h-14 items-center gap-2 border-b px-5 font-semibold">
          <Package className="h-5 w-5 text-primary" aria-hidden /> SwiftDrop
        </Link>
        {nav}
      </aside>
      <div className="flex min-w-0 flex-col">
        <header className="flex h-14 items-center justify-between border-b px-4">
          <Button
            variant="ghost"
            size="icon"
            className="md:hidden"
            aria-label="Open menu"
            onClick={() => setOpen(true)}
          >
            <Menu className="h-5 w-5" />
          </Button>
          <span className="font-semibold md:hidden">SwiftDrop</span>
          <div className="ml-auto flex items-center gap-2">
            {roles.length === 0 ? (
              <Button asChild size="sm">
                <Link to="/login">Sign in</Link>
              </Button>
            ) : (
              <>
                <Link
                  to="/account"
                  className="hidden text-sm text-muted-foreground hover:underline sm:inline"
                >
                  {email}
                </Link>
                <Button variant="outline" size="sm" onClick={() => void logout()}>
                  Sign out
                </Button>
              </>
            )}
          </div>
        </header>
        <main className="mx-auto w-full max-w-6xl flex-1 p-4 md:p-8">
          <Outlet />
        </main>
      </div>
      {status === 'authenticated' && env.assistantEnabled && (
        <Suspense fallback={null}>
          <ChatWidget />
        </Suspense>
      )}
      {open && (
        <div
          className="fixed inset-0 z-40 md:hidden"
          role="dialog"
          aria-modal="true"
          aria-label="Menu"
        >
          <button
            type="button"
            aria-label="Close menu"
            tabIndex={-1}
            className="absolute inset-0 h-full w-full bg-black/50"
            onClick={() => setOpen(false)}
          />
          <div className="relative h-full w-64 bg-background shadow-xl">
            <div className="flex h-14 items-center justify-between border-b px-4">
              <span className="font-semibold">SwiftDrop</span>
              <Button
                variant="ghost"
                size="icon"
                aria-label="Close menu"
                onClick={() => setOpen(false)}
              >
                <X className="h-5 w-5" />
              </Button>
            </div>
            {nav}
          </div>
        </div>
      )}
    </div>
  )
}

/** Chrome-free layout for the public tracking page. */
export function PublicShell() {
  return (
    <div className="min-h-screen bg-background">
      <header className="flex h-12 items-center gap-2 px-4 font-semibold">
        <Package className="h-5 w-5 text-primary" aria-hidden /> SwiftDrop
      </header>
      <main className="mx-auto w-full max-w-xl p-4">
        <Outlet />
      </main>
    </div>
  )
}
