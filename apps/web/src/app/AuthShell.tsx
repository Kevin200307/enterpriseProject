import { Package } from 'lucide-react'
import { Outlet } from 'react-router-dom'

/** Full-screen layout for sign-in, sign-up and the Google callback: no sidebar, no header, nothing else. */
export function AuthShell() {
  return (
    <div className="grid min-h-screen bg-background lg:grid-cols-2">
      <aside className="hidden flex-col justify-between bg-primary p-12 text-primary-foreground lg:flex">
        <div className="flex items-center gap-2 text-xl font-bold">
          <Package className="h-7 w-7" aria-hidden /> SwiftDrop
        </div>
        <div className="flex flex-col gap-3">
          <p className="text-4xl font-bold leading-tight">Send it. Track it. Done.</p>
          <p className="max-w-sm text-lg text-primary-foreground/85">
            Book a delivery, follow it live, and get proof when it arrives.
          </p>
        </div>
        <p className="text-sm text-primary-foreground/70">
          University of Moratuwa · Enterprise Application Development
        </p>
      </aside>
      <main className="flex items-center justify-center p-6 sm:p-10">
        <div className="w-full max-w-sm">
          <div className="mb-8 flex items-center gap-2 text-xl font-bold text-primary lg:hidden">
            <Package className="h-6 w-6" aria-hidden /> SwiftDrop
          </div>
          <Outlet />
        </div>
      </main>
    </div>
  )
}
