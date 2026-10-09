import { api } from '../client'
import type { MerchantApplication, MerchantOrder, MerchantProfile, Page } from '../types'

export const merchantApi = {
  /** 404 means "no application yet". */
  me: () => api.get<MerchantProfile>('/api/v1/merchants/me'),
  register: (a: MerchantApplication) => api.post<MerchantProfile>('/api/v1/merchants/register', a),
  orders: (f: { q?: string; page?: number }) =>
    api.get<Page<MerchantOrder>>('/api/v1/merchants/orders', {
      q: f.q,
      page: f.page ?? 0,
      size: 20,
    }),
}
