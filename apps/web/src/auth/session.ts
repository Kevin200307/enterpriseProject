import { useAuth } from './authContext'
export type { Role } from './types'

/** Thin view over the auth context for layout components. */
export function useSession() {
  const { claims } = useAuth()
  return { roles: claims?.roles ?? [], email: claims?.email }
}
