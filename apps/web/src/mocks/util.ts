import { delay, HttpResponse } from 'msw'
import { env } from '@/config/env'
import { decodeClaims } from '@/auth/jwt'
import type { Claims, Role } from '@/auth/types'
import type { Page } from '@/api/types'

export const base = env.apiBaseUrl

export const problem = (status: number, errorCode: string, detail: string, extra: object = {}) =>
  HttpResponse.json(
    {
      title: errorCode,
      status,
      detail,
      errorCode,
      traceId: crypto.randomUUID().slice(0, 8),
      ...extra,
    },
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )

export function paginate<T>(all: T[], url: URL): Page<T> {
  const size = Number(url.searchParams.get('size') ?? 20) || 20
  const number = Math.max(0, Number(url.searchParams.get('page') ?? 0) || 0)
  return {
    content: all.slice(number * size, (number + 1) * size),
    totalElements: all.length,
    totalPages: Math.max(1, Math.ceil(all.length / size)),
    number,
    size,
  }
}

export function caller(request: Request): Claims | null {
  const h = request.headers.get('Authorization')
  return decodeClaims(h?.startsWith('Bearer ') ? h.slice(7) : null)
}

type Params = Record<string, string | readonly string[] | undefined>
type Info = { request: Request; params: Params }

/** Wraps a resolver with a small latency, authentication and an optional role check, like the real gateway. */
export const guard =
  (roles: Role[] | null, fn: (info: Info, me: Claims) => Response | Promise<Response>) =>
  async (info: Info) => {
    await delay(120)
    const me = caller(info.request)
    if (!me) return problem(401, 'UNAUTHENTICATED', 'Authentication required')
    if (roles && !roles.some((r) => me.roles.includes(r)))
      return problem(403, 'ACCESS_DENIED', 'Access denied')
    return fn(info, me)
  }

export const open = (fn: (info: Info) => Response | Promise<Response>) => async (info: Info) => {
  await delay(120)
  return fn(info)
}

export const body = async <T extends object = Record<string, unknown>>(r: Request) =>
  (await r.json()) as T
export const param = (p: Params, k: string) => String(p[k] ?? '')
export const ago = (hours: number) => new Date(Date.now() - hours * 3_600_000).toISOString()
export const empty = (status = 204) => new HttpResponse(null, { status })
