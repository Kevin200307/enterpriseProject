import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@/test/userEvent'
import { http, HttpResponse } from 'msw'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import type { ApiKey, WebhookDelivery } from '@/api/types'
import { server } from '@/test/server'
import { API, mockSession, problem, renderApp } from '@/test/utils'
import { ApiKeysPage } from './ApiKeysPage'
import { validateWebhookUrl } from './snippets'

const page = <T,>(content: T[]) => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  number: 0,
  size: 20,
})
const SECRET = 'sk_live_9f8e7d6c5b4a39281706f5e4d3c2b1a0'
const key = (over: Partial<ApiKey> = {}): ApiKey => ({
  id: 'k-1',
  prefix: 'sk_live_9f8e7d6c',
  label: 'Production',
  scopes: ['orders:write'],
  lastUsedAt: null,
  expiresAt: null,
  ...over,
})

beforeEach(() => mockSession(['MERCHANT']))

describe('API keys', () => {
  it('lists keys by prefix only, never the full key', async () => {
    server.use(http.get(`${API}/api/v1/merchants/me/api-keys`, () => HttpResponse.json([key()])))
    renderApp('/merchant/api-keys')
    expect(await screen.findByText('Production')).toBeInTheDocument()
    expect(screen.getByText(/sk_live_9f8e7d6c…/)).toBeInTheDocument()
    expect(screen.getByText(/Never used/)).toBeInTheDocument()
    expect(document.body.textContent).not.toContain(SECRET)
  })

  it('shows a new key once, blocks dismissal until acknowledged, then wipes it', async () => {
    let created = false
    let body: unknown
    server.use(
      http.get(`${API}/api/v1/merchants/me/api-keys`, () =>
        HttpResponse.json(created ? [key()] : []),
      ),
      http.post(
        `${API}/api/v1/merchants/me/api-keys`,
        async ({ request }) => (
          (created = true),
          (body = await request.json()),
          HttpResponse.json({ ...key(), key: SECRET }, { status: 201 })
        ),
      ),
    )
    renderApp('/merchant/api-keys')
    await userEvent.click(await screen.findByRole('button', { name: 'Generate key' }))
    const form = await screen.findByRole('dialog')
    await userEvent.type(within(form).getByLabelText('Label'), 'Production')
    await userEvent.click(within(form).getByRole('button', { name: 'Generate' }))

    const modal = await screen.findByRole('dialog')
    expect(within(modal).getByLabelText('API key')).toHaveTextContent(SECRET)
    expect(within(modal).getByRole('alert')).toHaveTextContent(
      'You will not see this API key again',
    )
    expect(body).toEqual({ label: 'Production', scopes: ['orders:write', 'orders:read'] })

    // Cannot be dismissed yet: Done disabled, Escape ignored.
    expect(within(modal).getByRole('button', { name: 'Done' })).toBeDisabled()
    await userEvent.keyboard('{Escape}')
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    await userEvent.click(within(modal).getByLabelText(/I have copied this API key/))
    await userEvent.click(within(modal).getByRole('button', { name: 'Done' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(document.body.textContent).not.toContain(SECRET)
    expect(await screen.findByText(/sk_live_9f8e7d6c…/)).toBeInTheDocument()
  })

  it('never leaves the key in the query cache or the mutation cache', async () => {
    const client = new QueryClient()
    server.use(
      http.get(`${API}/api/v1/merchants/me/api-keys`, () => HttpResponse.json([])),
      http.post(`${API}/api/v1/merchants/me/api-keys`, () =>
        HttpResponse.json({ ...key(), key: SECRET }, { status: 201 }),
      ),
    )
    render(
      <QueryClientProvider client={client}>
        <MemoryRouter>
          <ApiKeysPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )
    await userEvent.click(await screen.findByRole('button', { name: 'Generate key' }))
    await userEvent.type(within(await screen.findByRole('dialog')).getByLabelText('Label'), 'CI')
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: 'Generate' }),
    )
    await screen.findByLabelText('API key') // modal is open and showing the secret
    const dump = () =>
      JSON.stringify([
        client
          .getQueryCache()
          .getAll()
          .map((q) => q.state.data),
        client
          .getMutationCache()
          .getAll()
          .map((m) => m.state.data),
      ])
    expect(dump()).not.toContain(SECRET)
    expect(client.getMutationCache().getAll()).toHaveLength(0)
  })

  it('revokes after confirmation', async () => {
    let revoked = false
    server.use(
      http.get(`${API}/api/v1/merchants/me/api-keys`, () => HttpResponse.json([key()])),
      http.delete(
        `${API}/api/v1/merchants/me/api-keys/k-1`,
        () => ((revoked = true), new HttpResponse(null, { status: 204 })),
      ),
    )
    renderApp('/merchant/api-keys')
    await userEvent.click(await screen.findByRole('button', { name: 'Revoke Production' }))
    const dialog = await screen.findByRole('dialog')
    expect(revoked).toBe(false)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Revoke key' }))
    await waitFor(() => expect(revoked).toBe(true))
  })

  it('redirects an unapproved merchant to the status page', async () => {
    server.use(
      http.get(`${API}/api/v1/merchants/me/api-keys`, () => problem(403, 'MERCHANT_NOT_APPROVED')),
      http.get(`${API}/api/v1/merchants/me`, () =>
        HttpResponse.json({ id: 'm', orgName: 'X', status: 'SUSPENDED' }),
      ),
    )
    const router = renderApp('/merchant/api-keys')
    await waitFor(() => expect(router.state.location.pathname).toBe('/merchant/pending'))
  })
})

describe('webhook settings', () => {
  it.each([
    ['https://example.com/hook', undefined],
    ['http://example.com/hook', 'The URL must start with https://'],
    ['not a url', 'Enter a full URL'],
    ['ftp://example.com', 'The URL must start with https://'],
  ])('validates %s', (url, error) => {
    const r = validateWebhookUrl(url)
    if (error) expect(r).toContain(error)
    else expect(r).toBeUndefined()
  })

  it('rejects http:// without calling the API', async () => {
    let called = false
    server.use(
      http.get(`${API}/api/v1/merchants/me`, () =>
        HttpResponse.json({ id: 'm', orgName: 'X', status: 'APPROVED', webhookUrl: null }),
      ),
      http.put(
        `${API}/api/v1/merchants/me/webhook`,
        () => ((called = true), HttpResponse.json({ secret: 'x' })),
      ),
    )
    renderApp('/merchant/webhook')
    await userEvent.type(
      await screen.findByLabelText('Endpoint URL'),
      'http://insecure.example/hook',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Save webhook' }))
    expect(await screen.findByText('The URL must start with https://')).toBeInTheDocument()
    expect(called).toBe(false)
  })

  it('warns that saving rotates the secret, then shows the new secret once', async () => {
    let body: unknown
    server.use(
      http.get(`${API}/api/v1/merchants/me`, () =>
        HttpResponse.json({
          id: 'm',
          orgName: 'X',
          status: 'APPROVED',
          webhookUrl: 'https://old.example/hook',
        }),
      ),
      http.put(
        `${API}/api/v1/merchants/me/webhook`,
        async ({ request }) => (
          (body = await request.json()),
          HttpResponse.json({ secret: 'whsec_abc123' })
        ),
      ),
    )
    renderApp('/merchant/webhook')
    const input = await screen.findByLabelText('Endpoint URL')
    await waitFor(() => expect(input).toHaveValue('https://old.example/hook'))
    await userEvent.clear(input)
    await userEvent.type(input, 'https://new.example/hook')
    await userEvent.click(screen.getByRole('button', { name: 'Save webhook' }))
    const confirm = await screen.findByRole('dialog')
    expect(confirm).toHaveTextContent('NEW signing secret')
    expect(body).toBeUndefined()
    await userEvent.click(within(confirm).getByRole('button', { name: 'Save and show secret' }))
    expect(await screen.findByLabelText('signing secret')).toHaveTextContent('whsec_abc123')
    expect(body).toEqual({ url: 'https://new.example/hook' })
  })

  it('shows verification snippets per language', async () => {
    server.use(
      http.get(`${API}/api/v1/merchants/me`, () =>
        HttpResponse.json({ id: 'm', orgName: 'X', status: 'APPROVED' }),
      ),
    )
    renderApp('/merchant/webhook')
    expect(await screen.findByRole('tabpanel')).toHaveTextContent('timingSafeEqual')
    await userEvent.click(screen.getByRole('tab', { name: 'Python' }))
    expect(screen.getByRole('tabpanel')).toHaveTextContent('compare_digest')
  })
})

describe('webhook deliveries', () => {
  const delivery = (over: Partial<WebhookDelivery> = {}): WebhookDelivery => ({
    id: 'd-1',
    eventId: 'e-1',
    trackingNumber: 'SD-ABCD2345',
    url: 'https://x',
    attempts: 3,
    lastStatus: 500,
    state: 'PENDING',
    nextRetryAt: '2026-10-09T11:00:00Z',
    history: [
      { at: '2026-10-09T10:00:00Z', status: 500 },
      { at: '2026-10-09T10:00:30Z', error: 'timeout' },
    ],
    ...over,
  })

  it('shows state, attempts and expandable history', async () => {
    server.use(
      http.get(`${API}/api/v1/merchants/me/webhook/deliveries`, () =>
        HttpResponse.json(
          page([
            delivery(),
            delivery({
              id: 'd-2',
              trackingNumber: 'SD-ZZZZ2222',
              state: 'DEAD',
              nextRetryAt: null,
            }),
          ]),
        ),
      ),
    )
    renderApp('/merchant/webhook/deliveries')
    expect(await screen.findByText('SD-ABCD2345')).toBeInTheDocument()
    expect(screen.getByText('DEAD')).toBeInTheDocument()
    expect(screen.getByText(/3 attempts · last HTTP 500 · next retry/)).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Show attempts for SD-ABCD2345' }))
    const attempts = screen.getByRole('list', { name: 'Attempts' })
    expect(attempts).toHaveTextContent('HTTP 500')
    expect(attempts).toHaveTextContent('timeout')
  })

  it('replays only after confirmation', async () => {
    let replayed = false
    server.use(
      http.get(`${API}/api/v1/merchants/me/webhook/deliveries`, () =>
        HttpResponse.json(page([delivery({ state: 'DEAD' })])),
      ),
      http.post(
        `${API}/api/v1/merchants/me/webhook/deliveries/d-1/replay`,
        () => ((replayed = true), new HttpResponse(null, { status: 202 })),
      ),
    )
    renderApp('/merchant/webhook/deliveries')
    await userEvent.click(await screen.findByRole('button', { name: 'Replay SD-ABCD2345' }))
    const dialog = await screen.findByRole('dialog')
    expect(replayed).toBe(false)
    await userEvent.click(within(dialog).getByRole('button', { name: 'Replay' }))
    await waitFor(() => expect(replayed).toBe(true))
  })

  it('shows an empty state', async () => {
    server.use(
      http.get(`${API}/api/v1/merchants/me/webhook/deliveries`, () => HttpResponse.json(page([]))),
    )
    renderApp('/merchant/webhook/deliveries')
    expect(await screen.findByText('No deliveries yet')).toBeInTheDocument()
  })
})
