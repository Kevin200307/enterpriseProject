import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@/test/userEvent'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import type { MerchantOrder } from '@/api/types'
import { makeJwt } from '@/test/utils'
import { server } from '@/test/server'
import { API, mockSession, problem, renderApp } from '@/test/utils'

const page = <T,>(content: T[]) => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  number: 0,
  size: 20,
})
const order = (over: Partial<MerchantOrder> = {}): MerchantOrder => ({
  merchantRef: 'ORD-1001',
  trackingNumber: 'SD-ABCD2345',
  status: 'IN_TRANSIT',
  destinationCity: 'Kandy',
  createdAt: '2026-10-08T05:00:00Z',
  ...over,
})

describe('merchant registration', () => {
  beforeEach(() => mockSession(['CUSTOMER']))

  it('validates before sending', async () => {
    renderApp('/merchant/register')
    await userEvent.click(await screen.findByRole('button', { name: 'Submit application' }))
    expect(await screen.findByText('Organisation name is required')).toBeInTheDocument()
    expect(screen.getByText('Use the format +94XXXXXXXXX')).toBeInTheDocument()
  })

  async function fill() {
    await userEvent.type(await screen.findByLabelText('Organisation name'), 'Lanka Crafts')
    await userEvent.type(screen.getByLabelText('Business registration number'), 'PV12345')
    await userEvent.type(screen.getByLabelText('Contact email'), 'shop@lanka.lk')
    await userEvent.type(screen.getByLabelText('Contact phone'), '112345678')
  }

  it('submits and lands on the pending page', async () => {
    let body: unknown
    server.use(
      http.post(
        `${API}/api/v1/merchants/register`,
        async ({ request }) => (
          (body = await request.json()),
          HttpResponse.json(
            { id: 'm-1', orgName: 'Lanka Crafts', status: 'PENDING' },
            { status: 201 },
          )
        ),
      ),
      http.get(`${API}/api/v1/merchants/me`, () =>
        HttpResponse.json({ id: 'm-1', orgName: 'Lanka Crafts', status: 'PENDING' }),
      ),
    )
    const router = renderApp('/merchant/register')
    await fill()
    await userEvent.click(screen.getByRole('button', { name: 'Submit application' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/merchant/pending'))
    expect(body).toMatchObject({ orgName: 'Lanka Crafts', contactPhone: '+94112345678' })
    expect(await screen.findByText('Application under review')).toBeInTheDocument()
  })

  it('points to the status page when an application already exists', async () => {
    server.use(http.post(`${API}/api/v1/merchants/register`, () => problem(409, 'CONFLICT')))
    renderApp('/merchant/register')
    await fill()
    await userEvent.click(screen.getByRole('button', { name: 'Submit application' }))
    expect(await screen.findByRole('link', { name: 'Check its status' })).toHaveAttribute(
      'href',
      '/merchant/pending',
    )
  })
})

describe('merchant pending', () => {
  beforeEach(() => mockSession(['CUSTOMER']))

  it('invites a user with no application to apply', async () => {
    server.use(http.get(`${API}/api/v1/merchants/me`, () => problem(404, 'RESOURCE_NOT_FOUND')))
    renderApp('/merchant/pending')
    expect(await screen.findByRole('link', { name: 'Apply now' })).toHaveAttribute(
      'href',
      '/merchant/register',
    )
  })

  it.each([
    ['REJECTED', 'Application not approved'],
    ['SUSPENDED', 'Account suspended'],
  ] as const)('explains a %s application', async (status, title) => {
    server.use(
      http.get(`${API}/api/v1/merchants/me`, () =>
        HttpResponse.json({ id: 'm', orgName: 'Lanka Crafts', status }),
      ),
    )
    renderApp('/merchant/pending')
    expect(await screen.findByText(title)).toBeInTheDocument()
  })

  it('refreshes the token on approval so the MERCHANT role arrives, then opens the console', async () => {
    let refreshes = 0
    server.use(
      http.get(`${API}/api/v1/merchants/me`, () =>
        HttpResponse.json({ id: 'm', orgName: 'Lanka Crafts', status: 'APPROVED' }),
      ),
      http.post(`${API}/api/v1/auth/refresh`, () =>
        ++refreshes === 1
          ? HttpResponse.json({ accessToken: makeJwt(['CUSTOMER']) })
          : HttpResponse.json({ accessToken: makeJwt(['CUSTOMER', 'MERCHANT']) }),
      ),
      http.get(`${API}/api/v1/merchants/orders`, () => HttpResponse.json(page([]))),
    )
    const router = renderApp('/merchant/pending')
    await userEvent.click(await screen.findByRole('button', { name: 'Activate merchant features' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/merchant/orders'))
    expect(await screen.findByText('No orders yet')).toBeInTheDocument()
  })
})

describe('merchant orders', () => {
  beforeEach(() => mockSession(['MERCHANT']))

  it('lists orders and searches by reference', async () => {
    const seen: (string | null)[] = []
    server.use(
      http.get(
        `${API}/api/v1/merchants/orders`,
        ({ request }) => (
          seen.push(new URL(request.url).searchParams.get('q')),
          HttpResponse.json(page([order()]))
        ),
      ),
    )
    renderApp('/merchant/orders')
    const row = (await screen.findByText('ORD-1001')).closest('tr')!
    expect(within(row).getByRole('link', { name: 'SD-ABCD2345' })).toHaveAttribute(
      'href',
      '/track/SD-ABCD2345',
    )
    await userEvent.type(screen.getByLabelText('Search by reference'), 'ORD-1001')
    await userEvent.click(screen.getByRole('button', { name: 'Search' }))
    await waitFor(() => expect(seen).toContain('ORD-1001'))
  })

  it('sends an unapproved merchant to the status page', async () => {
    server.use(
      http.get(`${API}/api/v1/merchants/orders`, () => problem(403, 'MERCHANT_NOT_APPROVED')),
      http.get(`${API}/api/v1/merchants/me`, () =>
        HttpResponse.json({ id: 'm', orgName: 'Lanka Crafts', status: 'SUSPENDED' }),
      ),
    )
    const router = renderApp('/merchant/orders')
    await waitFor(() => expect(router.state.location.pathname).toBe('/merchant/pending'))
  })

  it('keeps non-merchants out of the console', async () => {
    mockSession(['CUSTOMER'])
    renderApp('/merchant/orders')
    expect(await screen.findByRole('heading', { name: /403/ })).toBeInTheDocument()
  })
})

describe('merchant new order', () => {
  beforeEach(() => mockSession(['MERCHANT']))

  it('requires the merchant reference and sends it with the parcel', async () => {
    let body: Record<string, unknown> | undefined
    server.use(
      http.post(`${API}/api/v1/pricing/estimate`, () =>
        HttpResponse.json({
          zoneFrom: 'A',
          zoneTo: 'B',
          baseFee: 1,
          weightSurcharge: 0,
          expressSurcharge: 0,
          total: 1,
          currency: 'LKR',
          ruleId: 'r',
          calculatedAt: '2026-10-09T00:00:00Z',
        }),
      ),
      http.post(
        `${API}/api/v1/merchants/orders`,
        async ({ request }) => (
          expect(request.headers.get('Idempotency-Key')).toBeTruthy(),
          (body = (await request.json()) as Record<string, unknown>),
          HttpResponse.json({ id: 'p', trackingNumber: 'SD-ABCD2345' }, { status: 201 })
        ),
      ),
    )
    renderApp('/merchant/orders/new')
    await screen.findByRole('heading', { name: 'New order', level: 1 })
    const u = userEvent.setup()
    for (const g of [0, 1]) {
      await u.type(screen.getAllByLabelText('Contact name')[g]!, 'Nimal')
      await u.clear(screen.getAllByLabelText('Phone')[g]!)
      await u.type(screen.getAllByLabelText('Phone')[g]!, '+94771234567')
      await u.type(screen.getAllByLabelText('Address line 1')[g]!, '1 St')
      await u.type(screen.getAllByLabelText('City')[g]!, g ? 'Kandy' : 'Colombo')
      await u.type(screen.getAllByLabelText('Postal code')[g]!, '100')
    }
    await u.type(screen.getByLabelText('Weight (kg)'), '2')
    await u.click(screen.getByRole('radio', { name: /Small/ }))
    await screen.findByText('Total', {}, { timeout: 3000 })
    await u.click(screen.getByRole('button', { name: 'Book delivery' }))
    expect(await screen.findByText('Your reference is required')).toBeInTheDocument()
    expect(body).toBeUndefined()
    await u.type(screen.getByLabelText('Your reference'), 'ORD-2002')
    await u.click(screen.getByRole('button', { name: 'Book delivery' }))
    expect(await screen.findByRole('dialog')).toHaveTextContent('Order created')
    expect(body).toMatchObject({ merchantRef: 'ORD-2002' })
  })

  it('does not show the reference field to ordinary customers', async () => {
    mockSession(['CUSTOMER'])
    renderApp('/deliveries/new')
    await screen.findByRole('heading', { name: 'New delivery', level: 1 })
    expect(screen.queryByLabelText('Your reference')).not.toBeInTheDocument()
  })
})
