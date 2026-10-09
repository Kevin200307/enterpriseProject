import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { authApi } from '@/api/endpoints/auth'
import { describeError } from '@/api/errorMessages'
import { useAuth } from '@/auth/authContext'
import { roleLanding, safeReturnTo } from '@/auth/landing'
import { decodeClaims } from '@/auth/jwt'
import { oauthPending, oauthRedirectUri } from '@/auth/pkce'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

export function OAuthCallbackPage() {
  const [params] = useSearchParams()
  const { signInWithToken } = useAuth()
  const navigate = useNavigate()
  const [error, setError] = useState<string>()
  const started = useRef(false) // StrictMode runs effects twice; the pending state can only be taken once

  useEffect(() => {
    if (started.current) return
    started.current = true
    const pending = oauthPending.take()
    const code = params.get('code')
    const state = params.get('state')

    if (params.get('error')) return setError('Google sign-in was cancelled or denied.')
    if (!pending || !code || !state)
      return setError('This sign-in link is invalid or has already been used.')
    if (state !== pending.state)
      return setError('Security check failed (state mismatch). Please start sign-in again.')

    authApi
      .googleCallback(code, state, pending.verifier, oauthRedirectUri())
      .then(({ accessToken }) => {
        signInWithToken(accessToken)
        navigate(
          safeReturnTo(pending.returnTo, roleLanding(decodeClaims(accessToken)?.roles ?? [])),
          { replace: true },
        )
      })
      .catch((e) => setError(describeError(e).message))
  }, [params, signInWithToken, navigate])

  return (
    <div className="mx-auto max-w-sm">
      <Card>
        <CardHeader>
          <CardTitle>{error ? 'Sign-in failed' : 'Signing you in…'}</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-col gap-4">
          {error ? (
            <>
              <p role="alert" className="text-sm text-destructive">
                {error}
              </p>
              <Button asChild>
                <Link to="/login">Back to sign in</Link>
              </Button>
            </>
          ) : (
            <p className="text-sm text-muted-foreground">Completing Google sign-in.</p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
