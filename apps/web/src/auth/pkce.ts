const b64url = (bytes: Uint8Array) =>
  btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')

const random = (n: number) => crypto.getRandomValues(new Uint8Array(n))

export const createVerifier = () => b64url(random(32))

export async function challengeFor(verifier: string) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))
  return b64url(new Uint8Array(digest))
}

const KEY = 'sd-oauth'

interface Pending {
  verifier: string
  state: string
  returnTo?: string
}

/** Kept in sessionStorage only for the redirect round-trip, and removed as soon as it is read. */
export const oauthPending = {
  save(p: Pending) {
    sessionStorage.setItem(KEY, JSON.stringify(p))
  },
  take(): Pending | null {
    try {
      const raw = sessionStorage.getItem(KEY)
      sessionStorage.removeItem(KEY)
      return raw ? (JSON.parse(raw) as Pending) : null
    } catch {
      return null
    }
  },
}

/** The SPA route Google redirects back to; must match the URI registered in the Google console. */
export const oauthRedirectUri = () => `${window.location.origin}/oauth/callback`
