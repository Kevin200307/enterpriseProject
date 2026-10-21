import { api } from '../client'
import type {
  Estimate,
  EstimateInput,
  PricingRule,
  PricingRuleInput,
  Zone,
  ZoneInput,
} from '../types'

export const pricingApi = {
  zones: (includeInactive = false) =>
    api.get<Zone[]>(
      '/api/v1/pricing/zones',
      includeInactive ? { includeInactive: true } : undefined,
    ),
  createZone: (z: ZoneInput) => api.post<Zone>('/api/v1/pricing/zones', z),
  updateZone: (id: string, z: ZoneInput) => api.put<Zone>(`/api/v1/pricing/zones/${id}`, z),
  deleteZone: (id: string) => api.delete<void>(`/api/v1/pricing/zones/${id}`),
  rules: (includeInactive = true) =>
    api.get<PricingRule[]>(
      '/api/v1/pricing/rules',
      includeInactive ? { includeInactive: true } : undefined,
    ),
  createRule: (r: PricingRuleInput) => api.post<PricingRule>('/api/v1/pricing/rules', r),
  updateRule: (id: string, r: PricingRuleInput) =>
    api.put<PricingRule>(`/api/v1/pricing/rules/${id}`, r),
  estimate: (input: EstimateInput, signal?: AbortSignal) =>
    api.post<Estimate>('/api/v1/pricing/estimate', input, { signal }),
}
