import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@/test/userEvent'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import type { QueueItem, WorkloadRow } from '@/api/types'
import { ageTone } from '@/lib/format'
import { server } from '@/test/server'
import { API, mockSession, problem, renderApp } from '@/test/utils'

const hoursAgo = (h: number) => new Date(Date.now() - h * 3_600_000).toISOString()
const item = (over: Partial<QueueItem> = {}): QueueItem => ({
  parcelId: 'p-1',
  trackingNumber: 'SD-ABCD2345',
  pickupCity: 'Colombo',
  size: 'SMALL',
  weightKg: 1.5,
  createdAt: hoursAgo(1),
  ...over,
})
const courier = (over: Partial<WorkloadRow> = {}): WorkloadRow => ({
  courierId: 'c-1',
  name: 'Kasun',
  active: true,
  activeAssignments: 2,
  deliveredToday: 4,
  failedToday: 1,
  remainingCapacity: 6,
  ...over,
})
const suggestions = [
  { courierId: 'c-1', name: 'Kasun', reason: 'Same zone, lowest load', remainingCapacity: 6 },
]

function serve(queue: QueueItem[], workload: WorkloadRow[] = [courier()]) {
  server.use(
    http.get(`${API}/api/v1/dispatch/queue`, () => HttpResponse.json(queue)),
    http.get(`${API}/api/v1/dispatch/workload`, () => HttpResponse.json(workload)),
    http.get(`${API}/api/v1/dispatch/suggest`, () => HttpResponse.json(suggestions)),
  )
}

beforeEach(() => mockSession(['DISPATCHER']))

describe('ageTone', () => {
  it('turns amber after 4 hours and red after 24', () => {
    expect([ageTone(hoursAgo(1)), ageTone(hoursAgo(5)), ageTone(hoursAgo(30))]).toEqual([
      'ok',
      'warn',
      'late',
    ])
  })
})

describe('dispatch board', () => {
  it('lists the oldest parcels first with age and suggested couriers', async () => {
    serve([
      item({ parcelId: 'p-new', trackingNumber: 'SD-NEWW2222', createdAt: hoursAgo(1) }),
      item({ parcelId: 'p-old', trackingNumber: 'SD-OLDD2222', createdAt: hoursAgo(30) }),
    ])
    renderApp('/admin/dispatch')
    const rows = await screen
      .findAllByRole('listitem', {}, { timeout: 4000 })
      .then((all) => all.filter((r) => /SD-/.test(r.textContent ?? '')))
    expect(rows[0]).toHaveTextContent('SD-OLDD2222')
    expect(rows[0]).toHaveTextContent(/Waiting 1 d/)
    expect(await within(rows[0]!).findByRole('button', { name: /Kasun · 6 free/ })).toHaveAttribute(
      'title',
      'Same zone, lowest load',
    )
  })

  it('assigns with one click on a suggested chip', async () => {
    let body: unknown
    serve([item()])
    server.use(
      http.post(
        `${API}/api/v1/dispatch/assignments`,
        async ({ request }) => (
          (body = await request.json()),
          new HttpResponse(null, { status: 200 })
        ),
      ),
    )
    renderApp('/admin/dispatch')
    await userEvent.click(await screen.findByRole('button', { name: /Kasun · 6 free/ }))
    await waitFor(() => expect(body).toEqual({ parcelId: 'p-1', courierId: 'c-1' }))
    expect(await screen.findByText('SD-ABCD2345 assigned')).toBeInTheDocument()
  })

  it('shows an empty state when the queue is clear', async () => {
    serve([])
    renderApp('/admin/dispatch')
    expect(await screen.findByText('All caught up')).toBeInTheDocument()
  })

  it('disables inactive and full couriers in the picker', async () => {
    serve(
      [item()],
      [
        courier(),
        courier({ courierId: 'c-2', name: 'Full Fiona', remainingCapacity: 0 }),
        courier({ courierId: 'c-3', name: 'Idle Ivan', active: false }),
      ],
    )
    renderApp('/admin/dispatch')
    await userEvent.click(await screen.findByRole('button', { name: 'Assign…' }))
    const dialog = await screen.findByRole('dialog')
    expect(await within(dialog).findByRole('button', { name: /Kasun/ })).toBeEnabled()
    expect(within(dialog).getByRole('button', { name: /Full Fiona/ })).toBeDisabled()
    expect(within(dialog).getByRole('button', { name: /Idle Ivan/ })).toBeDisabled()
    await userEvent.type(within(dialog).getByLabelText('Search couriers'), 'ful')
    expect(within(dialog).queryByRole('button', { name: /Kasun/ })).not.toBeInTheDocument()
  })

  it('offers to reassign on 409 and retries with reassign=true', async () => {
    const urls: string[] = []
    serve([item()])
    server.use(
      http.post(`${API}/api/v1/dispatch/assignments`, ({ request }) => {
        urls.push(request.url)
        return urls.length === 1
          ? problem(409, 'CONFLICT')
          : new HttpResponse(null, { status: 200 })
      }),
    )
    renderApp('/admin/dispatch')
    await userEvent.click(await screen.findByRole('button', { name: /Kasun · 6 free/ }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog).toHaveTextContent('assigned to another courier')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Reassign' }))
    await waitFor(() => expect(urls).toHaveLength(2))
    expect(urls[0]).not.toContain('reassign')
    expect(urls[1]).toContain('reassign=true')
  })

  it('passes the zone filter to the queue request', async () => {
    const zones: (string | null)[] = []
    serve([item()])
    server.use(
      http.get(`${API}/api/v1/pricing/zones`, () =>
        HttpResponse.json([{ id: 'z1', name: 'Colombo', code: 'CMB', cities: [], active: true }]),
      ),
      http.get(
        `${API}/api/v1/dispatch/queue`,
        ({ request }) => (
          zones.push(new URL(request.url).searchParams.get('zone')),
          HttpResponse.json([item()])
        ),
      ),
    )
    renderApp('/admin/dispatch')
    await screen.findByText('SD-ABCD2345')
    const zoneSelect = screen.getByRole('button', { name: 'Pickup zone' })
    await userEvent.click(zoneSelect)
    await waitFor(() =>
      expect(screen.getByRole('option', { name: 'Colombo' })).toBeInTheDocument(),
    )
    await userEvent.click(screen.getByRole('option', { name: 'Colombo' }))
    await waitFor(() => expect(zones[zones.length - 1]).toBe('CMB'))
  })

  it('keeps customers out', async () => {
    mockSession(['CUSTOMER'])
    renderApp('/admin/dispatch')
    expect(await screen.findByRole('heading', { name: /403/ })).toBeInTheDocument()
  })
})

describe('workload page', () => {
  it('sorts by a column and marks inactive couriers', async () => {
    serve(
      [],
      [
        courier({ name: 'Amal', activeAssignments: 1 }),
        courier({ courierId: 'c-2', name: 'Bimal', activeAssignments: 5, active: false }),
      ],
    )
    renderApp('/admin/workload')
    const rowNames = () => screen.getAllByRole('rowheader').map((r) => r.textContent)
    await screen.findByText('Amal')
    expect(rowNames()[0]).toContain('Bimal') // default: busiest first
    expect(screen.getByText('(inactive)')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Courier' }))
    await userEvent.click(screen.getByRole('button', { name: 'Courier' }))
    expect(rowNames()[0]).toContain('Amal')
    expect(screen.getAllByRole('meter').length).toBe(2)
  })
})
