import { adminHandlers } from './admin'
import { assistantHandlers } from './assistant'
import { authHandlers } from './auth'
import { merchantHandlers } from './merchant'
import { opsHandlers } from './ops'
import { parcelHandlers } from './parcels'

/** An in-browser stand-in for the whole backend, used only when VITE_USE_MOCKS=true (offline demo). */
export const handlers = [
  ...authHandlers,
  ...parcelHandlers,
  ...opsHandlers,
  ...adminHandlers,
  ...merchantHandlers,
  ...assistantHandlers,
]
