import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import type { Assignment } from '@/api/types'
import { groupManifest } from '@/lib/manifest'
import { validateProof } from '@/lib/image'
import { allowedNext } from '@/lib/transitions'
import { server } from '@/test/server'
import { API, mockSession, problem, renderApp } from '@/test/utils'

const addr = (city: string, phone = '+94771234567') => ({
  contactName: 'Nimal',
  phone,
  line1: '1 Main St',
  city,
  postalCode: '1',
})
const assignment = (over: Partial<Assignment> = {}): Assignment => ({
  id: 'a-1',
  parcelId: 'p-1',
  trackingNumber: 'SD-ABCD2345',
  status: 'CREATED',
  pickup: addr('Colombo'),
  dropoff: addr('Kandy'),
  size: 'SMALL',
  weightKg: 1.5,
  assignedAt: '2026-10-09T03:00:00Z',
  ...over,
})
const serve = (list: Assignment[]) =>
  server.use(http.get(`${API}/api/v1/couriers/me/assignments`, () => HttpResponse.json(list)))

beforeEach(() => mockSession(['COURIER']))

describe('transitions', () => {
  it('matches the parcel state machine', () => {
    expect(allowedNext('CREATED')).toEqual(['PICKED_UP', 'CANCELLED'])
    expect(allowedNext('OUT_FOR_DELIVERY')).toEqual(['DELIVERED', 'FAILED'])
    expect(allowedNext('FAILED')).toEqual(['OUT_FOR_DELIVERY'])
    expect(allowedNext('DELIVERED')).toEqual([])
  })
})

describe('proof validation', () => {
  it('accepts JPEG/PNG up to 5 MB and rejects the rest', () => {
    expect(validateProof(new File(['x'], 'a.jpg', { type: 'image/jpeg' }))).toBeNull()
    expect(validateProof(new File(['x'], 'a.exe', { type: 'application/x-msdownload' }))).toMatch(
      /JPEG or PNG/,
    )
    expect(
      validateProof(
        new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'big.png', { type: 'image/png' }),
      ),
    ).toMatch(/5 MB/)
  })
})

describe('groupManifest', () => {
  it('groups by pickup city, then orders by drop-off city', () => {
    const g = groupManifest([
      assignment({ id: '1', pickup: addr('Kandy'), dropoff: addr('Galle') }),
      assignment({ id: '2', pickup: addr('Colombo'), dropoff: addr('Matara') }),
      assignment({ id: '3', pickup: addr('Colombo'), dropoff: addr('Galle') }),
    ])
    expect(g.map(([c]) => c)).toEqual(['Colombo', 'Kandy'])
    expect(g[0]![1].map((a) => a.id)).toEqual(['3', '2'])
  })
})

describe('courier today', () => {
  it('summarises the day and shows the right address per state', async () => {
    serve([
      assignment({ id: '1', status: 'CREATED' }),
      assignment({ id: '2', trackingNumber: 'SD-BBBB2222', status: 'OUT_FOR_DELIVERY' }),
      assignment({ id: '3', trackingNumber: 'SD-CCCC3333', status: 'DELIVERED' }),
    ])
    renderApp('/courier/today')
    expect(await screen.findByRole('status')).toHaveTextContent('2 to deliver · 1 done · 0 failed')
    expect(screen.getAllByText('Pick up from')).toHaveLength(1)
    expect(screen.getAllByText('Deliver to').length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: /\+94771234567/ })[0]).toHaveAttribute(
      'href',
      'tel:+94771234567',
    )
  })

  it('advances status with one tap, updating the card immediately', async () => {
    let body: unknown
    let status: Assignment['status'] = 'CREATED' // a real server reflects the change on the next read
    server.use(
      http.get(`${API}/api/v1/couriers/me/assignments`, () =>
        HttpResponse.json([assignment({ status })]),
      ),
      http.patch(`${API}/api/v1/parcels/p-1/status`, async ({ request }) => {
        body = await request.json()
        status = 'PICKED_UP'
        return HttpResponse.json({})
      }),
    )
    renderApp('/courier/today')
    await userEvent.click(await screen.findByRole('button', { name: 'Picked up' }))
    expect(await screen.findByRole('button', { name: 'In transit' })).toBeInTheDocument()
    expect(body).toMatchObject({ toStatus: 'PICKED_UP' })
  })

  it('rolls the card back and explains when the server says 409', async () => {
    serve([assignment()])
    server.use(
      http.patch(`${API}/api/v1/parcels/p-1/status`, () =>
        problem(409, 'INVALID_STATUS_TRANSITION'),
      ),
    )
    renderApp('/courier/today')
    await userEvent.click(await screen.findByRole('button', { name: 'Picked up' }))
    expect(
      await screen.findByText('This parcel was updated elsewhere. Refreshed.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Picked up' })).toBeInTheDocument()
  })

  it('uploads the proof first and sends its URL with DELIVERED', async () => {
    const order: string[] = []
    let patch: Record<string, unknown> = {}
    serve([assignment({ status: 'OUT_FOR_DELIVERY' })])
    server.use(
      http.post(
        `${API}/api/v1/parcels/p-1/proof`,
        () => (order.push('proof'), HttpResponse.json({ proofUrl: 'https://files/p.jpg' })),
      ),
      http.patch(`${API}/api/v1/parcels/p-1/status`, async ({ request }) => {
        order.push('status')
        patch = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({})
      }),
    )
    URL.createObjectURL = () => 'blob:preview'
    URL.revokeObjectURL = () => {}
    renderApp('/courier/today')
    await userEvent.click(await screen.findByRole('button', { name: 'Delivered' }))
    const sheet = await screen.findByRole('dialog')
    await userEvent.upload(
      within(sheet).getByLabelText(/Proof photo/),
      new File(['img'], 'door.jpg', { type: 'image/jpeg' }),
    )
    expect(await within(sheet).findByAltText('Proof preview')).toBeInTheDocument()
    await userEvent.click(within(sheet).getByRole('button', { name: 'Confirm delivered' }))
    await waitFor(() => expect(order).toEqual(['proof', 'status']))
    expect(patch).toMatchObject({ toStatus: 'DELIVERED', proofUrl: 'https://files/p.jpg' })
  })

  it('rejects a non-image in the proof sheet before any upload', async () => {
    serve([assignment({ status: 'OUT_FOR_DELIVERY' })])
    renderApp('/courier/today')
    await userEvent.click(await screen.findByRole('button', { name: 'Delivered' }))
    const sheet = await screen.findByRole('dialog')
    await userEvent.upload(
      within(sheet).getByLabelText(/Proof photo/),
      new File(['x'], 'run.exe', { type: 'application/x-msdownload' }),
      { applyAccept: false },
    )
    expect(await within(sheet).findByRole('alert')).toHaveTextContent('JPEG or PNG')
    expect(within(sheet).queryByAltText('Proof preview')).not.toBeInTheDocument()
  })

  it('requires a reason before marking a parcel failed', async () => {
    let patch: Record<string, unknown> | undefined
    serve([assignment({ status: 'OUT_FOR_DELIVERY' })])
    server.use(
      http.patch(`${API}/api/v1/parcels/p-1/status`, async ({ request }) => {
        patch = (await request.json()) as Record<string, unknown>
        return HttpResponse.json({})
      }),
    )
    renderApp('/courier/today')
    await userEvent.click(await screen.findByRole('button', { name: 'Failed' }))
    const sheet = await screen.findByRole('dialog')
    await userEvent.click(within(sheet).getByRole('button', { name: 'Mark failed' }))
    expect(within(sheet).getByRole('alert')).toHaveTextContent('A reason is required')
    expect(patch).toBeUndefined()

    await userEvent.click(within(sheet).getByLabelText('Wrong address'))
    await userEvent.click(within(sheet).getByRole('button', { name: 'Mark failed' }))
    await waitFor(() =>
      expect(patch).toMatchObject({ toStatus: 'FAILED', failureReason: 'Wrong address' }),
    )
  })

  it('offers re-attempt for a failed parcel', async () => {
    serve([assignment({ status: 'FAILED' })])
    renderApp('/courier/today')
    expect(await screen.findByRole('button', { name: 'Re-attempt' })).toBeInTheDocument()
  })

  it('shows an empty state when nothing is assigned', async () => {
    serve([])
    renderApp('/courier/today')
    expect(await screen.findByText('Nothing assigned yet')).toBeInTheDocument()
  })

  it('keeps a customer out of the courier area', async () => {
    mockSession(['CUSTOMER'])
    renderApp('/courier/today')
    expect(await screen.findByRole('heading', { name: /403/ })).toBeInTheDocument()
  })
})
