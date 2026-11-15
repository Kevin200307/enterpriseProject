import { trackingApi } from '@/api/endpoints/tracking'
import type { PublicTracking } from '@/api/types'
import { env } from '@/config/env'
import { makeFailureCounter, type Transport } from './ladder'

const parse = (raw: string): PublicTracking | null => {
  try {
    return JSON.parse(raw) as PublicTracking
  } catch {
    return null
  }
}

/** STOMP over native WebSocket (TRK-4); SSE and polling remain the compatibility fallbacks. */
export const stompTransport: Transport = {
  state: 'live',
  start(tn, h) {
    let client: { deactivate: () => Promise<void> | void } | undefined
    let cancelled = false
    const tooManyFailures = makeFailureCounter(3, 60_000)
    const fail = () => {
      if (!cancelled && tooManyFailures()) h.onFail()
    }

    void import('@stomp/stompjs')
      .then(({ Client }) => {
        if (cancelled) return
        const brokerUrl = new URL(env.wsUrl)
        if (brokerUrl.protocol === 'http:') brokerUrl.protocol = 'ws:'
        if (brokerUrl.protocol === 'https:') brokerUrl.protocol = 'wss:'
        const c = new Client({
          brokerURL: brokerUrl.toString(),
          reconnectDelay: 3000,
          heartbeatIncoming: 20000,
          heartbeatOutgoing: 20000,
          onConnect: () => {
            h.onOpen()
            c.subscribe(`/topic/parcels.${tn}`, (m) => {
              const t = parse(m.body)
              if (t) h.onMessage(t)
            })
          },
          onWebSocketClose: fail,
          onStompError: fail,
        })
        client = c
        c.activate()
      })
      .catch(() => h.onFail())

    return () => {
      cancelled = true
      void client?.deactivate()
    }
  },
}

/** Server-sent events fallback for clients that cannot hold a WebSocket. */
export const sseTransport: Transport = {
  state: 'sse',
  start(tn, h) {
    if (typeof EventSource === 'undefined') {
      queueMicrotask(h.onFail)
      return () => {}
    }
    const es = new EventSource(`${env.apiBaseUrl}/api/v1/tracking/public/${encodeURIComponent(tn)}/stream`)
    es.onopen = h.onOpen
    es.onmessage = (e) => {
      const t = parse(e.data as string)
      if (t) h.onMessage(t)
    }
    es.onerror = () => {
      es.close()
      h.onFail()
    }
    return () => es.close()
  },
}

export const POLL_INTERVAL_MS = 15_000

/** Last rung: never fails, only reports offline and keeps trying. */
export const pollingTransport: Transport = {
  state: 'polling',
  start(tn, h) {
    const ctrl = new AbortController()
    const tick = () =>
      trackingApi
        .get(tn, ctrl.signal)
        .then((t) => {
          h.onOpen()
          h.onMessage(t)
        })
        .catch((e: unknown) => {
          if (!(e instanceof DOMException && e.name === 'AbortError')) h.onOffline()
        })
    void tick()
    const id = setInterval(() => void tick(), POLL_INTERVAL_MS)
    return () => {
      clearInterval(id)
      ctrl.abort()
    }
  },
}

export const defaultTransports = (): Transport[] => [stompTransport, sseTransport, pollingTransport]
