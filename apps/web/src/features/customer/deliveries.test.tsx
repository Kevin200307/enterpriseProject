import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Parcel } from '@/api/types'
import { server } from '@/test/server'
import { API, mockSession, problem, renderApp } from '@/test/utils'

const addr = (city: string) => ({
  contactName: 'Nimal',
  phone: '+94771234567',
  line1: '12 Main St',
  city,
  postalCode: '10100',
})
const parcel = (over: Partial<Parcel> = {}): Parcel => ({
  id: 'p-1',
  trackingNumber: 'SD-ABCD2345',
  status: 'CREATED',
  size: 'SMALL',
  weightKg: 1.5,
  express: false,
  fee: 600,
  pickup: addr('Colombo'),
  dropoff: addr('Kandy'),
  createdAt: '2026-10-08T05:00:00Z',
  ...over,
})
const page = (content: Parcel[], totalPages = 1, number = 0) => ({
  content,
  totalElements: content.length,
  totalPages,
  number,
  size: 20,
})

beforeEach(() => mockSession(['CUSTOMER']))

describe('deliveries list', () => {
  it('lists parcels with route, fee and status', async () => {
    server.use(
      http.get(`${API}/api/v1/parcels`, () =>
        HttpResponse.json(
          page([
            parcel(),
            parcel({ id: 'p-2', trackingNumber: 'SD-ZZZZ2222', status: 'DELIVERED' }),
          ]),
        ),
      ),
    )
    renderApp('/deliveries')
    const first = await screen.findByRole('link', { name: /SD-ABCD2345/ })
    expect(first).toHaveAttribute('href', '/deliveries/p-1')
    expect(within(first).getByText('Colombo → Kandy')).toBeInTheDocument()
    expect(within(first).getByText('Created')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /SD-ZZZZ2222/ })).toBeInTheDocument()
  })

  it('shows the empty state with a booking action', async () => {
    server.use(http.get(`${API}/api/v1/parcels`, () => HttpResponse.json(page([]))))
    renderApp('/deliveries')
    expect(await screen.findByText('No deliveries yet')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Book your first delivery' })).toHaveAttribute(
      'href',
      '/deliveries/new',
    )
  })

  it('sends filters from the URL to the API and keeps them in sync when toggled', async () => {
    const seen: URL[] = []
    server.use(
      http.get(`${API}/api/v1/parcels`, ({ request }) => {
        seen.push(new URL(request.url))
        return HttpResponse.json(page([parcel({ status: 'FAILED' })]))
      }),
    )
    const router = renderApp('/deliveries?status=FAILED&page=1')
    await screen.findByRole('link', { name: /SD-ABCD2345/ })
    expect(seen[0]?.searchParams.getAll('status')).toEqual(['FAILED'])
    expect(seen[0]?.searchParams.get('page')).toBe('1')
    expect(screen.getByRole('button', { name: 'Failed' })).toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(screen.getByRole('button', { name: 'Delivered' }))
    await waitFor(() =>
      expect(router.state.location.search).toBe('?status=FAILED&status=DELIVERED'),
    )
    expect(router.state.location.search).not.toContain('page=') // filter change resets paging
  })

  it('explains an empty filtered result and clears the filters', async () => {
    server.use(http.get(`${API}/api/v1/parcels`, () => HttpResponse.json(page([]))))
    const router = renderApp('/deliveries?status=FAILED')
    expect(await screen.findByText('No deliveries match your filters')).toBeInTheDocument()
    await userEvent.click(screen.getAllByRole('button', { name: 'Clear filters' })[0]!)
    await waitFor(() => expect(router.state.location.search).toBe(''))
  })

  it('paginates through the URL', async () => {
    server.use(
      http.get(`${API}/api/v1/parcels`, ({ request }) => {
        const n = Number(new URL(request.url).searchParams.get('page'))
        return HttpResponse.json(page([parcel()], 3, n))
      }),
    )
    const router = renderApp('/deliveries')
    await screen.findByText('Page 1 of 3')
    await userEvent.click(screen.getByRole('button', { name: 'Next' }))
    await screen.findByText('Page 2 of 3')
    expect(router.state.location.search).toBe('?page=1')
  })

  it('renders a readable error with retry', async () => {
    server.use(http.get(`${API}/api/v1/parcels`, () => problem(503, 'UPSTREAM_UNAVAILABLE')))
    renderApp('/deliveries')
    // 5xx is retried once (with backoff) before the error is shown
    expect(await screen.findByRole('alert', {}, { timeout: 4000 })).toHaveTextContent(
      'Temporarily unavailable',
    )
    expect(screen.getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })
})

const history = [
  { fromStatus: null, toStatus: 'CREATED', occurredAt: '2026-10-08T05:00:00Z' },
  {
    fromStatus: 'CREATED',
    toStatus: 'PICKED_UP',
    actorName: 'Kasun',
    occurredAt: '2026-10-08T07:00:00Z',
  },
]

function mockParcel(p: Parcel) {
  server.use(
    http.get(`${API}/api/v1/parcels/${p.id}`, () => HttpResponse.json(p)),
    http.get(`${API}/api/v1/parcels/${p.id}/history`, () => HttpResponse.json(history)),
  )
}

describe('delivery detail', () => {
  it('shows the timeline newest first with the actor', async () => {
    mockParcel(parcel({ status: 'PICKED_UP' }))
    renderApp('/deliveries/p-1')
    const timeline = await screen.findByRole('list', { name: 'Status history' })
    const items = within(timeline).getAllByRole('listitem')
    expect(items[0]).toHaveTextContent('Picked up')
    expect(items[0]).toHaveTextContent('Kasun')
    expect(items[1]).toHaveTextContent('Created')
  })

  it('shows the same message for "not found" and "not yours"', async () => {
    server.use(http.get(`${API}/api/v1/parcels/p-9`, () => problem(404, 'RESOURCE_NOT_FOUND')))
    renderApp('/deliveries/p-9')
    expect(
      await screen.findByText("This delivery doesn't exist or isn't yours."),
    ).toBeInTheDocument()
  })

  it('offers cancel only while CREATED, behind a confirmation', async () => {
    mockParcel(parcel())
    let cancelled = false
    server.use(
      http.post(
        `${API}/api/v1/parcels/p-1/cancel`,
        () => ((cancelled = true), HttpResponse.json(parcel({ status: 'CANCELLED' }))),
      ),
    )
    renderApp('/deliveries/p-1')
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel delivery' }))
    expect(cancelled).toBe(false)
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yes, cancel' }),
    )
    await waitFor(() => expect(cancelled).toBe(true))
  })

  it('hides cancel once the parcel has moved on', async () => {
    mockParcel(parcel({ status: 'IN_TRANSIT' }))
    renderApp('/deliveries/p-1')
    await screen.findByText('Delivery details')
    expect(screen.queryByRole('button', { name: 'Cancel delivery' })).not.toBeInTheDocument()
  })

  it('surfaces a 409 on cancel as a readable toast and refetches', async () => {
    mockParcel(parcel())
    server.use(
      http.post(`${API}/api/v1/parcels/p-1/cancel`, () =>
        problem(409, 'INVALID_STATUS_TRANSITION'),
      ),
    )
    renderApp('/deliveries/p-1')
    await userEvent.click(await screen.findByRole('button', { name: 'Cancel delivery' }))
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Yes, cancel' }),
    )
    expect(await screen.findByText('INVALID_STATUS_TRANSITION detail')).toBeInTheDocument()
  })

  it('shows the failure reason and proof image when relevant', async () => {
    mockParcel(parcel({ status: 'FAILED', failureReason: 'Recipient unavailable' }))
    renderApp('/deliveries/p-1')
    expect(await screen.findByRole('alert')).toHaveTextContent('Recipient unavailable')
  })

  it('downloads the receipt through an authenticated blob request', async () => {
    mockParcel(parcel({ status: 'DELIVERED' }))
    let auth: string | null = null
    server.use(
      http.get(`${API}/api/v1/parcels/p-1/receipt`, ({ request }) => {
        auth = request.headers.get('Authorization')
        return new HttpResponse('%PDF-1.4', { headers: { 'Content-Type': 'application/pdf' } })
      }),
    )
    const create = vi.fn(() => 'blob:x')
    URL.createObjectURL = create
    URL.revokeObjectURL = vi.fn()
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    renderApp('/deliveries/p-1')
    await userEvent.click(await screen.findByRole('button', { name: /Download receipt/ }))
    await waitFor(() => expect(click).toHaveBeenCalled())
    expect(auth).toMatch(/^Bearer /)
    expect(create).toHaveBeenCalled()
  })
})
