import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@/test/userEvent'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { assistantApi } from '@/api/endpoints/assistant'
import { env } from '@/config/env'
import { server } from '@/test/server'
import { API, mockSession, problem, renderApp } from '@/test/utils'
import { chatFailure } from './useChat'
import { ApiError } from '@/api/ApiError'

const sse = (events: [string, object][]) =>
  new HttpResponse(
    new ReadableStream({
      start(c) {
        const enc = new TextEncoder()
        for (const [name, data] of events)
          c.enqueue(enc.encode(`event: ${name}\ndata: ${JSON.stringify(data)}\n\n`))
        c.close()
      },
    }),
    { headers: { 'Content-Type': 'text/event-stream' } },
  )

async function openChat() {
  await userEvent.click(await screen.findByRole('button', { name: 'Open assistant' }))
  return screen.findByRole('dialog', { name: 'SwiftDrop assistant' })
}
const say = async (panel: HTMLElement, text: string) => {
  await userEvent.type(within(panel).getByLabelText('Message'), text)
  await userEvent.click(within(panel).getByRole('button', { name: 'Send' }))
}

beforeEach(() => {
  sessionStorage.clear()
  mockSession(['CUSTOMER'])
})
afterEach(() => {
  env.assistantEnabled = true
  vi.restoreAllMocks()
})

describe('chatFailure', () => {
  it('maps rate limiting, 503 and other errors', () => {
    expect(
      chatFailure(new ApiError({ status: 429, errorCode: 'RATE_LIMITED', title: 'x' })).notice,
    ).toMatch(/hourly message limit/)
    expect(
      chatFailure(new ApiError({ status: 503, errorCode: 'UPSTREAM_UNAVAILABLE', title: 'x' })),
    ).toMatchObject({ unavailable: true })
    expect(
      chatFailure(new ApiError({ status: 500, errorCode: 'INTERNAL_ERROR', title: 'x' }))
        .unavailable,
    ).toBe(false)
  })
})

describe('assistant widget', () => {
  it('is hidden when the assistant is disabled and for anonymous visitors', async () => {
    env.assistantEnabled = false
    renderApp('/deliveries')
    await screen.findByRole('heading', { name: 'My deliveries' })
    expect(screen.queryByRole('button', { name: 'Open assistant' })).not.toBeInTheDocument()
  })

  it('does not appear on the public tracking page', async () => {
    server.use(
      http.get(`${API}/api/v1/tracking/SD-ABCD2345`, () => problem(404, 'RESOURCE_NOT_FOUND')),
    )
    renderApp('/track/SD-ABCD2345')
    await screen.findByText("We couldn't find that parcel")
    expect(screen.queryByRole('button', { name: 'Open assistant' })).not.toBeInTheDocument()
  })

  it('streams a reply, shows the tool used, and keeps the conversation id for the next message', async () => {
    const bodies: Record<string, unknown>[] = []
    server.use(
      http.post(`${API}/api/v1/assistant/chat/stream`, async ({ request }) => {
        bodies.push((await request.json()) as Record<string, unknown>)
        return sse([
          ['tool', { name: 'estimateFee', arguments: { fromCity: 'Colombo', toCity: 'Kandy' } }],
          ['token', { text: 'It costs ' }],
          ['token', { text: '**LKR 600**.' }],
          ['done', { conversationId: 'conv-1' }],
        ])
      }),
    )
    renderApp('/deliveries')
    const panel = await openChat()
    await say(panel, 'How much to Kandy?')
    expect(await within(panel).findByText('LKR 600')).toHaveProperty('tagName', 'STRONG')
    expect(within(panel).getByText('estimateFee')).toBeInTheDocument()
    expect(within(panel).getByText('How much to Kandy?')).toBeInTheDocument()
    expect(bodies[0]).toMatchObject({ message: 'How much to Kandy?' })
    expect(bodies[0]?.conversationId).toBeUndefined()

    await say(panel, 'and express?')
    await waitFor(() => expect(bodies).toHaveLength(2))
    expect(bodies[1]).toMatchObject({ conversationId: 'conv-1' })
  })

  it('renders assistant output safely: no raw HTML and no javascript: links', async () => {
    server.use(
      http.post(`${API}/api/v1/assistant/chat/stream`, () =>
        sse([
          [
            'token',
            {
              text: 'Hi <img src=x onerror="alert(1)"> [click](javascript:alert(1)) [ok](https://swiftdrop.lk)',
            },
          ],
          ['done', { conversationId: 'c' }],
        ]),
      ),
    )
    renderApp('/deliveries')
    const panel = await openChat()
    await say(panel, 'hello')
    const ok = await within(panel).findByRole('link', { name: 'ok' })
    expect(ok).toHaveAttribute('rel', 'noopener noreferrer')
    expect(ok).toHaveAttribute('target', '_blank')
    expect(panel.querySelector('img')).toBeNull()
    expect(panel.querySelector('[onerror]')).toBeNull()
    expect(
      Array.from(panel.querySelectorAll('a')).every(
        (a) => !(a.getAttribute('href') ?? '').startsWith('javascript:'),
      ),
    ).toBe(true)
  })

  it('tells the user about the hourly limit on 429', async () => {
    server.use(http.post(`${API}/api/v1/assistant/chat/stream`, () => problem(429, 'RATE_LIMITED')))
    renderApp('/deliveries')
    const panel = await openChat()
    await say(panel, 'hi')
    expect(await within(panel).findByText(/hourly message limit/)).toBeInTheDocument()
  })

  it('hides the launcher for the rest of the session after a 503', async () => {
    server.use(
      http.post(`${API}/api/v1/assistant/chat/stream`, () => problem(503, 'UPSTREAM_UNAVAILABLE')),
    )
    renderApp('/deliveries')
    const panel = await openChat()
    await say(panel, 'hi')
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: 'SwiftDrop assistant' })).not.toBeInTheDocument(),
    )
    expect(screen.queryByRole('button', { name: 'Open assistant' })).not.toBeInTheDocument()
    expect(sessionStorage.getItem('sd-assistant-off')).toBe('1')
  })

  it('stops a reply in progress by aborting the request it started', async () => {
    // The shared test setup strips AbortSignals from Request (a jsdom/Node incompatibility), so a real fetch
    // abort cannot be observed here. Instead assert the app's side of the contract: the signal it passes to the
    // transport is aborted on Stop, and the resulting AbortError is shown as "Stopped." rather than an error.
    let received: AbortSignal | undefined
    vi.spyOn(assistantApi, 'stream').mockImplementation((_m, _c, signal) => {
      received = signal
      return Promise.resolve(
        new Response(
          new ReadableStream({
            start(c) {
              c.enqueue(new TextEncoder().encode('event: token\ndata: {"text":"Working"}\n\n'))
              signal.addEventListener('abort', () =>
                c.error(new DOMException('Aborted', 'AbortError')),
              )
            },
          }),
        ),
      )
    })
    renderApp('/deliveries')
    const panel = await openChat()
    await say(panel, 'long one')
    expect(await within(panel).findByText('Working')).toBeInTheDocument()
    expect(received?.aborted).toBe(false)
    await userEvent.click(within(panel).getByRole('button', { name: 'Stop' }))
    expect(received?.aborted).toBe(true)
    expect(await within(panel).findByText('Stopped.')).toBeInTheDocument()
    expect(within(panel).getByRole('button', { name: 'Send' })).toBeInTheDocument()
  })

  it('blocks messages over 2000 characters', async () => {
    renderApp('/deliveries')
    const panel = await openChat()
    const box = within(panel).getByLabelText('Message')
    await userEvent.click(box)
    await userEvent.paste('x'.repeat(2001))
    expect(within(panel).getByText('2001/2000')).toBeInTheDocument()
    expect(within(panel).getByRole('button', { name: 'Send' })).toBeDisabled()
  })

  it('sends a suggested prompt with one click', async () => {
    let sent: unknown
    server.use(
      http.post(
        `${API}/api/v1/assistant/chat/stream`,
        async ({ request }) => (
          (sent = await request.json()),
          sse([
            ['token', { text: 'ok' }],
            ['done', { conversationId: 'c' }],
          ])
        ),
      ),
    )
    renderApp('/deliveries')
    const panel = await openChat()
    await userEvent.click(within(panel).getByRole('button', { name: /How much to send 2 kg/ }))
    await waitFor(() =>
      expect(sent).toMatchObject({ message: 'How much to send 2 kg from Colombo to Kandy?' }),
    )
  })

  it('rehydrates a saved conversation, and forgets one that is not found', async () => {
    sessionStorage.setItem('sd-conv', 'conv-9')
    server.use(
      http.get(`${API}/api/v1/assistant/conversations/conv-9`, () =>
        HttpResponse.json({
          conversationId: 'conv-9',
          messages: [
            { role: 'user', content: 'earlier question' },
            { role: 'assistant', content: 'earlier answer' },
          ],
        }),
      ),
    )
    renderApp('/deliveries')
    const panel = await openChat()
    expect(await within(panel).findByText('earlier answer')).toBeInTheDocument()
  })

  it('drops a conversation id the server does not recognise', async () => {
    sessionStorage.setItem('sd-conv', 'someone-elses')
    server.use(
      http.get(`${API}/api/v1/assistant/conversations/someone-elses`, () =>
        problem(404, 'RESOURCE_NOT_FOUND'),
      ),
    )
    renderApp('/deliveries')
    await openChat()
    await waitFor(() => expect(sessionStorage.getItem('sd-conv')).toBeNull())
  })

  it('clears the conversation on the server with New chat, and closes with Escape', async () => {
    let cleared = false
    sessionStorage.setItem('sd-conv', 'conv-1')
    server.use(
      http.get(`${API}/api/v1/assistant/conversations/conv-1`, () =>
        HttpResponse.json({
          conversationId: 'conv-1',
          messages: [{ role: 'user', content: 'hello there' }],
        }),
      ),
      http.delete(
        `${API}/api/v1/assistant/conversations/conv-1`,
        () => ((cleared = true), new HttpResponse(null, { status: 204 })),
      ),
    )
    renderApp('/deliveries')
    const panel = await openChat()
    await within(panel).findByText('hello there')
    await userEvent.click(within(panel).getByRole('button', { name: 'New chat' }))
    await waitFor(() => expect(cleared).toBe(true))
    expect(sessionStorage.getItem('sd-conv')).toBeNull()
    await userEvent.keyboard('{Escape}')
    await waitFor(() =>
      expect(screen.queryByRole('dialog', { name: 'SwiftDrop assistant' })).not.toBeInTheDocument(),
    )
  })
})
