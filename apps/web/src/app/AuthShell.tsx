import { Link, Outlet } from 'react-router-dom'
import { LogoLockup } from '@/components/brand/Logo'

/** Full-screen auth layout — modern, clean card style with ambient accents. */
export function AuthShell() {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center overflow-hidden bg-surface px-4 py-8 sm:py-12">
      {/* Ambient background glows for modern depth */}
      <div
        aria-hidden
        className="pointer-events-none absolute -top-40 left-1/2 h-[32rem] w-[40rem] -translate-x-1/2 rounded-full bg-accent/20 blur-[120px]"
      />
      <div
        aria-hidden
        className="pointer-events-none absolute -bottom-32 right-1/4 h-80 w-80 rounded-full bg-primary/5 blur-[100px]"
      />

      {/* Subtle background tech grid */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-0 bg-[linear-gradient(to_right,#8080800a_1px,transparent_1px),linear-gradient(to_bottom,#8080800a_1px,transparent_1px)] bg-[size:28px_28px]"
      />

      {/* Brand logo header */}
      <Link
        to="/"
        aria-label="SwiftDrop home"
        className="group relative mb-6 flex items-center rounded-2xl bg-sidebar px-5 py-2.5 shadow-md transition-all hover:bg-black hover:shadow-lg active:scale-[0.98]"
      >
        <LogoLockup className="h-7 w-[100px] text-white transition-opacity group-hover:opacity-95" />
      </Link>

      {/* Auth card container */}
      <main className="relative w-full max-w-[440px] rounded-3xl border border-border/80 bg-card p-6 sm:p-8 shadow-[0_20px_50px_-15px_rgba(0,0,0,0.08),0_1px_3px_rgba(0,0,0,0.04)] backdrop-blur-sm">
        <Outlet />
      </main>

      {/* Footer */}
      <p className="relative mt-6 text-center text-xs text-muted-foreground">
        University of Moratuwa · Enterprise Application Development
      </p>
    </div>
  )
}
