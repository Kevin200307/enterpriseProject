import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { describe, expect, it, vi } from 'vitest'
import { oauthPending } from '@/auth/pkce'
import { server } from '@/test/server'
import { API, makeJwt, mockSession, problem, renderApp } from '@/test/utils'

describe('route guards', () => {
  it('sends an anonymous visitor to login with returnTo', async () => {
    mockSession()
    const router = renderApp('/deliveries/new')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.search).toContain('returnTo=%2Fdeliveries%2Fnew')
  })

  it('shows the 403 page when a CUSTOMER opens /admin', async () => {
    mockSession(['CUSTOMER'])
    renderApp('/admin')
    expect(await screen.findByRole('heading', { name: /403/ })).toBeInTheDocument()
  })

  it('lets an ADMIN into /admin', async () => {
    mockSession(['ADMIN'])
    renderApp('/admin')
    expect(await screen.findByRole('heading', { name: 'Dashboard', level: 1 })).toBeInTheDocument()
  })

  it('restores a session from the refresh cookie on load', async () => {
    mockSession(['COURIER'])
    renderApp('/')
    expect(await screen.findByRole('heading', { name: 'Today', level: 1 })).toBeInTheDocument()
  })
})

describe('full-screen auth layout', () => {
  it.each(['/login', '/signup', '/oauth/callback?code=x&state=y'])(
    '%s has no sidebar, header or app chrome',
    async (path) => {
      mockSession()
      renderApp(path)
      await screen.findByRole('heading', { level: 1 })
      expect(screen.queryByRole('navigation', { name: 'Main' })).not.toBeInTheDocument()
      expect(screen.queryByRole('banner')).not.toBeInTheDocument()
      expect(screen.queryByRole('button', { name: 'Open assistant' })).not.toBeInTheDocument()
      expect(screen.getByRole('main')).toBeInTheDocument()
    },
  )
})

describe('login', () => {
  it('validates before sending a request', async () => {
    mockSession()
    renderApp('/login')
    await userEvent.click(await screen.findByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Email is required')).toBeInTheDocument()
    expect(screen.getByText('Password is required')).toBeInTheDocument()
  })

  it('shows one generic message for wrong credentials', async () => {
    mockSession()
    server.use(http.post(`${API}/api/v1/auth/login`, () => problem(401, 'INVALID_CREDENTIALS')))
    renderApp('/login')
    await userEvent.type(await screen.findByLabelText('Email'), 'a@b.lk')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByText('Email or password is incorrect.')).toBeInTheDocument()
  })

  it('signs in and lands on returnTo', async () => {
    mockSession()
    server.use(
      http.post(`${API}/api/v1/auth/login`, () =>
        HttpResponse.json({ accessToken: makeJwt(['CUSTOMER']) }),
      ),
    )
    const router = renderApp('/login?returnTo=%2Fdeliveries%2Fnew')
    await userEvent.type(await screen.findByLabelText('Email'), 'a@b.lk')
    await userEvent.type(screen.getByLabelText('Password'), 'secret123')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/deliveries/new'))
  })

  it('ignores an external returnTo', async () => {
    mockSession()
    server.use(
      http.post(`${API}/api/v1/auth/login`, () =>
        HttpResponse.json({ accessToken: makeJwt(['CUSTOMER']) }),
      ),
    )
    const router = renderApp('/login?returnTo=https%3A%2F%2Fevil.example')
    await userEvent.type(await screen.findByLabelText('Email'), 'a@b.lk')
    await userEvent.type(screen.getByLabelText('Password'), 'secret123')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/deliveries'))
  })

  it('disables submit with a countdown when rate limited', async () => {
    mockSession()
    server.use(
      http.post(`${API}/api/v1/auth/login`, () =>
        HttpResponse.json(
          { title: 'x', status: 429, errorCode: 'RATE_LIMITED' },
          {
            status: 429,
            headers: { 'Retry-After': '30', 'Content-Type': 'application/problem+json' },
          },
        ),
      ),
    )
    renderApp('/login')
    await userEvent.type(await screen.findByLabelText('Email'), 'a@b.lk')
    await userEvent.type(screen.getByLabelText('Password'), 'secret123')
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }))
    expect(await screen.findByRole('button', { name: 'Try again in 30s' })).toBeDisabled()
  })
})

describe('signup', () => {
  it('enforces the password and phone rules client-side', async () => {
    mockSession()
    renderApp('/signup')
    await userEvent.type(await screen.findByLabelText('Full name'), 'Nimal')
    await userEvent.type(screen.getByLabelText('Email'), 'n@x.lk')
    await userEvent.type(screen.getByLabelText('Password'), 'short')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByText('Use the format +94XXXXXXXXX')).toBeInTheDocument()
    expect(screen.getByText('Password does not meet the requirements')).toBeInTheDocument()
  })

  it('reports a duplicate email without revealing more', async () => {
    mockSession()
    server.use(http.post(`${API}/api/v1/auth/register`, () => problem(409, 'CONFLICT')))
    renderApp('/signup')
    await userEvent.type(await screen.findByLabelText('Full name'), 'Nimal')
    await userEvent.type(screen.getByLabelText('Email'), 'n@x.lk')
    await userEvent.type(screen.getByLabelText('Phone'), '771234567')
    await userEvent.type(screen.getByLabelText('Password'), 'abcdef12')
    await userEvent.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByText(/already exists/)).toBeInTheDocument()
  })
})

describe('google callback', () => {
  it('rejects a state mismatch without calling the API', async () => {
    mockSession()
    const called = vi.fn()
    server.use(
      http.get(
        `${API}/api/v1/auth/oauth2/callback/google`,
        () => (called(), HttpResponse.json({})),
      ),
    )
    oauthPending.save({ verifier: 'v', state: 'expected' })
    renderApp('/oauth/callback?code=abc&state=forged')
    expect(await screen.findByText(/state mismatch/)).toBeInTheDocument()
    expect(called).not.toHaveBeenCalled()
  })

  it('exchanges the code and signs in', async () => {
    mockSession()
    server.use(
      http.get(`${API}/api/v1/auth/oauth2/callback/google`, ({ request }) => {
        const q = new URL(request.url).searchParams
        return q.get('codeVerifier') === 'v'
          ? HttpResponse.json({ accessToken: makeJwt(['CUSTOMER']) })
          : problem(400, 'VALIDATION_FAILED')
      }),
    )
    oauthPending.save({ verifier: 'v', state: 's' })
    const router = renderApp('/oauth/callback?code=abc&state=s')
    await waitFor(() => expect(router.state.location.pathname).toBe('/deliveries'))
    expect(sessionStorage.getItem('sd-oauth')).toBeNull()
  })
})

describe('session expiry', () => {
  it('redirects to login with a notice when refresh fails mid-session', async () => {
    let refreshes = 0
    server.use(
      http.post(`${API}/api/v1/auth/refresh`, () =>
        ++refreshes === 1
          ? HttpResponse.json({ accessToken: makeJwt(['CUSTOMER']) })
          : problem(401, 'UNAUTHENTICATED'),
      ),
      http.get(`${API}/api/v1/users/me`, () => problem(401, 'UNAUTHENTICATED')),
    )
    const router = renderApp('/account')
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
    expect(router.state.location.search).toContain('reason=expired')
    expect(await screen.findByText(/session expired/i)).toBeInTheDocument()
  })
})

describe('logout', () => {
  it('clears the session and shows the signed-out shell', async () => {
    mockSession(['CUSTOMER'])
    server.use(
      http.post(`${API}/api/v1/auth/logout`, () => new HttpResponse(null, { status: 204 })),
    )
    renderApp('/deliveries')
    await userEvent.click(await screen.findByRole('button', { name: 'Sign out' }))
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })
})
