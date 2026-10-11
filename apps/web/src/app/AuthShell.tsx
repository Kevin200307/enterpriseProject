import { Link, Outlet } from 'react-router-dom'
import { LogoLockup } from '@/components/brand/Logo'

/** Full-screen auth layout — mobile-first card style matching the app's visual design system. */
export function AuthShell() {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden bg-surface px-5 py-10">
      {/* Lime accent glow, the one spot of colour on an otherwise monochrome screen */}
      <div
        aria-hidden
        className="absolute -right-24 -top-24 h-80 w-80 rounded-full bg-accent opacity-40 blur-3xl"
      />
      <div
        aria-hidden
        className="absolute -bottom-28 -left-16 h-72 w-72 rounded-full bg-foreground opacity-[0.04] blur-3xl"
      />

      {/* Logo */}
      <Link
        to="/"
        aria-label="SwiftDrop home"
        className="relative mb-8 flex items-center rounded-2xl bg-sidebar px-5 py-3"
      >
        <LogoLockup className="h-8 w-[106px] text-white" />
      </Link>

      {/* Card */}
      <main className="relative w-full max-w-sm rounded-3xl border border-border bg-card p-8 shadow-[0_18px_50px_-24px_rgba(0,0,0,0.25)]">
        <Outlet />
      </main>

      <p className="relative mt-6 text-xs text-muted-foreground">
        University of Moratuwa · Enterprise Application Development
      </p>
    </div>
  )
}
