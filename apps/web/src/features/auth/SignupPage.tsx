import { zodResolver } from '@hookform/resolvers/zod'
import { Check, Circle } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { isApiError } from '@/api/ApiError'
import { authApi } from '@/api/endpoints/auth'
import { describeError } from '@/api/errorMessages'
import { applyViolations } from '@/api/formErrors'
import { useAuth } from '@/auth/authContext'
import { roleLanding } from '@/auth/landing'
import { decodeClaims } from '@/auth/jwt'
import { FormField } from '@/components/common/FormField'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { GoogleButton } from './GoogleButton'
import { passwordRules, signupSchema, type SignupInput } from './schemas'

export function SignupPage() {
  const { signInWithToken } = useAuth()
  const navigate = useNavigate()
  const [formError, setFormError] = useState<string>()
  const [duplicate, setDuplicate] = useState(false)
  const {
    register,
    handleSubmit,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<SignupInput>({ resolver: zodResolver(signupSchema), defaultValues: { phone: '+94' } })
  const password = watch('password') ?? ''

  async function onSubmit(values: SignupInput) {
    setFormError(undefined)
    setDuplicate(false)
    try {
      await authApi.register(values)
      const { accessToken } = await authApi.login(values.email, values.password)
      signInWithToken(accessToken)
      navigate(roleLanding(decodeClaims(accessToken)?.roles ?? []), { replace: true })
    } catch (e) {
      if (isApiError(e) && e.errorCode === 'CONFLICT') return setDuplicate(true)
      const extra = applyViolations(e, setError, ['fullName', 'email', 'phone', 'password'])
      setFormError([describeError(e).message, ...extra].join(' '))
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <h1 className="text-2xl font-bold tracking-tight">Create your account</h1>
      <div className="flex flex-col gap-4">
        <form
          onSubmit={(e) => void handleSubmit(onSubmit)(e)}
          noValidate
          className="flex flex-col gap-4"
        >
          <FormField label="Full name" error={errors.fullName?.message}>
            {(p) => <Input autoComplete="name" {...p} {...register('fullName')} />}
          </FormField>
          <FormField label="Email" error={errors.email?.message}>
            {(p) => <Input type="email" autoComplete="email" {...p} {...register('email')} />}
          </FormField>
          <FormField
            label="Phone"
            error={errors.phone?.message}
            hint="Sri Lankan mobile, e.g. +94771234567"
          >
            {(p) => <Input type="tel" autoComplete="tel" {...p} {...register('phone')} />}
          </FormField>
          <FormField label="Password" error={errors.password?.message}>
            {(p) => (
              <Input type="password" autoComplete="new-password" {...p} {...register('password')} />
            )}
          </FormField>
          <ul aria-label="Password requirements" className="-mt-2 flex flex-col gap-1 text-sm">
            {passwordRules.map((r) => {
              const ok = r.test(password)
              return (
                <li key={r.id} className={ok ? 'text-green-700' : 'text-muted-foreground'}>
                  {ok ? (
                    <Check className="mr-1.5 inline h-3.5 w-3.5" aria-hidden />
                  ) : (
                    <Circle className="mr-1.5 inline h-3.5 w-3.5" aria-hidden />
                  )}
                  {r.label}
                  <span className="sr-only">{ok ? ' (met)' : ' (not met)'}</span>
                </li>
              )
            })}
          </ul>
          {duplicate && (
            <p role="alert" className="text-sm text-destructive">
              An account with this email already exists.{' '}
              <Link className="underline" to="/login">
                Sign in instead
              </Link>
            </p>
          )}
          {formError && (
            <p role="alert" className="text-sm text-destructive">
              {formError}
            </p>
          )}
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? 'Creating account…' : 'Create account'}
          </Button>
        </form>
        <div className="text-center text-xs text-muted-foreground">or</div>
        <GoogleButton />
        <p className="text-center text-sm text-muted-foreground">
          Already have an account?{' '}
          <Link className="text-primary underline-offset-4 hover:underline" to="/login">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
