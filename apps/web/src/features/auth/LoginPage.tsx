import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState } from 'react'
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
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { GoogleButton } from './GoogleButton'
import { loginSchema, type LoginInput } from './schemas'

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
    <div className="mx-auto max-w-sm">
      <Card>
        <CardHeader>
          <CardTitle>Sign in</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {expired && (
            <p
              role="status"
              className="rounded-md bg-amber-100 p-3 text-sm text-amber-900 dark:bg-amber-950 dark:text-amber-100"
            >
              Your session expired. Please sign in again.
            </p>
          )}
          <form
            onSubmit={(e) => void handleSubmit(onSubmit)(e)}
            noValidate
            className="flex flex-col gap-4"
          >
            <FormField label="Email" error={errors.email?.message}>
              {(p) => <Input type="email" autoComplete="email" {...p} {...register('email')} />}
            </FormField>
            <FormField label="Password" error={errors.password?.message}>
              {(p) => (
                <Input
                  type="password"
                  autoComplete="current-password"
                  {...p}
                  {...register('password')}
                />
              )}
            </FormField>
            {formError && (
              <p role="alert" className="text-sm text-destructive">
                {formError}
              </p>
            )}
            <Button type="submit" disabled={isSubmitting || wait > 0}>
              {wait > 0 ? `Try again in ${wait}s` : isSubmitting ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>
          <div className="text-center text-xs text-muted-foreground">or</div>
          <GoogleButton returnTo={returnTo ?? undefined} />
          <p className="text-center text-sm text-muted-foreground">
            New to SwiftDrop?{' '}
            <Link className="text-primary underline-offset-4 hover:underline" to="/signup">
              Create an account
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
