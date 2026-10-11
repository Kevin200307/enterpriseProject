import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { authApi } from '@/api/endpoints/auth'
import { describeError } from '@/api/errorMessages'
import { useAuth } from '@/auth/authContext'
import { decodeClaims } from '@/auth/jwt'
import { roleLanding, safeReturnTo } from '@/auth/landing'
import { DEMO_ACCOUNTS, DEMO_PASSWORD } from '@/mocks/demoAccounts'

/**
 * One-click sign-in for the offline demo (VITE_USE_MOCKS=true only; lazy-loaded so it never ships otherwise).
 * It signs in through the normal login endpoint, so the whole auth flow is exercised.
 */
export default function DemoAccounts({ returnTo }: { returnTo?: string }) {
  const { signInWithToken } = useAuth()
  const navigate = useNavigate()
  const [busy, setBusy] = useState<string>()

  async function enter(email: string) {
    setBusy(email)
    try {
      const { accessToken } = await authApi.login(email, DEMO_PASSWORD)
      signInWithToken(accessToken)
      navigate(safeReturnTo(returnTo, roleLanding(decodeClaims(accessToken)?.roles ?? [])), {
        replace: true,
      })
    } catch (e) {
      toast.error(describeError(e).message)
      setBusy(undefined)
    }
  }

  return (
    <section
      aria-label="Demo accounts"
      className="flex flex-col gap-3 rounded-lg border border-primary/30 bg-accent p-4"
    >
      <div>
        <h2 className="text-sm font-semibold text-accent-foreground">Demo login</h2>
        <p className="text-xs text-accent-foreground/80">
          Offline demo data. Click a role to go straight in.
        </p>
      </div>
      <ul className="grid gap-2 sm:grid-cols-2">
        {DEMO_ACCOUNTS.map((a) => (
          <li key={a.email}>
            <button
              type="button"
              disabled={!!busy}
              onClick={() => void enter(a.email)}
              className="flex w-full flex-col rounded-md border bg-background px-3 py-2 text-left text-sm hover:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-60"
            >
              <span className="font-semibold">{busy === a.email ? 'Signing in…' : a.label}</span>
              <span className="text-xs text-muted-foreground">{a.hint}</span>
            </button>
          </li>
        ))}
      </ul>
      <p className="text-xs text-accent-foreground/80">
        Or type any of these emails with password{' '}
        <code className="font-mono font-semibold">{DEMO_PASSWORD}</code>.
      </p>
    </section>
  )
}
