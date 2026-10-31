import { api } from '../client'
import type { AdminNotification, MyNotification, NotificationPrefs, Page } from '../types'

export interface NotificationFilters {
  status?: string
  channel?: string
  from?: string
  to?: string
  page?: number
}

export const notificationsApi = {
  preferences: () => api.get<NotificationPrefs>('/api/v1/notifications/preferences'),
  savePreferences: (p: NotificationPrefs) =>
    api.put<NotificationPrefs>('/api/v1/notifications/preferences', p),
  mine: (page: number) =>
    api.get<Page<MyNotification>>('/api/v1/notifications/me', { page, size: 20 }),
  log: (f: NotificationFilters) =>
    api.get<Page<AdminNotification>>('/api/v1/notifications', {
      status: f.status,
      channel: f.channel,
      from: f.from,
      to: f.to,
      page: f.page ?? 0,
      size: 20,
    }),
  byTracking: (trackingNumber: string) =>
    api.get<AdminNotification[]>(`/api/v1/notifications/${encodeURIComponent(trackingNumber)}`),
  resend: (id: string) => api.post<AdminNotification>(`/api/v1/notifications/${id}/resend`),
}
