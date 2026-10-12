import { zodResolver } from '@hookform/resolvers/zod'
import {
  AlertCircle,
  ArrowRight,
  Clock,
  Eye,
  EyeOff,
  Loader2,
  Lock,
  Mail,
} from 'lucide-react'
import { Suspense, lazy, useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { isApiError } from '@/api/ApiError'
import { authApi } from '@/api/endpoints/auth'
import { describeError } from '@/api/errorMessages'
import { useAuth } from '@/auth/authContext'
import { roleLanding, safeReturnTo } from '@/auth/landing'
import { decodeClaims } from '@/auth/jwt'
import { FormField } from '@/components/common/FormField'
import { LoadingScreen } from '@/components/common/LoadingScreen'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { env } from '@/config/env'
import { GoogleButton } from './GoogleButton'
import { loginSchema, type LoginInput } from './schemas'

const DemoAccounts =
  import.meta.env.PROD && import.meta.env.VITE_DEMO_MODE !== 'true'
    ? null
    : lazy(() => import('./DemoAccounts'))

const LOGIN_LOADING_MIN_MS = 800

export function LoginPage() {
  const { status, claims, signInWithToken } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const returnTo = params.get('returnTo')
  const expired = params.get('reason') === 'expired'
  const [formError, setFormError] = useState<string>()
  const [wait, setWait] = useState(0)
  const [showPassword, setShowPassword] = useState(false)
  const [loginLoading, setLoginLoading] = useState(false)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginInput>({ resolver: zodResolver(loginSchema) })

  useEffect(() => {
    if (wait <= 0) return
    const id = setTimeout(() => setWait((w) => w - 1), 1000)
    return () => clearTimeout(id)
  }, [wait])

  if (status === 'authenticated' && claims)
    return <Navigate to={safeReturnTo(returnTo, roleLanding(claims.roles))} replace />

  if (isSubmitting || loginLoading) return <LoadingScreen />

  async function onSubmit(values: LoginInput) {
    setFormError(undefined)
    const startedAt = Date.now()
    setLoginLoading(true)

    const waitForMinimumLoading = async () => {
      const remaining = LOGIN_LOADING_MIN_MS - (Date.now() - startedAt)
      if (remaining > 0) await new Promise((resolve) => setTimeout(resolve, remaining))
    }

    try {
      const { accessToken } = await authApi.login(values.email, values.password)
      await waitForMinimumLoading()
      signInWithToken(accessToken)
      navigate(safeReturnTo(returnTo, roleLanding(decodeClaims(accessToken)?.roles ?? [])), {
        replace: true,
      })
    } catch (e) {
      await waitForMinimumLoading()
      setLoginLoading(false)
      if (isApiError(e) && e.errorCode === 'RATE_LIMITED') setWait(e.retryAfter ?? 60)
      setFormError(describeError(e).message)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="space-y-1.5 text-center">
        <h1 className="text-2xl font-bold tracking-tight text-foreground">Sign in</h1>
        <p className="text-xs text-muted-foreground sm:text-sm">
          Welcome back! Enter your credentials to access your account.
        </p>
      </div>

      <div className="flex flex-col gap-4">
        {expired && (
          <div
            role="status"
            className="flex items-start gap-2.5 rounded-xl border border-amber-200/80 bg-amber-50/90 p-3 text-xs text-amber-900"
          >
            <Clock className="mt-0.5 h-4 w-4 shrink-0 text-amber-700" />
            <span>Your session expired. Please sign in again.</span>
          </div>
        )}

        <form
          onSubmit={(e) => void handleSubmit(onSubmit)(e)}
          noValidate
          className="flex flex-col gap-3.5"
        >
          <FormField label="Email" error={errors.email?.message}>
            {(p) => (
              <div className="relative">
                <Mail className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  type="email"
                  autoComplete="email"
                  placeholder="you@example.com"
                  className="h-11 rounded-xl border-border/80 bg-secondary/30 pl-10 pr-3 transition-colors hover:border-border focus:bg-background focus-visible:ring-ring"
                  {...p}
                  {...register('email')}
                />
              </div>
            )}
          </FormField>

          <FormField label="Password" error={errors.password?.message}>
            {(p) => (
              <div className="relative">
                <Lock className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  placeholder="••••••••"
                  className="h-11 rounded-xl border-border/80 bg-secondary/30 pl-10 pr-10 transition-colors hover:border-border focus:bg-background focus-visible:ring-ring"
                  {...p}
                  {...register('password')}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((prev) => !prev)}
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 rounded-md p-1 text-muted-foreground/80 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  tabIndex={-1}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            )}
          </FormField>

          {formError && (
            <div
              role="alert"
              className="flex items-start gap-2.5 rounded-xl border border-destructive/20 bg-destructive/10 p-3 text-xs text-destructive"
            >
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-destructive" />
              <span>{formError}</span>
            </div>
          )}

          <Button
            type="submit"
            disabled={isSubmitting || wait > 0}
            className="group mt-1 h-11 rounded-xl text-sm font-semibold shadow-sm transition-all active:scale-[0.99]"
          >
            {isSubmitting && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
            {wait > 0 ? (
              `Try again in ${wait}s`
            ) : isSubmitting ? (
              'Signing in…'
            ) : (
              <span className="inline-flex items-center gap-1.5">
                Sign in
                <ArrowRight className="h-4 w-4 opacity-70 transition-transform group-hover:translate-x-0.5" />
              </span>
            )}
          </Button>
        </form>

        <div className="relative my-0.5 flex items-center justify-center">
          <div className="absolute inset-0 flex items-center">
            <div className="w-full border-t border-border/70" />
          </div>
          <span className="relative bg-surface px-3 text-[11px] uppercase tracking-wider text-muted-foreground">
            or
          </span>
        </div>

        <GoogleButton returnTo={returnTo ?? undefined} />

        {DemoAccounts && env.useMocks && (
          <Suspense fallback={null}>
            <DemoAccounts returnTo={returnTo ?? undefined} />
          </Suspense>
        )}

        <p className="text-center text-xs text-muted-foreground">
          New to SwiftDrop?{' '}
          <Link
            className="font-semibold text-primary underline-offset-4 hover:underline"
            to="/signup"
          >
            Create an account
          </Link>
        </p>
      </div>
    </div>
  )
}
