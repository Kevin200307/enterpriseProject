import { request } from '../client'
import { api } from '../client'
import type { AssignmentCommand, DailyStat, QueueItem, Suggestion, WorkloadRow } from '../types'

export const dispatchApi = {
  queue: (zone?: string) => api.get<QueueItem[]>('/api/v1/dispatch/queue', { zone }),
  suggest: (parcelId: string) => api.get<Suggestion[]>('/api/v1/dispatch/suggest', { parcelId }),
  dailyStats: (from: string, to: string) =>
    api.get<DailyStat[]>('/api/v1/dispatch/stats/daily', { from, to }),
  workload: () => api.get<WorkloadRow[]>('/api/v1/dispatch/workload'),
  /** Idempotent for the same courier; a different courier is a 409 unless `reassign` is set.
   *  Returns a command when the operation is queued asynchronously (HTTP 202). */
  assign: (parcelId: string, courierId: string, reassign = false) =>
    request<AssignmentCommand | undefined>('/api/v1/dispatch/assignments', {
      method: 'POST',
      query: reassign ? { reassign: true } : undefined,
      body: { parcelId, courierId },
    }),
  /** Unassign an active assignment. Returns a command when queued asynchronously (HTTP 202). */
  unassign: (assignmentId: string, reason: string, requestId?: string) =>
    request<AssignmentCommand | undefined>(`/api/v1/dispatch/assignments/${encodeURIComponent(assignmentId)}`, {
      method: 'DELETE',
      headers: requestId ? { 'X-Request-ID': requestId } : undefined,
      body: { reason },
    }),
  /** Poll an async dispatch command until its state is DONE or FAILED. */
  getCommand: (commandId: string) =>
    api.get<AssignmentCommand>(`/api/v1/dispatch/commands/${encodeURIComponent(commandId)}`),
}
