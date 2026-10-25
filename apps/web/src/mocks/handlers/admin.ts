import { http, HttpResponse } from 'msw'
import type { NotificationPrefs } from '@/api/types'
import { defaultPrefs, merchants, myNotifications, notificationLog, prefs, users } from '../db'
import { base, body, empty, guard, paginate, param, problem } from '../util'
import { publicUser } from './auth'

const notFound = () => problem(404, 'RESOURCE_NOT_FOUND', 'Resource not found')

export const adminHandlers = [
  http.get(
    `${base}/api/v1/users`,
    guard(['ADMIN'], ({ request }) => {
      const url = new URL(request.url)
      const [role, status] = [url.searchParams.get('role'), url.searchParams.get('status')]
      const all = users
        .filter(
          (u) => (!role || u.roles.includes(role as never)) && (!status || u.status === status),
        )
        .map(publicUser)
      return HttpResponse.json(paginate(all, url))
    }),
  ),
  http.patch(
    `${base}/api/v1/users/:id/status`,
    guard(['ADMIN'], async ({ request, params }) => {
      const u = users.find((x) => x.id === param(params, 'id'))
      if (!u) return notFound()
      u.status = (await body<{ status: 'ACTIVE' | 'SUSPENDED' }>(request)).status
      return HttpResponse.json(publicUser(u))
    }),
  ),
  http.patch(
    `${base}/api/v1/users/:id/roles`,
    guard(['ADMIN'], async ({ request, params }, me) => {
      const u = users.find((x) => x.id === param(params, 'id'))
      if (!u) return notFound()
      const { roles } = await body<{ roles: string[] }>(request)
      if (u.id === me.sub && !roles.includes('ADMIN'))
        return problem(409, 'CONFLICT', 'You cannot remove your own admin access')
      u.roles = roles as typeof u.roles
      return HttpResponse.json(publicUser(u))
    }),
  ),

  http.get(
    `${base}/api/v1/merchants`,
    guard(['ADMIN'], ({ request }) => {
      const url = new URL(request.url)
      const status = url.searchParams.get('status')
      return HttpResponse.json(
        paginate(
          merchants.filter((m) => !status || m.status === status),
          url,
        ),
      )
    }),
  ),
  ...(['approve', 'reject', 'suspend'] as const).map((action) =>
    http.post(
      `${base}/api/v1/merchants/:id/${action}`,
      guard(['ADMIN'], ({ params }, me) => {
        const m = merchants.find((x) => x.id === param(params, 'id'))
        if (!m) return notFound()
        m.status =
          action === 'approve' ? 'APPROVED' : action === 'reject' ? 'REJECTED' : 'SUSPENDED'
        m.approvedBy = users.find((u) => u.id === me.sub)?.fullName
        m.approvedAt = new Date().toISOString()
        return HttpResponse.json(m)
      }),
    ),
  ),

  http.get(
    `${base}/api/v1/notifications/preferences`,
    guard(null, (_i, me) => HttpResponse.json(prefs.get(me.sub) ?? defaultPrefs())),
  ),
  http.put(
    `${base}/api/v1/notifications/preferences`,
    guard(null, async ({ request }, me) => {
      const p = await body<NotificationPrefs>(request)
      prefs.set(me.sub, p)
      return HttpResponse.json(p)
    }),
  ),
  http.get(
    `${base}/api/v1/notifications/me`,
    guard(null, ({ request }) =>
      HttpResponse.json(paginate(myNotifications, new URL(request.url))),
    ),
  ),
  http.get(
    `${base}/api/v1/notifications`,
    guard(['ADMIN'], ({ request }) => {
      const url = new URL(request.url)
      const [status, channel, from, to] = ['status', 'channel', 'from', 'to'].map((k) =>
        url.searchParams.get(k),
      )
      const all = notificationLog.filter(
        (n) =>
          (!status || n.status === status) &&
          (!channel || n.channel === channel) &&
          (!from || n.createdAt.slice(0, 10) >= from) &&
          (!to || n.createdAt.slice(0, 10) <= to),
      )
      return HttpResponse.json(paginate(all, url))
    }),
  ),
  http.post(
    `${base}/api/v1/notifications/:id/resend`,
    guard(['ADMIN'], ({ params }) => {
      const n = notificationLog.find((x) => x.id === param(params, 'id'))
      if (!n) return notFound()
      n.status = 'PENDING'
      return empty(202)
    }),
  ),
]
