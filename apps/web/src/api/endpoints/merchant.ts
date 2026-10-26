import { api } from '../client'
import type {
  ApiKey,
  CreatedApiKey,
  MerchantApplication,
  WebhookDelivery,
  MerchantOrder,
  MerchantProfile,
  Page,
} from '../types'

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
  apiKeys: () => api.get<ApiKey[]>('/api/v1/merchants/me/api-keys'),
  createApiKey: (label: string, scopes: string[]) =>
    api.post<CreatedApiKey>('/api/v1/merchants/me/api-keys', { label, scopes }),
  revokeApiKey: (id: string) => api.delete<void>(`/api/v1/merchants/me/api-keys/${id}`),
  /** Saving generates a NEW signing secret, returned once. */
  setWebhook: (url: string) => api.put<{ secret: string }>('/api/v1/merchants/me/webhook', { url }),
  deliveries: (page: number) =>
    api.get<Page<WebhookDelivery>>('/api/v1/merchants/me/webhook/deliveries', { page, size: 20 }),
  replay: (id: string) => api.post<void>(`/api/v1/merchants/me/webhook/deliveries/${id}/replay`),
}
