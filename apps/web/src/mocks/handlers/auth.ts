import { http, HttpResponse } from 'msw'
import type { AdminUser } from '@/api/types'
import { users, type DemoUser } from '../db'
import { base, body, caller, empty, guard, open, problem } from '../util'

const SESSION_KEY = 'sd-mock-session' // stands in for the refresh cookie in the offline demo only

const b64 = (o: object) =>
  btoa(JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_')
export function tokenFor(u: DemoUser) {
  const exp = Math.floor(Date.now() / 1000) + 900
  return `${b64({ alg: 'none' })}.${b64({ sub: u.id, email: u.email, roles: u.roles, merchantId: u.merchantId, exp })}.demo`
}
const setSession = (id: string | null) =>
  id ? sessionStorage.setItem(SESSION_KEY, id) : sessionStorage.removeItem(SESSION_KEY)
const signedIn = (u: DemoUser) => (
  setSession(u.id),
  HttpResponse.json({ accessToken: tokenFor(u), expiresIn: 900 })
)

export const publicUser = (u: DemoUser): AdminUser => ({
  id: u.id,
  email: u.email,
  fullName: u.fullName,
  phone: u.phone,
  roles: u.roles,
  status: u.status,
})
const GOOGLE_USER = () => users.find((u) => u.id === 'u-customer')!

export const authHandlers = [
  http.post(
    `${base}/api/v1/auth/login`,
    open(async ({ request }) => {
      const { email, password } = await body<{ email: string; password: string }>(request)
      const u = users.find((x) => x.email.toLowerCase() === String(email).trim().toLowerCase())
      if (!u || u.password !== password)
        return problem(401, 'INVALID_CREDENTIALS', 'Invalid credentials')
      if (u.status === 'SUSPENDED')
        return problem(403, 'ACCESS_DENIED', 'This account is suspended')
      return signedIn(u)
    }),
  ),
  http.post(
    `${base}/api/v1/auth/register`,
    open(async ({ request }) => {
      const b = await body<{ email: string; password: string; fullName: string; phone: string }>(
        request,
      )
      if (users.some((x) => x.email.toLowerCase() === b.email.toLowerCase()))
        return problem(409, 'CONFLICT', 'Conflict')
      users.push({
        id: `u-${crypto.randomUUID().slice(0, 8)}`,
        email: b.email,
        fullName: b.fullName,
        phone: b.phone,
        password: b.password,
        roles: ['CUSTOMER'],
        status: 'ACTIVE',
      })
      return empty(201)
    }),
  ),
  http.post(
    `${base}/api/v1/auth/refresh`,
    open(() => {
      const u = users.find((x) => x.id === sessionStorage.getItem(SESSION_KEY))
      return u && u.status === 'ACTIVE'
        ? signedIn(u)
        : problem(401, 'UNAUTHENTICATED', 'No session')
    }),
  ),
  http.post(
    `${base}/api/v1/auth/logout`,
    open(() => (setSession(null), empty())),
  ),
  // Google: skip the real redirect and bounce straight back to our own callback with the same state.
  http.get(
    `${base}/api/v1/auth/oauth2/google/authorize`,
    open(({ request }) => {
      const state = crypto.randomUUID()
      void request
      return HttpResponse.json({
        authorizationUrl: `${location.origin}/oauth/callback?code=demo-code&state=${state}`,
        state,
      })
    }),
  ),
  http.get(
    `${base}/api/v1/auth/oauth2/callback/google`,
    open(({ request }) => {
      const q = new URL(request.url).searchParams
      if (!q.get('code') || !q.get('codeVerifier'))
        return problem(400, 'VALIDATION_FAILED', 'Missing code or verifier')
      return signedIn(GOOGLE_USER())
    }),
  ),
  http.get(
    `${base}/api/v1/users/me`,
    guard(null, (_i, me) => {
      const u = users.find((x) => x.id === me.sub)
      return u ? HttpResponse.json(publicUser(u)) : problem(404, 'RESOURCE_NOT_FOUND', 'Not found')
    }),
  ),
  http.patch(
    `${base}/api/v1/users/me`,
    guard(null, async ({ request }, me) => {
      const u = users.find((x) => x.id === me.sub)
      if (!u) return problem(404, 'RESOURCE_NOT_FOUND', 'Not found')
      const b = await body<{ fullName: string; phone: string }>(request)
      u.fullName = b.fullName
      u.phone = b.phone
      return HttpResponse.json(publicUser(u))
    }),
  ),
]

export { caller }
