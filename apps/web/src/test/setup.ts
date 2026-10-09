import '@testing-library/jest-dom/vitest'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { server } from './server'

// jsdom ships its own AbortSignal, which Node's Request rejects. React Router builds a Request with a
// signal on every navigation, so drop it here — aborting in-flight requests is irrelevant in tests.
const NodeRequest = globalThis.Request
globalThis.Request = class extends NodeRequest {
  constructor(input: ConstructorParameters<typeof Request>[0], init?: RequestInit) {
    super(input, init ? { ...init, signal: undefined } : init)
  }
} as typeof Request

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))
afterEach(() => server.resetHandlers())
afterAll(() => server.close())
