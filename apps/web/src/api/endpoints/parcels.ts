import { api, requestBlob } from '../client'
import type { CreateParcelInput, HistoryEntry, Page, Parcel, StatusChange } from '../types'

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
  byTracking: (tn: string) =>
    api.get<Parcel>(`/api/v1/parcels/by-tracking/${encodeURIComponent(tn)}`),
  get: (id: string) => api.get<Parcel>(`/api/v1/parcels/${id}`),
  history: (id: string) => api.get<HistoryEntry[]>(`/api/v1/parcels/${id}/history`),
  create: (input: CreateParcelInput) => api.post<Parcel>('/api/v1/parcels', input),
  cancel: (id: string) => api.post<Parcel>(`/api/v1/parcels/${id}/cancel`),
  updateStatus: (id: string, change: StatusChange) =>
    api.patch<Parcel>(`/api/v1/parcels/${id}/status`, change),
  uploadProof: (id: string, file: File) => {
    const body = new FormData()
    body.append('file', file)
    return api.post<{ proofUrl: string }>(`/api/v1/parcels/${id}/proof`, body)
  },
  receipt: (id: string) => requestBlob(`/api/v1/parcels/${id}/receipt`),
}
