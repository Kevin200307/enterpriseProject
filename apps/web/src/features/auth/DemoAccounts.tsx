import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import {
  User,
  Store,
  Truck,
  LayoutGrid,
  ShieldCheck,
  Sparkles,
  Loader2,
} from 'lucide-react'
import { authApi } from '@/api/endpoints/auth'
import { describeError } from '@/api/errorMessages'
import { useAuth } from '@/auth/authContext'
import { decodeClaims } from '@/auth/jwt'
import { roleLanding, safeReturnTo } from '@/auth/landing'
import { LoadingScreen } from '@/components/common/LoadingScreen'
import { DEMO_ACCOUNTS, DEMO_PASSWORD } from '@/mocks/demoAccounts'
import { waitForLoginMinimum } from './loginLoading'

const ROLE_ICONS: Record<string, React.ElementType> = {
  Customer: User,
  Merchant: Store,
  Courier: Truck,
  Dispatcher: LayoutGrid,
  Admin: ShieldCheck,
}

/**
 * One-click sign-in for the offline demo (VITE_USE_MOCKS=true only; lazy-loaded so it never ships otherwise).
 * It signs in through the normal login endpoint, so the whole auth flow is exercised.
 */
export default function DemoAccounts({ returnTo }: { returnTo?: string }) {
  const { signInWithToken } = useAuth()
  const navigate = useNavigate()
  const [busy, setBusy] = useState<string>()

  async function enter(email: string) {
    const startedAt = Date.now()
    setBusy(email)
    try {
      const { accessToken } = await authApi.login(email, DEMO_PASSWORD)
      await waitForLoginMinimum(startedAt)
      signInWithToken(accessToken)
      navigate(safeReturnTo(returnTo, roleLanding(decodeClaims(accessToken)?.roles ?? [])), {
        replace: true,
      })
    } catch (e) {
      await waitForLoginMinimum(startedAt)
      toast.error(describeError(e).message)
      setBusy(undefined)
    }
  }

  if (busy) return <LoadingScreen />

  return (
    <section
      aria-label="Demo accounts"
      className="flex flex-col gap-3 rounded-2xl border border-border/80 bg-secondary/30 p-3.5 text-foreground transition-all"
    >
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          <Sparkles className="h-3.5 w-3.5 text-primary" />
          <h2 className="text-xs font-semibold tracking-wide text-foreground">Demo login</h2>
        </div>
        <span className="rounded-full bg-accent/25 px-2 py-0.5 text-[10px] font-semibold text-foreground">
          1-Click Demo
        </span>
      </div>

      <p className="text-[11px] text-muted-foreground">
        Preloaded test accounts. Click any role to sign in instantly.
      </p>

      <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        {DEMO_ACCOUNTS.map((a, idx) => {
          const Icon = ROLE_ICONS[a.label] ?? User
          const isFullWidth = idx === DEMO_ACCOUNTS.length - 1 && DEMO_ACCOUNTS.length % 2 !== 0
          return (
            <li key={a.email} className={isFullWidth ? 'col-span-2 sm:col-span-1' : ''}>
              <button
                type="button"
                disabled={!!busy}
                onClick={() => void enter(a.email)}
                className="group flex w-full items-center gap-2 rounded-xl border border-border/80 bg-card p-2 text-left transition-all hover:border-primary/50 hover:bg-card/90 hover:shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-60 active:scale-[0.98]"
              >
                <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg bg-secondary text-foreground group-hover:bg-primary group-hover:text-primary-foreground transition-colors">
                  {busy === a.email ? (
                    <Loader2 className="h-3.5 w-3.5 animate-spin" />
                  ) : (
                    <Icon className="h-3.5 w-3.5" />
                  )}
                </div>
                <div className="min-w-0 flex-1">
                  <div className="truncate text-xs font-semibold text-foreground">
                    {busy === a.email ? 'Entering…' : a.label}
                  </div>
                  <div className="truncate text-[10px] text-muted-foreground">{a.hint}</div>
                </div>
              </button>
            </li>
          )
        })}
      </ul>

      <div className="flex items-center justify-between border-t border-border/50 pt-2 text-[10px] text-muted-foreground">
        <span>Password for manual entry:</span>
        <code className="rounded bg-background/80 px-1.5 py-0.5 font-mono font-semibold text-foreground border border-border/60">
          {DEMO_PASSWORD}
        </code>
      </div>
    </section>
  )
}
