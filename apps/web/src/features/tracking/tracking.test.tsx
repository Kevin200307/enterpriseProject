import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@/test/userEvent'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { PublicTracking } from '@/api/types'
import type { Transport, TransportHandlers } from '@/realtime/ladder'
import { server } from '@/test/server'
import { API, mockSession, problem, renderApp } from '@/test/utils'

const live = vi.hoisted(() => ({
  handlers: undefined as TransportHandlers | undefined,
  started: [] as string[],
}))

vi.mock('@/realtime/transports', () => ({
  defaultTransports: (): Transport[] => [
    {
      state: 'live',
      start: (tn, h) => {
        live.handlers = h
        live.started.push(tn)
        return () => {}
      },
    },
  ],
}))

const tracking = (over: Partial<PublicTracking> = {}): PublicTracking => ({
  trackingNumber: 'SD-ABCD2345',
  status: 'IN_TRANSIT',
  originCity: 'Colombo',
  destinationCity: 'Kandy',
  courierFirstName: 'Kasun',
  eta: '2026-10-10T09:00:00Z',
  updatedAt: new Date().toISOString(),
  timeline: [
    { status: 'CREATED', occurredAt: '2026-10-08T05:00:00Z' },
    { status: 'IN_TRANSIT', occurredAt: '2026-10-09T05:00:00Z', note: 'Left Colombo hub' },
  ],
  ...over,
})

beforeEach(() => {
  live.handlers = undefined
  live.started.length = 0
  mockSession()
})

describe('public tracking page', () => {
  it('shows status, route, courier first name and the timeline without app chrome', async () => {
    server.use(http.get(`${API}/api/v1/tracking/SD-ABCD2345`, () => HttpResponse.json(tracking())))
    renderApp('/track/SD-ABCD2345')
    expect(await screen.findByRole('heading', { name: 'In transit' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Route' })).toHaveTextContent('Colombo → Kandy')
    expect(screen.getByRole('region', { name: 'Route' })).toHaveTextContent('Kasun')
    const items = within(screen.getByRole('list', { name: 'Status history' })).getAllByRole(
      'listitem',
    )
    expect(items[0]).toHaveTextContent('In transit')
    expect(items[0]).toHaveTextContent('Left Colombo hub')
    expect(screen.queryByRole('navigation', { name: 'Main' })).not.toBeInTheDocument()
    expect(document.title).toBe('Tracking SD-ABCD2345 · SwiftDrop')
  })

  it('updates in place when a live push arrives, with no refetch', async () => {
    let fetches = 0
    server.use(
      http.get(
        `${API}/api/v1/tracking/SD-ABCD2345`,
        () => (fetches++, HttpResponse.json(tracking())),
      ),
    )
    renderApp('/track/SD-ABCD2345')
    await screen.findByRole('heading', { name: 'In transit' })
    await waitFor(() => expect(live.handlers).toBeDefined())
    act(() => live.handlers!.onOpen())
    expect(await screen.findByText('Live')).toBeInTheDocument()

    act(() =>
      live.handlers!.onMessage(
        tracking({
          status: 'OUT_FOR_DELIVERY',
          updatedAt: new Date(Date.now() + 1000).toISOString(),
          timeline: [
            ...tracking().timeline,
            { status: 'OUT_FOR_DELIVERY', occurredAt: '2026-10-10T03:00:00Z' },
          ],
        }),
      ),
    )
    expect(await screen.findByRole('heading', { name: 'Out for delivery' })).toBeInTheDocument()
    expect(fetches).toBe(1)
  })

  it('ignores an older push that arrives late', async () => {
    server.use(
      http.get(`${API}/api/v1/tracking/SD-ABCD2345`, () =>
        HttpResponse.json(tracking({ status: 'OUT_FOR_DELIVERY' })),
      ),
    )
    renderApp('/track/SD-ABCD2345')
    await screen.findByRole('heading', { name: 'Out for delivery' })
    await waitFor(() => expect(live.handlers).toBeDefined())
    act(() =>
      live.handlers!.onMessage(tracking({ status: 'CREATED', updatedAt: '2020-01-01T00:00:00Z' })),
    )
    expect(screen.getByRole('heading', { name: 'Out for delivery' })).toBeInTheDocument()
  })

  it('does not even call the API for a malformed tracking number', async () => {
    const called = vi.fn()
    server.use(
      http.get(`${API}/api/v1/tracking/:tn`, () => (called(), HttpResponse.json(tracking()))),
    )
    renderApp('/track/SD-INVALID')
    expect(await screen.findByText('Invalid tracking number')).toBeInTheDocument()
    expect(called).not.toHaveBeenCalled()
    expect(live.started).toHaveLength(0)
  })

  it('accepts a tracking number generated from the parcel sequence', async () => {
    const generatedTracking = 'SD-00000001'
    server.use(
      http.get(`${API}/api/v1/tracking/${generatedTracking}`, () =>
        HttpResponse.json(tracking({ trackingNumber: generatedTracking })),
      ),
    )
    renderApp(`/track/${generatedTracking}`)
    expect(await screen.findByRole('heading', { name: 'In transit' })).toBeInTheDocument()
    expect(screen.getByText(generatedTracking)).toBeInTheDocument()
  })

  it('gives the same flat message for any unknown parcel', async () => {
    server.use(
      http.get(`${API}/api/v1/tracking/SD-ZZZZ2222`, () => problem(404, 'RESOURCE_NOT_FOUND')),
    )
    renderApp('/track/SD-ZZZZ2222')
    expect(await screen.findByText("We couldn't find that parcel")).toBeInTheDocument()
    expect(live.started).toHaveLength(0)
  })

  it('tells the visitor how long to wait when rate limited', async () => {
    server.use(
      http.get(`${API}/api/v1/tracking/SD-ABCD2345`, () =>
        HttpResponse.json(
          { title: 'x', status: 429, errorCode: 'RATE_LIMITED' },
          {
            status: 429,
            headers: { 'Retry-After': '20', 'Content-Type': 'application/problem+json' },
          },
        ),
      ),
    )
    renderApp('/track/SD-ABCD2345')
    expect(await screen.findByText(/wait 20 seconds/)).toBeInTheDocument()
  })

  it('shows a failed delivery as a problem state rather than a progress bar', async () => {
    server.use(
      http.get(`${API}/api/v1/tracking/SD-ABCD2345`, () =>
        HttpResponse.json(tracking({ status: 'FAILED' })),
      ),
    )
    renderApp('/track/SD-ABCD2345')
    expect(await screen.findByText(/Delivery was unsuccessful/)).toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Delivery progress' })).not.toBeInTheDocument()
  })

  it('never renders anything beyond the masked fields it was given', async () => {
    server.use(http.get(`${API}/api/v1/tracking/SD-ABCD2345`, () => HttpResponse.json(tracking())))
    renderApp('/track/SD-ABCD2345')
    await screen.findByRole('heading', { name: 'In transit' })
    expect(document.body.textContent).not.toMatch(/\+94\d{9}|[0-9a-f]{8}-[0-9a-f]{4}-/i)
  })
})

describe('tracking lookup', () => {
  it('rejects a malformed number inline and navigates for a valid one', async () => {
    server.use(http.get(`${API}/api/v1/tracking/SD-ABCD2345`, () => HttpResponse.json(tracking())))
    const router = renderApp('/track')
    const input = await screen.findByLabelText('Tracking number')
    await userEvent.type(input, 'hello')
    await userEvent.click(screen.getByRole('button', { name: 'Track' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(
      "doesn't look like a SwiftDrop tracking number",
    )
    await userEvent.clear(input)
    await userEvent.type(input, 'sd-abcd2345')
    await userEvent.click(screen.getByRole('button', { name: 'Track' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/track/SD-ABCD2345'))
  })

  it('accepts the tracking number returned for a newly created parcel', async () => {
    const generatedTracking = 'SD-00000001'
    server.use(
      http.get(`${API}/api/v1/tracking/${generatedTracking}`, () =>
        HttpResponse.json(tracking({ trackingNumber: generatedTracking })),
      ),
    )
    const router = renderApp('/track')
    const input = await screen.findByLabelText('Tracking number')
    await userEvent.type(input, generatedTracking)
    await userEvent.click(screen.getByRole('button', { name: 'Track' }))
    await waitFor(() => expect(router.state.location.pathname).toBe(`/track/${generatedTracking}`))
    expect(await screen.findByRole('heading', { name: 'In transit' })).toBeInTheDocument()
  })
})
