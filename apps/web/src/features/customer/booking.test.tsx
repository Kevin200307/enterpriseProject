import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { server } from '@/test/server'
import { API, mockSession, problem, renderApp } from '@/test/utils'

const estimate = {
  zoneFrom: 'Colombo',
  zoneTo: 'Kandy',
  baseFee: 450,
  weightSurcharge: 50,
  expressSurcharge: 100,
  total: 600,
  currency: 'LKR',
  ruleId: 'r-1',
  calculatedAt: '2026-10-09T10:00:00Z',
}

beforeEach(() => {
  mockSession(['CUSTOMER'])
  server.use(
    http.get(`${API}/api/v1/pricing/zones`, () =>
      HttpResponse.json([
        { id: 'z', name: 'Colombo', code: 'CMB', cities: ['Colombo', 'Kandy'], active: true },
      ]),
    ),
  )
})

async function fillAddress(group: 0 | 1, city: string) {
  const u = userEvent.setup()
  await u.type(screen.getAllByLabelText('Contact name')[group]!, 'Nimal Perera')
  await u.clear(screen.getAllByLabelText('Phone')[group]!)
  await u.type(screen.getAllByLabelText('Phone')[group]!, '+94771234567')
  await u.type(screen.getAllByLabelText('Address line 1')[group]!, '12 Main St')
  await u.type(screen.getAllByLabelText('City')[group]!, city)
  await u.type(screen.getAllByLabelText('Postal code')[group]!, '10100')
}

describe('booking form', () => {
  it('asks only for active zones (regression: query context must not leak into includeInactive)', async () => {
    const seen: URL[] = []
    server.use(
      http.get(
        `${API}/api/v1/pricing/zones`,
        ({ request }) => (seen.push(new URL(request.url)), HttpResponse.json([])),
      ),
    )
    renderApp('/deliveries/new')
    await screen.findByRole('heading', { name: 'New delivery', level: 1 })
    await waitFor(() => expect(seen.length).toBeGreaterThan(0))
    expect(seen[0]?.searchParams.has('includeInactive')).toBe(false)
  })

  it('shows validation errors when submitted empty, without calling the API', async () => {
    const created = vi.fn()
    server.use(http.post(`${API}/api/v1/parcels`, () => (created(), HttpResponse.json({}))))
    renderApp('/deliveries/new')
    await userEvent.click(await screen.findByRole('button', { name: 'Book delivery' }))
    expect(await screen.findAllByText('Contact name is required')).toHaveLength(2)
    expect(screen.getByText('Enter a weight above 0 (up to 2 decimals)')).toBeInTheDocument()
    expect(created).not.toHaveBeenCalled()
  })

  it('shows the live fee breakdown once the inputs are valid', async () => {
    server.use(http.post(`${API}/api/v1/pricing/estimate`, () => HttpResponse.json(estimate)))
    renderApp('/deliveries/new')
    await screen.findByRole('heading', { name: 'New delivery', level: 1 })
    await fillAddress(0, 'Colombo')
    await fillAddress(1, 'Kandy')
    await userEvent.type(screen.getByLabelText('Weight (kg)'), '1.5')
    expect(await screen.findByText('Total', {}, { timeout: 3000 })).toBeInTheDocument()
    const panel = screen.getByText('Price estimate').closest('[aria-live]') as HTMLElement
    expect(within(panel).getByText(/450/)).toBeInTheDocument()
    expect(within(panel).getByText(/600/)).toBeInTheDocument()
    expect(within(panel).getByText('Colombo → Kandy')).toBeInTheDocument()
  })

  it('says we do not deliver there yet on a 404 and blocks booking', async () => {
    server.use(
      http.post(`${API}/api/v1/pricing/estimate`, () => problem(404, 'RESOURCE_NOT_FOUND')),
    )
    renderApp('/deliveries/new')
    await screen.findByRole('heading', { name: 'New delivery', level: 1 })
    await fillAddress(0, 'Colombo')
    await fillAddress(1, 'Atlantis')
    await userEvent.type(screen.getByLabelText('Weight (kg)'), '2')
    expect(
      await screen.findByText(/don't deliver to Atlantis yet/, {}, { timeout: 3000 }),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Book delivery' })).toBeDisabled()
  })

  it('books the parcel and shows the tracking number', async () => {
    let body: Record<string, unknown> = {}
    server.use(
      http.post(`${API}/api/v1/pricing/estimate`, () => HttpResponse.json(estimate)),
      http.post(`${API}/api/v1/parcels`, async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({ id: 'p-1', trackingNumber: 'SD-ABCD2345' }, { status: 201 })
      }),
    )
    renderApp('/deliveries/new')
    await screen.findByRole('heading', { name: 'New delivery', level: 1 })
    await fillAddress(0, 'Colombo')
    await fillAddress(1, 'Kandy')
    await userEvent.type(screen.getByLabelText('Weight (kg)'), '1.5')
    await screen.findByText('Total', {}, { timeout: 3000 })
    await userEvent.click(screen.getByRole('button', { name: 'Book delivery' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByLabelText('tracking number')).toHaveTextContent('SD-ABCD2345')
    expect(body).toMatchObject({ size: 'SMALL', weightKg: 1.5, express: false })
    expect((body.pickup as { city: string }).city).toBe('Colombo')
  })

  it('maps server violations onto the weight field', async () => {
    server.use(
      http.post(`${API}/api/v1/pricing/estimate`, () => HttpResponse.json(estimate)),
      http.post(`${API}/api/v1/parcels`, () =>
        problem(400, 'VALIDATION_FAILED', {
          violations: [{ field: 'weightKg', message: 'exceeds max weight for this size' }],
        }),
      ),
    )
    renderApp('/deliveries/new')
    await screen.findByRole('heading', { name: 'New delivery', level: 1 })
    await fillAddress(0, 'Colombo')
    await fillAddress(1, 'Kandy')
    await userEvent.type(screen.getByLabelText('Weight (kg)'), '90')
    await screen.findByText('Total', {}, { timeout: 3000 })
    await userEvent.click(screen.getByRole('button', { name: 'Book delivery' }))
    await waitFor(() =>
      expect(screen.getByText('exceeds max weight for this size')).toBeInTheDocument(),
    )
  })
})
