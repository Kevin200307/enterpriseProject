import { request } from '../client'
import { api } from '../client'
import type { QueueItem, Suggestion, WorkloadRow } from '../types'

export const dispatchApi = {
  queue: (zone?: string) => api.get<QueueItem[]>('/api/v1/dispatch/queue', { zone }),
  suggest: (parcelId: string) => api.get<Suggestion[]>('/api/v1/dispatch/suggest', { parcelId }),
  workload: () => api.get<WorkloadRow[]>('/api/v1/dispatch/workload'),
  /** Idempotent for the same courier; a different courier is a 409 unless `reassign` is set. */
  assign: (parcelId: string, courierId: string, reassign = false) =>
    request<void>('/api/v1/dispatch/assignments', {
      method: 'POST',
      query: reassign ? { reassign: true } : undefined,
      body: { parcelId, courierId },
    }),
}
