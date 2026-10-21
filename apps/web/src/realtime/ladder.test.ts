import { describe, expect, it, vi } from 'vitest'
import type { PublicTracking } from '@/api/types'
import { makeFailureCounter, runLadder, type Transport, type TransportHandlers } from './ladder'

const msg = (status = 'IN_TRANSIT'): PublicTracking => ({
  trackingNumber: 'SD-ABCD2345',
  status: status as PublicTracking['status'],
  originCity: 'Colombo',
  destinationCity: 'Kandy',
  updatedAt: '2026-10-09T10:00:00Z',
  timeline: [],
})

function fake(state: Transport['state']) {
  const handlers: { current?: TransportHandlers } = {}
  const stop = vi.fn()
  const transport: Transport = {
    state,
    start: (_tn, h) => {
      handlers.current = h
      return stop
    },
  }
  return { transport, handlers, stop }
}

describe('runLadder', () => {
  it('reports the rung that opened', () => {
    const ws = fake('live')
    const onState = vi.fn()
    runLadder('SD-ABCD2345', [ws.transport], { onMessage: vi.fn(), onState })
    ws.handlers.current!.onOpen()
    expect(onState).toHaveBeenLastCalledWith('live')
  })

  it('falls through websocket → sse → polling as each rung fails', () => {
    const [ws, sse, poll] = [fake('live'), fake('sse'), fake('polling')]
    const onState = vi.fn()
    runLadder('SD-ABCD2345', [ws.transport, sse.transport, poll.transport], {
      onMessage: vi.fn(),
      onState,
    })

    ws.handlers.current!.onFail()
    expect(ws.stop).toHaveBeenCalled()
    expect(sse.handlers.current).toBeDefined()
    sse.handlers.current!.onOpen()
    expect(onState).toHaveBeenLastCalledWith('sse')

    sse.handlers.current!.onFail()
    poll.handlers.current!.onOpen()
    expect(onState).toHaveBeenLastCalledWith('polling')
  })

  it('goes offline when every rung is exhausted, and when the last rung cannot reach the server', () => {
    const only = fake('polling')
    const onState = vi.fn()
    runLadder('SD-ABCD2345', [only.transport], { onMessage: vi.fn(), onState })
    only.handlers.current!.onOffline()
    expect(onState).toHaveBeenLastCalledWith('offline')
    only.handlers.current!.onFail()
    expect(onState).toHaveBeenLastCalledWith('offline')
  })

  it('ignores messages from a rung that already failed', () => {
    const [ws, sse] = [fake('live'), fake('sse')]
    const onMessage = vi.fn()
    runLadder('SD-ABCD2345', [ws.transport, sse.transport], { onMessage, onState: vi.fn() })
    const stale = ws.handlers.current!
    stale.onFail()
    stale.onMessage(msg())
    expect(onMessage).not.toHaveBeenCalled()
    sse.handlers.current!.onMessage(msg())
    expect(onMessage).toHaveBeenCalledTimes(1)
  })

  it('stops the active transport and ignores everything afterwards', () => {
    const ws = fake('live')
    const [onMessage, onState] = [vi.fn(), vi.fn()]
    const stop = runLadder('SD-ABCD2345', [ws.transport], { onMessage, onState })
    stop()
    expect(ws.stop).toHaveBeenCalled()
    ws.handlers.current!.onOpen()
    ws.handlers.current!.onMessage(msg())
    expect(onMessage).not.toHaveBeenCalled()
    expect(onState).not.toHaveBeenCalled()
  })
})

describe('makeFailureCounter', () => {
  it('trips after 3 failures inside the window, not when they are spread out', () => {
    let now = 0
    const failed = makeFailureCounter(3, 60_000, () => now)
    expect([failed(), ((now = 10_000), failed()), ((now = 20_000), failed())]).toEqual([
      false,
      false,
      true,
    ])

    now = 0
    const spread = makeFailureCounter(3, 60_000, () => now)
    expect([spread(), ((now = 40_000), spread()), ((now = 80_000), spread())]).toEqual([
      false,
      false,
      false,
    ])
  })
})
