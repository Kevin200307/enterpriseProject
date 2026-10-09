/**
 * Access token lives in module memory only — never localStorage/sessionStorage.
 * Concurrent 401s share one in-flight refresh promise (single-flight).
 */
type Listener = (token: string | null) => void

let accessToken: string | null = null
let inFlight: Promise<string | null> | null = null
let refresher: (() => Promise<string | null>) | null = null
const listeners = new Set<Listener>()

export const tokenStore = {
  get: () => accessToken,
  set(token: string | null) {
    accessToken = token
    listeners.forEach((l) => l(token))
  },
  clear() {
    this.set(null)
  },
  subscribe(l: Listener) {
    listeners.add(l)
    return () => listeners.delete(l)
  },
  /** The auth layer registers how to perform the refresh call. */
  setRefresher(fn: () => Promise<string | null>) {
    refresher = fn
  },
  refresh(): Promise<string | null> {
    if (!refresher) return Promise.resolve(null)
    inFlight ??= refresher()
      .then((t) => {
        this.set(t)
        return t
      })
      .catch(() => {
        this.set(null)
        return null
      })
      .finally(() => {
        inFlight = null
      })
    return inFlight
  },
}
