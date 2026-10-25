import { api } from '../client'
import type {
  AdminUser,
  DashboardSummary,
  Merchant,
  MerchantStatus,
  Page,
  UserStatus,
} from '../types'

export const adminApi = {
  dashboard: () => api.get<DashboardSummary>('/api/v1/dashboard/summary'),
  users: (f: { role?: string; status?: string; page?: number }) =>
    api.get<Page<AdminUser>>('/api/v1/users', {
      role: f.role,
      status: f.status,
      page: f.page ?? 0,
      size: 20,
    }),
  setUserStatus: (id: string, status: UserStatus) =>
    api.patch<AdminUser>(`/api/v1/users/${id}/status`, { status }),
  setUserRoles: (id: string, roles: string[]) =>
    api.patch<AdminUser>(`/api/v1/users/${id}/roles`, { roles }),
  merchants: (f: { status?: MerchantStatus; page?: number }) =>
    api.get<Page<Merchant>>('/api/v1/merchants', { status: f.status, page: f.page ?? 0, size: 20 }),
  decideMerchant: (id: string, action: 'approve' | 'reject' | 'suspend', note?: string) =>
    api.post<Merchant>(`/api/v1/merchants/${id}/${action}`, note ? { note } : undefined),
}
