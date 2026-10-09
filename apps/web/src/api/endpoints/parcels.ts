import { api, requestBlob } from '../client'
import type { CreateParcelInput, HistoryEntry, Page, Parcel } from '../types'

export interface ParcelFilters {
  status?: string[]
  from?: string
  to?: string
  page?: number
  size?: number
}

export const parcelsApi = {
  list: (f: ParcelFilters) =>
    api.get<Page<Parcel>>('/api/v1/parcels', {
      status: f.status,
      from: f.from,
      to: f.to,
      page: f.page ?? 0,
      size: f.size ?? 20,
      sort: 'createdAt,desc',
    }),
  get: (id: string) => api.get<Parcel>(`/api/v1/parcels/${id}`),
  history: (id: string) => api.get<HistoryEntry[]>(`/api/v1/parcels/${id}/history`),
  create: (input: CreateParcelInput) => api.post<Parcel>('/api/v1/parcels', input),
  cancel: (id: string) => api.post<Parcel>(`/api/v1/parcels/${id}/cancel`),
  receipt: (id: string) => requestBlob(`/api/v1/parcels/${id}/receipt`),
}
