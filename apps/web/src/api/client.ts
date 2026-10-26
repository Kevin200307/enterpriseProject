import { env } from '@/config/env'
import { tokenStore } from '@/auth/tokenStore'
import { ApiError } from './ApiError'

type Query = Record<string, string | number | boolean | undefined | null | (string | number)[]>

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  query?: Query
  body?: unknown
  headers?: Record<string, string>
  signal?: AbortSignal
  /** Skip the 401 → refresh → retry behaviour (used by /auth/* calls). */
  skipAuthRetry?: boolean
}

let onSessionExpired: (() => void) | null = null
export const setSessionExpiredHandler = (fn: () => void) => {
  onSessionExpired = fn
}

function buildUrl(path: string, query?: Query) {
  const url = new URL(env.apiBaseUrl + path)
  for (const [k, v] of Object.entries(query ?? {})) {
    if (v === undefined || v === null || v === '') continue
    if (Array.isArray(v)) v.forEach((x) => url.searchParams.append(k, String(x)))
    else url.searchParams.set(k, String(v))
  }
  return url.toString()
}

async function send(path: string, opts: RequestOptions, retried: boolean): Promise<Response> {
  const headers: Record<string, string> = {
    Accept: 'application/json, application/problem+json',
    'X-Request-Id': crypto.randomUUID(),
    ...opts.headers,
  }
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`
  let body: BodyInit | undefined
  if (opts.body instanceof FormData) body = opts.body
  else if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(opts.body)
  }

  let res: Response
  try {
    res = await fetch(buildUrl(path, opts.query), {
      method: opts.method ?? 'GET',
      headers,
      body,
      credentials: 'include',
      signal: opts.signal,
    })
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') throw e
    throw ApiError.network()
  }

  if (res.status === 401 && !retried && !opts.skipAuthRetry) {
    // Captured before refresh: a failed refresh clears the token, and only a user who HAD a session can expire.
    const hadSession = tokenStore.get() !== null
    const fresh = await tokenStore.refresh()
    if (fresh) return send(path, opts, true)
    if (hadSession) onSessionExpired?.()
  }
  if (!res.ok) throw await ApiError.fromResponse(res)
  return res
}

export async function request<T>(path: string, opts: RequestOptions = {}): Promise<T> {
  const res = await send(path, opts, false)
  if (res.status === 204) return undefined as T
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

/** Same auth, refresh and error handling as `request`, but hands back the raw Response for streaming bodies. */
export async function requestRaw(path: string, opts: RequestOptions = {}): Promise<Response> {
  return send(path, opts, false)
}

export async function requestBlob(path: string, opts: RequestOptions = {}): Promise<Blob> {
  const res = await send(path, opts, false)
  return res.blob()
}

export const api = {
  get: <T>(path: string, query?: Query, o?: RequestOptions) => request<T>(path, { ...o, query }),
  post: <T>(path: string, body?: unknown, o?: RequestOptions) =>
    request<T>(path, { ...o, method: 'POST', body }),
  put: <T>(path: string, body?: unknown, o?: RequestOptions) =>
    request<T>(path, { ...o, method: 'PUT', body }),
  patch: <T>(path: string, body?: unknown, o?: RequestOptions) =>
    request<T>(path, { ...o, method: 'PATCH', body }),
  delete: <T>(path: string, o?: RequestOptions) => request<T>(path, { ...o, method: 'DELETE' }),
}
