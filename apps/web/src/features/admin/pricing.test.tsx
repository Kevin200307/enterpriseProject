import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@/test/userEvent'
import { http, HttpResponse } from 'msw'
import { beforeEach, describe, expect, it } from 'vitest'
import type { PricingRule, Zone } from '@/api/types'
import { server } from '@/test/server'
import { API, mockSession, problem, renderApp } from '@/test/utils'

const zone = (over: Partial<Zone> = {}): Zone => ({
  id: 'z-1',
  name: 'Colombo',
  code: 'CMB',
  cities: ['Colombo', 'Dehiwala'],
  active: true,
  ...over,
})
const rule = (over: Partial<PricingRule> = {}): PricingRule => ({
  id: 'r-1',
  zoneFromId: 'z-1',
  zoneToId: 'z-2',
  sizeBand: 'SMALL',
  baseFee: 450,
  perKg: 80,
  minFee: 300,
  maxWeightKg: 5,
  active: true,
  effectiveFrom: '2026-01-01',
  ...over,
})

beforeEach(() => mockSession(['ADMIN']))

describe('zones', () => {
  it('requests inactive zones only when asked', async () => {
    const seen: (string | null)[] = []
    server.use(
      http.get(
        `${API}/api/v1/pricing/zones`,
        ({ request }) => (
          seen.push(new URL(request.url).searchParams.get('includeInactive')),
          HttpResponse.json([zone()])
        ),
      ),
    )
    renderApp('/admin/zones')
    await screen.findByText('Dehiwala', { exact: false })
    await userEvent.click(screen.getByLabelText('Include inactive zones'))
    await waitFor(() => expect(seen).toContain('true'))
    expect(seen[0]).toBeNull()
  })

  it('creates a zone from tag-input cities and uppercases the code', async () => {
    let body: unknown
    server.use(
      http.get(`${API}/api/v1/pricing/zones`, () => HttpResponse.json([])),
      http.post(
        `${API}/api/v1/pricing/zones`,
        async ({ request }) => (
          (body = await request.json()),
          HttpResponse.json(zone(), { status: 201 })
        ),
      ),
    )
    renderApp('/admin/zones')
    await userEvent.click(await screen.findByRole('button', { name: 'New zone' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Name'), 'Kandy')
    await userEvent.type(within(dialog).getByLabelText('Code'), 'kdy')
    await userEvent.type(
      within(dialog).getByPlaceholderText(/Type a city/),
      'Kandy{Enter}Peradeniya,',
    )
    expect(within(dialog).getByRole('list', { name: 'Cities' })).toHaveTextContent('Peradeniya')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save zone' }))
    await waitFor(() =>
      expect(body).toEqual({
        name: 'Kandy',
        code: 'KDY',
        cities: ['Kandy', 'Peradeniya'],
        active: true,
      }),
    )
  })

  it('validates before sending', async () => {
    server.use(http.get(`${API}/api/v1/pricing/zones`, () => HttpResponse.json([])))
    renderApp('/admin/zones')
    await userEvent.click(await screen.findByRole('button', { name: 'New zone' }))
    await userEvent.click(
      within(await screen.findByRole('dialog')).getByRole('button', { name: 'Save zone' }),
    )
    expect(await screen.findByText('Name is required')).toBeInTheDocument()
    expect(screen.getByText('Add at least one city')).toBeInTheDocument()
  })

  it('explains a 409 on delete and offers to deactivate instead', async () => {
    let put: unknown
    server.use(
      http.get(`${API}/api/v1/pricing/zones`, () => HttpResponse.json([zone()])),
      http.delete(`${API}/api/v1/pricing/zones/z-1`, () => problem(409, 'CONFLICT')),
      http.put(
        `${API}/api/v1/pricing/zones/z-1`,
        async ({ request }) => (
          (put = await request.json()),
          HttpResponse.json(zone({ active: false }))
        ),
      ),
    )
    renderApp('/admin/zones')
    await userEvent.click(await screen.findByRole('button', { name: 'Delete Colombo' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Delete' }))
    expect(await within(dialog).findByText(/used by an active pricing rule/)).toBeInTheDocument()
    await userEvent.click(within(dialog).getByRole('button', { name: 'Deactivate instead' }))
    await waitFor(() => expect(put).toMatchObject({ code: 'CMB', active: false }))
  })

  it('is closed to dispatchers', async () => {
    mockSession(['DISPATCHER'])
    renderApp('/admin/zones')
    expect(await screen.findByRole('heading', { name: /403/ })).toBeInTheDocument()
  })
})

describe('pricing rules', () => {
  const serve = (rules: PricingRule[]) =>
    server.use(
      http.get(`${API}/api/v1/pricing/rules`, () => HttpResponse.json(rules)),
      http.get(`${API}/api/v1/pricing/zones`, () =>
        HttpResponse.json([zone(), zone({ id: 'z-2', name: 'Kandy', code: 'KDY' })]),
      ),
    )

  it('shows rules with zone names and filters by size band', async () => {
    serve([rule(), rule({ id: 'r-2', sizeBand: 'LARGE' })])
    renderApp('/admin/pricing')
    await screen.findAllByText('Colombo → Kandy')
    expect(screen.getAllByRole('row')).toHaveLength(3)
    await userEvent.selectOptions(screen.getByLabelText('Size band'), 'LARGE')
    expect(screen.getAllByRole('row')).toHaveLength(2)
  })

  it('rejects negative or malformed fees client-side', async () => {
    serve([])
    renderApp('/admin/pricing')
    await userEvent.click(await screen.findByRole('button', { name: 'New rule' }))
    const dialog = await screen.findByRole('dialog')
    await userEvent.type(within(dialog).getByLabelText('Base fee (LKR)'), '-5')
    await userEvent.type(within(dialog).getByLabelText('Per kg (LKR)'), '1.234')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save rule' }))
    expect(await within(dialog).findByText(/Base fee: a non-negative amount/)).toBeInTheDocument()
    expect(within(dialog).getByText(/Per kg: a non-negative amount/)).toBeInTheDocument()
    expect(within(dialog).getByText('Max weight must be above 0')).toBeInTheDocument()
  })

  it('edits a rule and sends numeric fees', async () => {
    let body: Record<string, unknown> = {}
    serve([rule()])
    server.use(
      http.put(
        `${API}/api/v1/pricing/rules/r-1`,
        async ({ request }) => (
          (body = (await request.json()) as Record<string, unknown>),
          HttpResponse.json(rule())
        ),
      ),
    )
    renderApp('/admin/pricing')
    await userEvent.click(
      await screen.findByRole('button', { name: /Edit rule Colombo to Kandy SMALL/ }),
    )
    const dialog = await screen.findByRole('dialog')
    const base = within(dialog).getByLabelText('Base fee (LKR)')
    await userEvent.clear(base)
    await userEvent.type(base, '500.50')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Save rule' }))
    await waitFor(() => expect(body.baseFee).toBe(500.5))
    expect(body).toMatchObject({
      zoneFromId: 'z-1',
      zoneToId: 'z-2',
      sizeBand: 'SMALL',
      perKg: 80,
      effectiveTo: null,
    })
  })
})
