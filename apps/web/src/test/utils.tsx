import { render } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { Providers } from '@/app/providers'
import { routes } from '@/app/router'
import { tokenStore } from '@/auth/tokenStore'
import type { Role } from '@/auth/types'
import { server } from './server'

export const API = 'http://localhost:8081'

const b64 = (o: object) =>
  btoa(JSON.stringify(o)).replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_')

export function makeJwt(roles: Role[], extra: Record<string, unknown> = {}) {
  const exp = Math.floor(Date.now() / 1000) + 900
  return `${b64({ alg: 'RS256' })}.${b64({ sub: 'u-1', email: 'a@b.lk', roles, exp, ...extra })}.sig`
}

export const problem = (status: number, errorCode: string, extra: object = {}) =>
  HttpResponse.json(
    { title: errorCode, status, detail: `${errorCode} detail`, errorCode, ...extra },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )

/** Boot-time refresh answers with a session for `roles`, or 401 (anonymous) when omitted. */
export function mockSession(roles?: Role[]) {
  server.use(
    http.post(`${API}/api/v1/auth/refresh`, () =>
      roles ? HttpResponse.json({ accessToken: makeJwt(roles) }) : problem(401, 'UNAUTHENTICATED'),
    ),
  )
}

export function renderApp(path: string) {
  tokenStore.clear()
  const router = createMemoryRouter(routes, { initialEntries: [path] })
  render(
    <Providers>
      <RouterProvider router={router} />
    </Providers>,
  )
  return router
}
