import { Link, Outlet } from 'react-router-dom'
import { LogoLockup } from '@/components/brand/Logo'

/**
 * Full-screen auth layout: the courier on one side, the form on the other, both standing on one
 * uninterrupted surface — the artwork is a cut-out, so nothing sits behind it.
 */
export function AuthShell() {
  return (
    <div className="min-h-screen bg-surface lg:grid lg:grid-cols-[1fr_minmax(0,38rem)]">
      {/* Brand panel — wide screens only */}
      <aside className="relative hidden overflow-hidden p-10 lg:block">
        <Link to="/" aria-label="SwiftDrop home" className="relative flex w-fit">
          <LogoLockup className="h-11 w-[158px] text-sidebar" onLight />
        </Link>
        <img
          src="/courier.webp"
          alt="A SwiftDrop courier holding a parcel"
          className="pointer-events-none absolute inset-x-0 bottom-0 mx-auto h-[88vh] w-auto max-w-full object-contain object-bottom"
          width={805}
          height={1350}
        />
      </aside>

      {/* Form column */}
      {/* The extra right padding pulls the card off the screen edge, towards the middle. */}
      <div className="flex min-h-screen flex-col items-center justify-center px-4 py-8 sm:py-12 lg:pr-16">
        {/* Narrow screens get the logo and the courier above the card */}
        <div className="mb-4 flex w-full max-w-[440px] shrink-0 flex-col items-center lg:hidden">
          <Link to="/" aria-label="SwiftDrop home" className="flex">
            <LogoLockup className="h-11 w-[158px] text-sidebar" onLight />
          </Link>
          <img
            src="/courier.webp"
            alt=""
            aria-hidden
            className="pointer-events-none mt-1 h-48 w-auto object-contain sm:h-56"
          />
        </div>

        {/* No card — the form sits straight on the page surface. */}
        <main className="w-full max-w-[400px]">
          <Outlet />
        </main>

        <p className="mt-6 text-center text-xs text-muted-foreground">
          University of Moratuwa · Enterprise Application Development
        </p>
      </div>
    </div>
  )
}
