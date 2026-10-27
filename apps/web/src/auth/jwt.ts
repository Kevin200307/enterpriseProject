import { ALL_ROLES, type Claims, type Role } from './types'

function b64urlDecode(s: string) {
  const padded = s
    .replace(/-/g, '+')
    .replace(/_/g, '/')
    .padEnd(Math.ceil(s.length / 4) * 4, '=')
  return decodeURIComponent(
    atob(padded)
      .split('')
      .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
      .join(''),
  )
}

/** Decodes without verifying the signature — never use the result for authorization decisions. */
export function decodeClaims(token: string | null): Claims | null {
  if (!token) return null
  try {
    const payload = JSON.parse(b64urlDecode(token.split('.')[1] ?? '')) as Record<string, unknown>
    const roles = (Array.isArray(payload.roles) ? payload.roles : []).filter((r): r is Role =>
      ALL_ROLES.includes(r as Role),
    )
    if (typeof payload.sub !== 'string' || typeof payload.exp !== 'number') return null
    return {
      sub: payload.sub,
      email: typeof payload.email === 'string' ? payload.email : '',
      roles,
      merchantId: typeof payload.merchantId === 'string' ? payload.merchantId : undefined,
      exp: payload.exp,
    }
  } catch {
    return null
  }
}
