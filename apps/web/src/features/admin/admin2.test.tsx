import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@/test/userEvent'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Courier, DailyStat, Parcel } from '@/api/types'
import { statsCsv, toCsv } from '@/lib/csv'
import { server } from '@/test/server'
import { API, mockSession, problem, renderApp } from '@/test/utils'

const page = <T,>(content: T[], totalPages = 1, number = 0) => ({
  content,
  totalElements: content.length,
  totalPages,
  page: number,
  number,
  size: 20,
})

describe('toCsv', () => {
  it('quotes commas, quotes and newlines per RFC 4180 and ends lines with CRLF', () => {
    const csv = toCsv(
      ['a', 'b'],
      [
        ['plain', 'x,y'],
        ['say "hi"', 'line1\nline2'],
        [1, 0],
      ],
    )
    expect(csv).toBe('a,b\r\nplain,"x,y"\r\n"say ""hi""","line1\nline2"\r\n1,0\r\n')
  })
  it('builds the daily report in the documented column order', () => {
    expect(statsCsv([{ date: '2026-10-08', created: 5, completed: 3, failed: 1 }])).toBe(
      'date,created,completed,failed\r\n2026-10-08,5,3,1\r\n',
    )
  })
})

describe('reports', () => {
  beforeEach(() => mockSession(['ADMIN']))
  const stats: DailyStat[] = [
    { date: '2026-10-07', created: 4, completed: 3, failed: 1 },
    { date: '2026-10-08', created: 6, completed: 5, failed: 0 },
  ]

  it('shows rows and totals, and exports exactly what is shown', async () => {
    server.use(http.get(`${API}/api/v1/dispatch/stats/daily`, () => HttpResponse.json(stats)))
    let blob: Blob | undefined
    URL.createObjectURL = (b) => ((blob = b as Blob), 'blob:csv')
    URL.revokeObjectURL = () => {}
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    renderApp('/admin/reports?from=2026-10-07&to=2026-10-08')
    const total = (await screen.findByText('Total')).closest('tr')!
    expect(total).toHaveTextContent('10')
    await userEvent.click(screen.getByRole('button', { name: /Export CSV/ }))
    expect(click).toHaveBeenCalled()
    expect(await blob!.text()).toBe(
      'date,created,completed,failed\r\n2026-10-07,4,3,1\r\n2026-10-08,6,5,0\r\n',
    )
  })

  it('sends the chosen range and disables export when empty', async () => {
    const seen: URL[] = []
    server.use(
      http.get(
        `${API}/api/v1/dispatch/stats/daily`,
        ({ request }) => (seen.push(new URL(request.url)), HttpResponse.json([])),
      ),
    )
    renderApp('/admin/reports?from=2026-10-01&to=2026-10-05')
    expect(await screen.findByText('No activity in this range')).toBeInTheDocument()
    expect(seen[0]?.searchParams.get('from')).toBe('2026-10-01')
    expect(seen[0]?.searchParams.get('to')).toBe('2026-10-05')
    expect(screen.getByRole('button', { name: /Export CSV/ })).toBeDisabled()
  })

  it('is admin-only', async () => {
    mockSession(['DISPATCHER'])
    renderApp('/admin/reports')
    expect(await screen.findByRole('heading', { name: /403/ })).toBeInTheDocument()
  })
})

describe('couriers', () => {
  beforeEach(() => mockSession(['DISPATCHER']))
  const courier = (over: Partial<Courier> = {}): Courier => ({
    id: 'c-1',
    userId: 'u-9',
    name: 'Kasun',
    vehicleType: 'BIKE',
    homeZoneCode: 'CMB',
    phone: '+94771234567',
    maxDailyLoad: 20,
    active: true,
    ...over,
  })

  it('lists couriers for a dispatcher and passes filters through', async () => {
    const seen: URL[] = []
    server.use(
      http.get(
        `${API}/api/v1/couriers`,
        ({ request }) => (seen.push(new URL(request.url)), HttpResponse.json(page([courier()]))),
      ),
    )
    renderApp('/admin/couriers?vehicle=VAN&active=true')
    expect(await screen.findByText('Kasun')).toBeInTheDocument()
    expect(seen[0]?.searchParams.get('vehicle')).toBe('VAN')
    expect(seen[0]?.searchParams.get('active')).toBe('true')
  })

  it('shows the 409 detail (tracking numbers to reassign) when deactivating', async () => {
    server.use(
      http.get(`${API}/api/v1/couriers`, () => HttpResponse.json(page([courier()]))),
      http.patch(`${API}/api/v1/couriers/c-1`, () =>
        problem(409, 'CONFLICT', { detail: 'Reassign SD-AAAA2222, SD-BBBB3333 first' }),
      ),
    )
    renderApp('/admin/couriers')
    await userEvent.click(await screen.findByRole('button', { name: 'Edit Kasun' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByLabelText('Active'))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save courier' }))
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      'Reassign SD-AAAA2222, SD-BBBB3333 first',
    )
  })

  it('falls back to a user-id field when a dispatcher cannot list users', async () => {
    let body: Record<string, unknown> = {}
    server.use(
      http.get(`${API}/api/v1/couriers`, () => HttpResponse.json(page([]))),
      http.get(`${API}/api/v1/users`, () => problem(403, 'ACCESS_DENIED')),
      http.get(`${API}/api/v1/pricing/zones`, () =>
        HttpResponse.json([{ id: 'z', name: 'Colombo', code: 'CMB', cities: [], active: true }]),
      ),
      http.post(
        `${API}/api/v1/couriers`,
        async ({ request }) => (
          (body = (await request.json()) as Record<string, unknown>),
          HttpResponse.json(courier(), { status: 201 })
        ),
      ),
    )
    renderApp('/admin/couriers')
    await userEvent.click(await screen.findByRole('button', { name: 'Register courier' }))
    const dialog = await screen.findByRole('dialog')
    await waitFor(() =>
      expect(within(dialog).getByLabelText('Courier user')).toHaveProperty('tagName', 'INPUT'),
    )
    await userEvent.type(within(dialog).getByLabelText('Courier user'), 'u-77')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Home zone' }))
    await userEvent.click(await within(dialog).findByRole('option', { name: 'Colombo' }))
    await userEvent.clear(within(dialog).getByLabelText('Phone'))
    await userEvent.type(within(dialog).getByLabelText('Phone'), '+94771234567')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save courier' }))
    await waitFor(() =>
      expect(body).toMatchObject({
        userId: 'u-77',
        homeZoneCode: 'CMB',
        maxDailyLoad: 20,
        vehicleType: 'BIKE',
      }),
    )
  })

  it('validates the daily load', async () => {
    server.use(http.get(`${API}/api/v1/couriers`, () => HttpResponse.json(page([courier()]))))
    renderApp('/admin/couriers')
    await userEvent.click(await screen.findByRole('button', { name: 'Edit Kasun' }))
    const dialog = await screen.findByRole('dialog')
    const load = within(dialog).getByLabelText('Max daily load')
    await userEvent.clear(load)
    await userEvent.type(load, '0')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save courier' }))
    expect(await within(dialog).findByText('Whole number between 1 and 200')).toBeInTheDocument()
  })
})

describe('all parcels', () => {
  beforeEach(() => mockSession(['DISPATCHER']))
  const addr = (city: string) => ({
    contactName: 'N',
    phone: '+94771234567',
    line1: '1 St',
    city,
    postalCode: '1',
  })
  const parcel = (over: Partial<Parcel> = {}): Parcel => ({
    id: 'p-1',
    trackingNumber: 'SD-ABCD2345',
    customerId: 'u-1',
    merchantId: null,
    assigneeId: null,
    status: 'IN_TRANSIT',
    size: 'SMALL',
    weightKg: 1,
    fee: 600,
    currency: 'LKR',
    pickup: addr('Colombo'),
    dropoff: addr('Kandy'),
    createdAt: '2026-10-08T05:00:00Z',
    updatedAt: '2026-10-08T05:00:00Z',
    version: 0,
    ...over,
  })

  it('offers only legal next statuses and requires a reason for FAILED', async () => {
    let body: Record<string, unknown> | undefined
    server.use(
      http.get(`${API}/api/v1/parcels`, () =>
        HttpResponse.json(page([parcel({ status: 'OUT_FOR_DELIVERY' })])),
      ),
      http.patch(
        `${API}/api/v1/parcels/p-1/status`,
        async ({ request }) => (
          (body = (await request.json()) as Record<string, unknown>),
          HttpResponse.json({})
        ),
      ),
    )
    renderApp('/admin/parcels')
    await userEvent.click(
      await screen.findByRole('button', { name: 'Change status of SD-ABCD2345' }),
    )
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'New status' }))
    const listbox = within(dialog).getByRole('listbox')
    const options = within(listbox)
      .getAllByRole('option')
      .map((o) => o.textContent)
    expect(options).toEqual(['Delivered', 'Failed'])
    await userEvent.click(within(listbox).getByRole('option', { name: 'Failed' }))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Update status' }))
    expect(await within(dialog).findByText('A reason is required.')).toBeInTheDocument()
    expect(body).toBeUndefined()
    await userEvent.type(within(dialog).getByLabelText('Failure reason'), 'Refused')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Update status' }))
    await waitFor(() => expect(body).toEqual({ status: 'FAILED', failedReason: 'Refused' }))
  })

  it('says a final-state parcel cannot be changed', async () => {
    server.use(
      http.get(`${API}/api/v1/parcels`, () =>
        HttpResponse.json(page([parcel({ status: 'DELIVERED' })])),
      ),
    )
    renderApp('/admin/parcels')
    await userEvent.click(await screen.findByRole('button', { name: /Change status/ }))
    expect(await screen.findByText(/final state/)).toBeInTheDocument()
  })

  it('searches by tracking number and rejects a malformed one without a request', async () => {
    const called = vi.fn()
    server.use(
      http.get(`${API}/api/v1/parcels/by-tracking/SD-ABCD2345/details`, () =>
        HttpResponse.json(parcel()),
      ),
      http.get(
        `${API}/api/v1/parcels/by-tracking/:tn/details`,
        () => (called(), problem(404, 'RESOURCE_NOT_FOUND')),
      ),
    )
    renderApp('/admin/parcels')
    const box = await screen.findByLabelText('Tracking number')
    await userEvent.type(box, 'nope')
    await userEvent.click(screen.getByRole('button', { name: 'Search' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('look like SD-')
    expect(called).not.toHaveBeenCalled()
    await userEvent.clear(box)
    await userEvent.type(box, 'sd-abcd2345')
    await userEvent.click(screen.getByRole('button', { name: 'Search' }))
    expect(await screen.findByText('SD-ABCD2345', { selector: 'p' })).toBeInTheDocument()
  })
})
