import { api, requestBlob } from '../client'
import type {
  CreateParcelInput,
  DeliveryProof,
  HistoryEntry,
  Page,
  Parcel,
  ParcelPage,
  ProofUrl,
  StatusChange,
} from '../types'

export interface ParcelFilters {
  status?: string[]
  from?: string
  to?: string
  page?: number
  size?: number
}

const normalizePage = <T,>({ page, ...result }: ParcelPage<T>): Page<T> => ({
  ...result,
  number: page,
})

export const parcelsApi = {
  list: async (f: ParcelFilters) =>
    normalizePage(
      await api.get<ParcelPage<Parcel>>('/api/v1/parcels', {
        status: f.status,
        from: f.from,
        to: f.to,
        page: f.page ?? 0,
        size: f.size ?? 20,
      }),
    ),
  byTracking: (tn: string) =>
    api.get<Parcel>(`/api/v1/parcels/by-tracking/${encodeURIComponent(tn)}/details`),
  get: (id: string) => api.get<Parcel>(`/api/v1/parcels/${id}`),
  history: async (id: string, page = 0) =>
    normalizePage(
      await api.get<ParcelPage<HistoryEntry>>(`/api/v1/parcels/${id}/history`, {
        page,
        size: 100,
      }),
    ),
  proofUrl: (id: string, proofId: string) =>
    api.get<ProofUrl>(`/api/v1/parcels/${id}/proofs/${proofId}/url`),
  create: (input: CreateParcelInput, idempotencyKey = crypto.randomUUID()) =>
    api.post<Parcel>('/api/v1/parcels', input, {
      headers: { 'Idempotency-Key': idempotencyKey },
    }),
  cancel: (id: string) => api.post<Parcel>(`/api/v1/parcels/${id}/cancel`),
  updateStatus: (id: string, change: StatusChange) =>
    api.patch<Parcel>(`/api/v1/parcels/${id}/status`, change),
  uploadProof: (id: string, file: File) => {
    const body = new FormData()
    body.append('file', file)
    return api.post<DeliveryProof>(`/api/v1/parcels/${id}/proofs`, body)
  },
  receipt: (id: string) =>
    requestBlob(`/api/v1/parcels/${id}/receipt`, { headers: { Accept: 'application/pdf' } }),
}
