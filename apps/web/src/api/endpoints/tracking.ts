import { api } from '../client'
import type { PublicTracking } from '../types'

export const trackingApi = {
  // Public endpoint: never trigger a token refresh / "session expired" for it.
  get: (tn: string, signal?: AbortSignal) =>
    api.get<PublicTracking>(`/api/v1/tracking/${encodeURIComponent(tn)}`, undefined, {
      skipAuthRetry: true,
      signal,
    }),
}
