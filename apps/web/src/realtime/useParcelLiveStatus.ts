import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { qk } from '@/api/queryKeys'
import type { PublicTracking } from '@/api/types'
import { runLadder, type ConnectionState } from './ladder'
import { defaultTransports } from './transports'

/** Never let an older push overwrite a newer cached state (events can arrive out of order). */
export const isNewer = (incoming: PublicTracking, cached?: PublicTracking) =>
  !cached || incoming.updatedAt >= cached.updatedAt

/**
 * Keeps a tracking number live: WebSocket → SSE → 15 s polling. By default every push is written straight
 * into the TanStack cache so components only ever read from the query. Pauses while the tab is hidden and
 * refetches immediately when it returns.
 */
export function useParcelLiveStatus(
  trackingNumber: string | undefined,
  opts: { enabled?: boolean; onMessage?: (t: PublicTracking) => void } = {},
): ConnectionState {
  const qc = useQueryClient()
  const [state, setState] = useState<ConnectionState>('offline')
  const handler = useRef(opts.onMessage)
  handler.current = opts.onMessage
  const enabled = (opts.enabled ?? true) && !!trackingNumber

  useEffect(() => {
    if (!enabled || !trackingNumber) return
    let stop: (() => void) | undefined

    const start = () => {
      stop = runLadder(trackingNumber, defaultTransports(), {
        onState: setState,
        onMessage: (t) => {
          if (handler.current) return handler.current(t)
          const key = qk.tracking.public(trackingNumber)
          if (isNewer(t, qc.getQueryData<PublicTracking>(key))) qc.setQueryData(key, t)
        },
      })
    }
    const onVisibility = () => {
      if (document.hidden) {
        stop?.()
        stop = undefined
      } else if (!stop) {
        void qc.invalidateQueries({ queryKey: qk.tracking.public(trackingNumber) })
        start()
      }
    }

    if (!document.hidden) start()
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      document.removeEventListener('visibilitychange', onVisibility)
      stop?.()
    }
  }, [enabled, trackingNumber, qc])

  return enabled ? state : 'offline'
}
