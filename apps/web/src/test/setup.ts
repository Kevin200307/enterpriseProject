import '@testing-library/jest-dom/vitest'
import { configure } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, vi } from 'vitest'
import { server } from './server'

// jsdom ships its own AbortSignal, which Node's Request rejects. React Router builds a Request with a
// signal on every navigation, so drop it here — aborting in-flight requests is irrelevant in tests.
const NodeRequest = globalThis.Request
globalThis.Request = class extends NodeRequest {
  constructor(input: ConstructorParameters<typeof Request>[0], init?: RequestInit) {
    super(input, init ? { ...init, signal: undefined } : init)
  }
} as typeof Request

// jsdom does not implement matchMedia. Report "no match" so responsive hooks resolve to the desktop layout.
window.matchMedia ??= ((query: string) => ({
  matches: false,
  media: query,
  onchange: null,
  addEventListener: () => {},
  removeEventListener: () => {},
  addListener: () => {},
  removeListener: () => {},
  dispatchEvent: () => false,
})) as typeof window.matchMedia

// Lazy route chunks are transformed on first use and workers compete for CPU; the 1 s default is far too tight.
configure({ asyncUtilTimeout: 10_000 })

// No real sockets in unit tests. Files that exercise live updates mock this module themselves.
vi.mock('@/realtime/transports', () => ({ defaultTransports: () => [] }))

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
