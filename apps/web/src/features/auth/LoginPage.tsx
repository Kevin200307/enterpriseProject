import { zodResolver } from '@hookform/resolvers/zod'
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
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { env } from '@/config/env'
import { GoogleButton } from './GoogleButton'
import { loginSchema, type LoginInput } from './schemas'

const DemoAccounts = import.meta.env.PROD ? null : lazy(() => import('./DemoAccounts'))

export function LoginPage() {
  const { status, claims, signInWithToken } = useAuth()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const returnTo = params.get('returnTo')
  const expired = params.get('reason') === 'expired'
  const [formError, setFormError] = useState<string>()
  const [wait, setWait] = useState(0)
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

  async function onSubmit(values: LoginInput) {
    setFormError(undefined)
    try {
      const { accessToken } = await authApi.login(values.email, values.password)
      signInWithToken(accessToken)
      navigate(safeReturnTo(returnTo, roleLanding(decodeClaims(accessToken)?.roles ?? [])), {
        replace: true,
      })
    } catch (e) {
      if (isApiError(e) && e.errorCode === 'RATE_LIMITED') setWait(e.retryAfter ?? 60)
      setFormError(describeError(e).message)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-foreground">Sign in</h1>
        <p className="mt-1 text-sm text-muted-foreground">Sign in to your SwiftDrop account</p>
      </div>

      <div className="flex flex-col gap-4">
        {expired && (
          <p role="status" className="rounded-2xl bg-amber-50 px-4 py-3 text-sm text-amber-800">
            Your session expired. Please sign in again.
          </p>
        )}

        <form
          onSubmit={(e) => void handleSubmit(onSubmit)(e)}
          noValidate
          className="flex flex-col gap-4"
        >
          <FormField label="Email" error={errors.email?.message}>
            {(p) => (
              <Input
                type="email"
                autoComplete="email"
                placeholder="you@example.com"
                className="h-11 rounded-xl bg-secondary focus-visible:ring-ring"
                {...p}
                {...register('email')}
              />
            )}
          </FormField>
          <FormField label="Password" error={errors.password?.message}>
            {(p) => (
              <Input
                type="password"
                autoComplete="current-password"
                placeholder="••••••••"
                className="h-11 rounded-xl bg-secondary focus-visible:ring-ring"
                {...p}
                {...register('password')}
              />
            )}
          </FormField>

          {formError && (
            <p role="alert" className="rounded-xl bg-destructive/10 px-4 py-2 text-sm text-destructive">
              {formError}
            </p>
          )}

          <Button
            type="submit"
            disabled={isSubmitting || wait > 0}
            className="mt-1 h-12 rounded-2xl text-sm font-semibold"
          >
            {wait > 0 ? `Try again in ${wait}s` : isSubmitting ? 'Signing in…' : 'Sign in'}
          </Button>
        </form>

        <div className="flex items-center gap-3">
          <div className="h-px flex-1 bg-border/60" />
          <span className="text-xs text-muted-foreground">or</span>
          <div className="h-px flex-1 bg-border/60" />
        </div>

        <GoogleButton returnTo={returnTo ?? undefined} />

        {DemoAccounts && env.useMocks && (
          <Suspense fallback={null}>
            <DemoAccounts returnTo={returnTo ?? undefined} />
          </Suspense>
        )}

        <p className="text-center text-sm text-muted-foreground">
          New to SwiftDrop?{' '}
          <Link className="font-semibold text-primary underline-offset-4 hover:underline" to="/signup">
            Create an account
          </Link>
        </p>
      </div>
    </div>
  )
}
