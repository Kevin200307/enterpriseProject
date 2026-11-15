import type { PublicTracking } from '@/api/types'

/** What the UI shows: websocket push, SSE push, 15 s polling, or nothing reachable. */
export type ConnectionState = 'live' | 'sse' | 'polling' | 'offline'

export interface TransportHandlers {
  onMessage: (t: PublicTracking) => void
  /** The transport is delivering (or about to deliver) updates. */
  onOpen: () => void
  /** The transport gave up; the ladder should move to the next rung. */
  onFail: () => void
  /** Even the last rung cannot reach the server. */
  onOffline: () => void
}

export interface Transport {
  state: Exclude<ConnectionState, 'offline'>
  start: (trackingNumber: string, h: TransportHandlers) => () => void
}

/**
 * Runs transports in order (WebSocket → SSE → polling). When one fails the next takes over;
 * the last rung is expected to retry forever and report `onOffline` instead of failing.
 */
export function runLadder(
  trackingNumber: string,
  transports: Transport[],
  cb: { onMessage: (t: PublicTracking) => void; onState: (s: ConnectionState) => void },
): () => void {
  let index = 0
  let stopCurrent: (() => void) | undefined
  let stopped = false

  const startRung = () => {
    const transport = transports[index]
    if (stopped || !transport) return cb.onState('offline')
    let dead = false
    stopCurrent = transport.start(trackingNumber, {
      onMessage: (t) => !dead && !stopped && cb.onMessage(t),
      onOpen: () => !dead && !stopped && cb.onState(transport.state),
      onFail: () => {
        if (dead || stopped) return
        dead = true
        stopCurrent?.()
        index += 1
        startRung()
      },
      onOffline: () => !dead && !stopped && cb.onState('offline'),
    })
  }

  startRung()
  return () => {
    stopped = true
    stopCurrent?.()
  }
}

/** True once `limit` failures landed inside `windowMs` — used to decide a socket is not worth retrying. */
export function makeFailureCounter(limit: number, windowMs: number, now: () => number = Date.now) {
  const times: number[] = []
  return () => {
    const t = now()
    times.push(t)
    while (times.length && t - (times[0] ?? t) > windowMs) times.shift()
    return times.length >= limit
  }
}
