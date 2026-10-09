import { api } from '../client'
import type { Estimate, EstimateInput, Zone } from '../types'

export const pricingApi = {
  zones: () => api.get<Zone[]>('/api/v1/pricing/zones'),
  estimate: (input: EstimateInput, signal?: AbortSignal) =>
    api.post<Estimate>('/api/v1/pricing/estimate', input, { signal }),
}
