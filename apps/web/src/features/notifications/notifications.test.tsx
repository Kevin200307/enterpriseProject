import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@/test/userEvent'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import type { AdminNotification, MyNotification } from '@/api/types'
import { humanize } from '@/lib/format'
import { server } from '@/test/server'
import { API, mockSession, problem, renderApp } from '@/test/utils'

const page = <T,>(content: T[], totalPages = 1, number = 0) => ({
  content,
  totalElements: content.length,
  totalPages,
  number,
  size: 20,
})
const mine = (over: Partial<MyNotification> = {}): MyNotification => ({
  id: 'n-1',
  channel: 'EMAIL',
  templateKey: 'booking-confirmed',
  status: 'SENT',
  trackingNumber: 'SD-ABCD2345',
  createdAt: '2026-10-08T05:00:00Z',
  sentAt: '2026-10-08T05:00:05Z',
  ...over,
})
const adminRow = (over: Partial<AdminNotification> = {}): AdminNotification => ({
  id: 'a-1',
  eventId: 'e-1',
  channel: 'SMS',
  recipientRef: '3f2a9c1e-aaaa-bbbb-cccc-1234567890ab',
  templateKey: 'out-for-delivery',
  status: 'FAILED',
  attempts: 5,
  lastError: 'provider timeout',
  createdAt: '2026-10-08T05:00:00Z',
  ...over,
})

describe('humanize', () => {
  it('turns keys into readable labels', () => {
    expect(humanize('booking-confirmed')).toBe('Booking confirmed')
    expect(humanize('OUT_FOR_DELIVERY')).toBe('Out for delivery')
  })
})

describe('notification preferences', () => {
  beforeEach(() => mockSession(['CUSTOMER']))
  const serve = (prefs = { EMAIL: ['CREATED', 'DELIVERED'], SMS: ['DELIVERED'] }) =>
    server.use(
      http.get(`${API}/api/v1/notifications/preferences`, () => HttpResponse.json(prefs)),
      http.get(`${API}/api/v1/notifications/me`, () => HttpResponse.json(page([mine()]))),
    )

  /** The preference grid moved into a sheet behind the header button. */
  async function openPrefs() {
    await userEvent.click(await screen.findByRole('button', { name: /Preferences/ }))
  }

  it('shows the saved choices as a status × channel grid', async () => {
    serve()
    renderApp('/account/notifications')
    await openPrefs()
    expect(await screen.findByLabelText('Email when created')).toBeChecked()
    expect(screen.getByLabelText('Email when picked up')).not.toBeChecked()
    expect(screen.getByLabelText('SMS when delivered')).toBeChecked()
    expect(screen.getByLabelText('SMS when created')).not.toBeChecked()
  })

  it('only enables Save when something changed, and sends the full new set', async () => {
    let body: unknown
    serve()
    server.use(
      http.put(
        `${API}/api/v1/notifications/preferences`,
        async ({ request }) => (
          (body = await request.json()),
          HttpResponse.json(body as Record<string, unknown>)
        ),
      ),
    )
    renderApp('/account/notifications')
    await openPrefs()
    const save = await screen.findByRole('button', { name: 'Save preferences' })
    await screen.findByLabelText('Email when created')
    expect(save).toBeDisabled()
    await userEvent.click(screen.getByLabelText('SMS when out for delivery'))
    expect(save).toBeEnabled()
    await userEvent.click(save)
    await waitFor(() =>
      expect(body).toEqual({
        EMAIL: ['CREATED', 'DELIVERED'],
        SMS: ['DELIVERED', 'OUT_FOR_DELIVERY'],
      }),
    )
    expect(await screen.findByText('Preferences saved')).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Save preferences' })).toBeDisabled(),
    )
  })

  it('discards unsaved changes', async () => {
    serve()
    renderApp('/account/notifications')
    await openPrefs()
    const box = await screen.findByLabelText('Email when picked up')
    await userEvent.click(box)
    expect(box).toBeChecked()
    await userEvent.click(screen.getByRole('button', { name: 'Discard changes' }))
    expect(box).not.toBeChecked()
  })

  it('lists recent notifications with readable names and status', async () => {
    serve()
    renderApp('/account/notifications')
    const row = (await screen.findByText('Booking confirmed')).closest('li')!
    expect(row).toHaveTextContent('SD-ABCD2345')
    expect(row).toHaveTextContent('Email')
    expect(within(row).getByText('Sent')).toBeInTheDocument()
  })

  it('shows an empty history state', async () => {
    serve()
    server.use(http.get(`${API}/api/v1/notifications/me`, () => HttpResponse.json(page([]))))
    renderApp('/account/notifications')
    expect(await screen.findByText('Nothing sent yet')).toBeInTheDocument()
  })

  it('requires sign-in', async () => {
    mockSession()
    const router = renderApp('/account/notifications')
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
  })
})

describe('admin notification log', () => {
  beforeEach(() => mockSession(['ADMIN']))

  it('sends filters to the API and shows the recipient as a truncated user id', async () => {
    const seen: URL[] = []
    server.use(
      http.get(
        `${API}/api/v1/notifications`,
        ({ request }) => (seen.push(new URL(request.url)), HttpResponse.json(page([adminRow()]))),
      ),
    )
    renderApp('/admin/notifications?status=FAILED&channel=SMS&from=2026-10-01')
    const row = (await screen.findByText('Out for delivery')).closest('tr')!
    expect(seen[0]?.searchParams.get('status')).toBe('FAILED')
    expect(seen[0]?.searchParams.get('channel')).toBe('SMS')
    expect(seen[0]?.searchParams.get('from')).toBe('2026-10-01')
    expect(row).toHaveTextContent('3f2a9c1e…')
    expect(row.textContent).not.toContain('bbbb-cccc') // never the full reference
    expect(row).toHaveTextContent('provider timeout')
  })

  it('offers resend only for failed or skipped rows, after confirmation', async () => {
    let resent = ''
    server.use(
      http.get(`${API}/api/v1/notifications`, () =>
        HttpResponse.json(
          page([adminRow(), adminRow({ id: 'a-2', status: 'SENT', templateKey: 'delivered' })]),
        ),
      ),
      http.post(
        `${API}/api/v1/notifications/a-1/resend`,
        () => ((resent = 'a-1'), new HttpResponse(null, { status: 202 })),
      ),
    )
    renderApp('/admin/notifications')
    await screen.findByText('Delivered')
    expect(screen.getAllByRole('button', { name: /^Resend/ })).toHaveLength(1)
    await userEvent.click(screen.getByRole('button', { name: /^Resend/ }))
    const dialog = await screen.findByRole('dialog')
    expect(resent).toBe('')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Resend' }))
    await waitFor(() => expect(resent).toBe('a-1'))
  })

  it('surfaces a resend failure', async () => {
    server.use(
      http.get(`${API}/api/v1/notifications`, () => HttpResponse.json(page([adminRow()]))),
      http.post(`${API}/api/v1/notifications/a-1/resend`, () => problem(409, 'CONFLICT')),
    )
    renderApp('/admin/notifications')
    await userEvent.click(await screen.findByRole('button', { name: /^Resend/ }))
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Resend' }),
    )
    expect(await screen.findByText('CONFLICT detail')).toBeInTheDocument()
  })

  it('is admin-only', async () => {
    mockSession(['DISPATCHER'])
    renderApp('/admin/notifications')
    expect(await screen.findByRole('heading', { name: /403/ })).toBeInTheDocument()
  })
})
