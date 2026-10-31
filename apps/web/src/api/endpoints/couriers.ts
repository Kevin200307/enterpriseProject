import { api } from '../client'
import type { Assignment, Courier, CourierInput, Page } from '../types'

export const couriersApi = {
  list: (f: { zone?: string; vehicle?: string; active?: string; page?: number }) =>
    api.get<Page<Courier>>('/api/v1/couriers', {
      zone: f.zone,
      vehicle: f.vehicle,
      active: f.active,
      page: f.page ?? 0,
      size: 20,
    }),
  create: (c: CourierInput) => api.post<Courier>('/api/v1/couriers', c),
  update: (id: string, c: Partial<CourierInput>) => api.patch<Courier>(`/api/v1/couriers/${id}`, c),
  myAssignments: () => api.get<Assignment[]>('/api/v1/couriers/me/assignments'),
  /** Dispatcher/admin: today's (or a specific date's) assignments for a specific courier. */
  manifest: (courierId: string, date: string) =>
    api.get<Assignment[]>(`/api/v1/couriers/${encodeURIComponent(courierId)}/manifest`, { date }),
}
