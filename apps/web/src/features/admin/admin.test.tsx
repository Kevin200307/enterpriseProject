import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import type { AdminUser, Merchant } from '@/api/types'
import { server } from '@/test/server'
import { API, mockSession, renderApp } from '@/test/utils'

const page = <T,>(content: T[], totalPages = 1, number = 0) => ({
  content,
  totalElements: content.length,
  totalPages,
  number,
  size: 20,
})
const user = (over: Partial<AdminUser> = {}): AdminUser => ({
  id: 'u-2',
  email: 'nimal@x.lk',
  fullName: 'Nimal Perera',
  phone: '+94771234567',
  roles: ['CUSTOMER'],
  status: 'ACTIVE',
  ...over,
})
const merchant = (over: Partial<Merchant> = {}): Merchant => ({
  id: 'm-1',
  orgName: 'Lanka Crafts',
  businessRegNo: 'PV12345',
  contactEmail: 'shop@lanka.lk',
  contactPhone: '+94112345678',
  status: 'PENDING',
  ...over,
})

describe('dashboard', () => {
  beforeEach(() => mockSession(['ADMIN']))

  it('shows the figures', async () => {
    server.use(
      http.get(`${API}/api/v1/dashboard/summary`, () =>
        HttpResponse.json({
          parcels: { today: 42, outForDelivery: 7, deliveredToday: 30, failedToday: 2 },
          dispatch: { unassigned: 5 },
          merchants: { pending: 3 },
        }),
      ),
    )
    renderApp('/admin')
    const tile = (await screen.findByText('Waiting for a courier')).closest('div')!
    expect(tile).toHaveTextContent('5')
    expect(screen.getByText('Parcels today').closest('div')).toHaveTextContent('42')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('degrades a tile instead of failing the page when one upstream is down', async () => {
    server.use(
      http.get(`${API}/api/v1/dashboard/summary`, () =>
        HttpResponse.json({
          parcels: { today: 42, outForDelivery: 7, deliveredToday: 30, failedToday: 2 },
          dispatch: null,
          merchants: { pending: 3 },
        }),
      ),
    )
    renderApp('/admin')
    const tile = (await screen.findByText('Waiting for a courier')).closest('div')!
    expect(tile).toHaveTextContent('Unavailable')
    expect(screen.getByText('Parcels today').closest('div')).toHaveTextContent('42')
    expect(screen.getByRole('status')).toHaveTextContent('temporarily unavailable')
  })
})

describe('users', () => {
  beforeEach(() => mockSession(['ADMIN']))
  // mockSession issues a token whose sub is "u-1" (see test utils), so u-1 is "me".
  const me = user({ id: 'u-1', fullName: 'Admin Me', email: 'a@b.lk', roles: ['ADMIN'] })

  it('lets only an ADMIN in, not a dispatcher', async () => {
    mockSession(['DISPATCHER'])
    renderApp('/admin/users')
    expect(await screen.findByRole('heading', { name: /403/ })).toBeInTheDocument()
  })

  it('sends filters to the API from the URL', async () => {
    const seen: URL[] = []
    server.use(
      http.get(
        `${API}/api/v1/users`,
        ({ request }) => (seen.push(new URL(request.url)), HttpResponse.json(page([user()]))),
      ),
    )
    renderApp('/admin/users?role=COURIER&status=SUSPENDED')
    await screen.findByText('Nimal Perera')
    expect(seen[0]?.searchParams.get('role')).toBe('COURIER')
    expect(seen[0]?.searchParams.get('status')).toBe('SUSPENDED')
  })

  it('warns before suspending and then calls the API', async () => {
    let body: unknown
    server.use(
      http.get(`${API}/api/v1/users`, () => HttpResponse.json(page([user()]))),
      http.patch(
        `${API}/api/v1/users/u-2/status`,
        async ({ request }) => (
          (body = await request.json()),
          HttpResponse.json(user({ status: 'SUSPENDED' }))
        ),
      ),
    )
    renderApp('/admin/users')
    await userEvent.click(await screen.findByRole('button', { name: 'Suspend Nimal Perera' }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('signed out everywhere')
    expect(body).toBeUndefined()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Suspend' }))
    await waitFor(() => expect(body).toEqual({ status: 'SUSPENDED' }))
  })

  it('stops an admin suspending themselves or removing their own ADMIN role', async () => {
    server.use(http.get(`${API}/api/v1/users`, () => HttpResponse.json(page([me, user()]))))
    renderApp('/admin/users')
    expect(await screen.findByRole('button', { name: 'Suspend Admin Me' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Suspend Nimal Perera' })).toBeEnabled()
    await userEvent.click(screen.getByRole('button', { name: 'Edit roles for Admin Me' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByLabelText(/ADMIN/)).toBeDisabled()
    expect(within(dialog).getByLabelText('CUSTOMER')).toBeEnabled()
  })

  it('saves the edited roles', async () => {
    let body: unknown
    server.use(
      http.get(`${API}/api/v1/users`, () => HttpResponse.json(page([user()]))),
      http.patch(
        `${API}/api/v1/users/u-2/roles`,
        async ({ request }) => ((body = await request.json()), HttpResponse.json(user())),
      ),
    )
    renderApp('/admin/users')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Edit roles for Nimal Perera' }),
    )
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByLabelText('COURIER'))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save roles' }))
    await waitFor(() => expect(body).toEqual({ roles: ['CUSTOMER', 'COURIER'] }))
  })
})

describe('merchants', () => {
  beforeEach(() => mockSession(['ADMIN']))

  it('approves a pending merchant with an optional note', async () => {
    let body: unknown
    let url = ''
    server.use(
      http.get(`${API}/api/v1/merchants`, ({ request }) =>
        HttpResponse.json(
          page(new URL(request.url).searchParams.get('status') === 'PENDING' ? [merchant()] : []),
        ),
      ),
      http.post(
        `${API}/api/v1/merchants/m-1/approve`,
        async ({ request }) => (
          (url = request.url),
          (body = await request.json()),
          HttpResponse.json(merchant({ status: 'APPROVED' }))
        ),
      ),
    )
    renderApp('/admin/merchants')
    await userEvent.click(await screen.findByRole('button', { name: 'Approve Lanka Crafts' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText(/Note/), 'Docs verified')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Approve' }))
    await waitFor(() => expect(body).toEqual({ note: 'Docs verified' }))
    expect(url).toMatch(/\/merchants\/m-1\/approve$/)
  })

  it('offers only the actions that make sense for each status', async () => {
    server.use(
      http.get(`${API}/api/v1/merchants`, ({ request }) => {
        const s = new URL(request.url).searchParams.get('status')
        return HttpResponse.json(page([merchant({ status: s as Merchant['status'] })]))
      }),
    )
    renderApp('/admin/merchants?status=APPROVED')
    expect(await screen.findByRole('button', { name: 'Suspend Lanka Crafts' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Approve Lanka Crafts' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('tab', { name: 'Rejected' }))
    await screen.findByText('Lanka Crafts')
    expect(screen.queryByRole('button', { name: /Lanka Crafts/ })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('tab', { name: 'Suspended' }))
    expect(
      await screen.findByRole('button', { name: 'Reactivate Lanka Crafts' }),
    ).toBeInTheDocument()
  })

  it('shows an empty state per tab', async () => {
    server.use(http.get(`${API}/api/v1/merchants`, () => HttpResponse.json(page([]))))
    renderApp('/admin/merchants')
    expect(await screen.findByText('No pending merchants')).toBeInTheDocument()
  })
})
