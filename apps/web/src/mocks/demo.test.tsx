import { screen, waitFor } from '@testing-library/react'
import userEvent from '@/test/userEvent'
import { beforeEach, describe, expect, it } from 'vitest'
import { authApi } from '@/api/endpoints/auth'
import { api, request } from '@/api/client'
import type { Page, Parcel, PublicTracking } from '@/api/types'
import { ApiError } from '@/api/ApiError'
import { decodeClaims } from '@/auth/jwt'
import { tokenStore } from '@/auth/tokenStore'
import { env } from '@/config/env'
import { server } from '@/test/server'
import { renderApp } from '@/test/utils'
import { DEMO_ACCOUNTS, DEMO_PASSWORD } from './demoAccounts'
import { handlers } from './handlers'

beforeEach(() => {
  sessionStorage.clear()
  tokenStore.clear()
  server.use(...handlers)
})

const signIn = async (email: string) => {
  const { accessToken } = await authApi.login(email, DEMO_PASSWORD)
  tokenStore.set(accessToken)
  return decodeClaims(accessToken)!
}
const fails = (p: Promise<unknown>) =>
  p.then(
    () => null,
    (e: unknown) => e as ApiError,
  )

describe('demo accounts', () => {
  it.each(DEMO_ACCOUNTS.map((a) => [a.label, a.email] as const))(
    '%s can sign in with the shared demo password',
    async (_label, email) => {
      const claims = await signIn(email)
      expect(claims.email).toBe(email)
      expect(claims.roles.length).toBeGreaterThan(0)
    },
  )

  it('grants each account exactly the roles its label promises', async () => {
    const roles = async (email: string) => (await signIn(email)).roles
    expect(await roles('customer@swiftdrop.demo')).toEqual(['CUSTOMER'])
    expect(await roles('courier@swiftdrop.demo')).toEqual(['COURIER'])
    expect(await roles('dispatcher@swiftdrop.demo')).toEqual(['DISPATCHER'])
    expect(await roles('admin@swiftdrop.demo')).toEqual(['ADMIN'])
    const m = await signIn('merchant@swiftdrop.demo')
    expect(m.roles).toEqual(['CUSTOMER', 'MERCHANT'])
    expect(m.merchantId).toBe('m-1')
  })

  it('rejects a wrong password and an unknown email with the same error', async () => {
    const bad = await fails(authApi.login('admin@swiftdrop.demo', 'nope'))
    const unknown = await fails(authApi.login('nobody@swiftdrop.demo', DEMO_PASSWORD))
    expect(bad?.errorCode).toBe('INVALID_CREDENTIALS')
    expect(unknown?.errorCode).toBe('INVALID_CREDENTIALS')
  })

  it('restores the session on refresh until logout', async () => {
    await signIn('customer@swiftdrop.demo')
    expect((await authApi.refresh()).accessToken).toBeTruthy()
    await authApi.logout()
    expect((await fails(authApi.refresh()))?.status).toBe(401)
  })
})

describe('demo backend rules', () => {
  it('answers unauthenticated calls with 401', async () => {
    expect((await fails(api.get('/api/v1/parcels')))?.status).toBe(401)
  })

  it('shows a customer only their own parcels and hides others behind a 404', async () => {
    await signIn('customer@swiftdrop.demo')
    const mine = await api.get<Page<Parcel>>('/api/v1/parcels')
    expect(mine.content.length).toBeGreaterThan(3)
    const foreign = await signInAndFind('SD-Z7G2PA9S') // belongs to another customer
    expect(foreign).toBe(404)
  })

  it('lets dispatchers see everything but not list users (admin only)', async () => {
    await signIn('dispatcher@swiftdrop.demo')
    const all = await api.get<Page<Parcel>>('/api/v1/parcels', { size: 100 })
    expect(all.totalElements).toBeGreaterThan(10)
    expect((await fails(api.get('/api/v1/users')))?.status).toBe(403)
  })

  it('keeps customers out of admin and dispatch endpoints', async () => {
    await signIn('customer@swiftdrop.demo')
    expect((await fails(api.get('/api/v1/dispatch/queue')))?.status).toBe(403)
    expect((await fails(api.get('/api/v1/merchants')))?.status).toBe(403)
  })

  it('walks a new parcel through booking, cancel and the state machine', async () => {
    await signIn('customer@swiftdrop.demo')
    const addr = (city: string) => ({
      contactName: 'A',
      phone: '+94771234567',
      line1: '1 St',
      city,
      postalCode: '1',
    })
    const created = await api.post<Parcel>('/api/v1/parcels', {
      pickup: addr('Colombo'),
      dropoff: addr('Kandy'),
      size: 'SMALL',
      weightKg: 1,
      express: false,
    })
    expect(created.status).toBe('CREATED')
    expect(created.trackingNumber).toMatch(/^SD-[A-HJ-NP-Z2-9]{8}$/)
    expect(created).not.toHaveProperty('customerId') // internal fields never leak

    const cancelled = await api.post<Parcel>(`/api/v1/parcels/${created.id}/cancel`)
    expect(cancelled.status).toBe('CANCELLED')
    expect((await fails(api.post(`/api/v1/parcels/${created.id}/cancel`)))?.errorCode).toBe(
      'INVALID_STATUS_TRANSITION',
    )

    const inTransit = (await api.get<Page<Parcel>>('/api/v1/parcels', { status: 'IN_TRANSIT' }))
      .content[0]!
    expect((await fails(api.post(`/api/v1/parcels/${inTransit.id}/cancel`)))?.status).toBe(409)
  })

  it('prices a delivery and says when a city is not served', async () => {
    await signIn('customer@swiftdrop.demo')
    const est = await api.post<{ total: number; zoneFrom: string; zoneTo: string }>(
      '/api/v1/pricing/estimate',
      { fromCity: 'Colombo', toCity: 'Kandy', size: 'SMALL', weightKg: 2, express: false },
    )
    expect(est.zoneFrom).toBe('Colombo')
    expect(est.total).toBeGreaterThan(0)
    expect(
      (
        await fails(
          api.post('/api/v1/pricing/estimate', {
            fromCity: 'Colombo',
            toCity: 'Atlantis',
            size: 'SMALL',
            weightKg: 1,
            express: false,
          }),
        )
      )?.status,
    ).toBe(404)
    expect(
      (
        await fails(
          api.post('/api/v1/pricing/estimate', {
            fromCity: 'Colombo',
            toCity: 'Kandy',
            size: 'SMALL',
            weightKg: 99,
            express: false,
          }),
        )
      )?.errorCode,
    ).toBe('VALIDATION_FAILED')
  })

  it('serves public tracking without any phone, street or id', async () => {
    const t = await request<PublicTracking>('/api/v1/tracking/SD-P5V7JG3L')
    expect(t.status).toBe('OUT_FOR_DELIVERY')
    expect(t.courierFirstName).toBe('Kasun')
    expect(JSON.stringify(t)).not.toMatch(/\+94\d{9}|Galle Road|Temple Street|"id"|customerId/)
    expect((await fails(request('/api/v1/tracking/SD-ZZZZ9999')))?.status).toBe(404)
  })

  it('shows a courier only their own assignments', async () => {
    await signIn('courier@swiftdrop.demo')
    const mine = await api.get<{ trackingNumber: string }[]>('/api/v1/couriers/me/assignments')
    expect(mine.map((a) => a.trackingNumber)).toContain('SD-P5V7JG3L')
    expect(mine.map((a) => a.trackingNumber)).not.toContain('SD-T9J3BW7E') // belongs to courier c-2
    expect((await fails(api.get('/api/v1/dispatch/queue')))?.status).toBe(403)
  })
})

const demoButton = (panel: HTMLElement, label: string) =>
  Array.from(panel.querySelectorAll('button')).find((b) => b.textContent?.includes(label))!

async function signInAndFind(tn: string) {
  const e = await fails(api.get(`/api/v1/parcels/by-tracking/${tn}`))
  return e?.status
}

describe('one-click demo login', () => {
  beforeEach(() => {
    env.useMocks = true
    return () => {
      env.useMocks = false
    }
  })

  it.each([
    ['Customer', '/deliveries'],
    ['Merchant', '/merchant/orders'],
    ['Courier', '/courier/today'],
    ['Dispatcher', '/admin/dispatch'],
    ['Admin', '/admin'],
  ])('clicking %s signs in and lands on %s', async (label, path) => {
    const router = renderApp('/login')
    const panel = await screen.findByRole('region', { name: 'Demo accounts' })
    await userEvent.click(demoButton(panel, label))
    await waitFor(() => expect(router.state.location.pathname).toBe(path))
  })

  it('shows the demo panel only in mock mode', async () => {
    env.useMocks = false
    renderApp('/login')
    await screen.findByRole('heading', { name: 'Sign in' })
    expect(screen.queryByRole('region', { name: 'Demo accounts' })).not.toBeInTheDocument()
  })

  it('honours returnTo after a demo sign-in', async () => {
    const router = renderApp('/login?returnTo=%2Faccount')
    const panel = await screen.findByRole('region', { name: 'Demo accounts' })
    await userEvent.click(
      Array.from(panel.querySelectorAll('button')).find((b) =>
        b.textContent?.includes('Customer'),
      )!,
    )
    await waitFor(() => expect(router.state.location.pathname).toBe('/account'))
  })
})
