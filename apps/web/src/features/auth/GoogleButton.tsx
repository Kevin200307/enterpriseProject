import { useState } from 'react'
import { toast } from 'sonner'
import { describeError } from '@/api/errorMessages'
import { authApi } from '@/api/endpoints/auth'
import { challengeFor, createVerifier, oauthPending, oauthRedirectUri } from '@/auth/pkce'
import { Button } from '@/components/ui/button'

export function GoogleButton({ returnTo }: { returnTo?: string }) {
  const [busy, setBusy] = useState(false)

  async function start() {
    setBusy(true)
    try {
      const verifier = createVerifier()
      const { authorizationUrl, state } = await authApi.googleAuthorize(
        await challengeFor(verifier),
        oauthRedirectUri(),
      )
      oauthPending.save({ verifier, state, returnTo })
      window.location.assign(authorizationUrl)
    } catch (e) {
      toast.error(describeError(e).message)
      setBusy(false)
    }
  }

  return (
    <Button
      type="button"
      variant="outline"
      className="w-full"
      disabled={busy}
      onClick={() => void start()}
    >
      Continue with Google
    </Button>
  )
}
