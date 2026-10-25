import { QueryClient } from '@tanstack/react-query'
import { isApiError } from './ApiError'

export const makeQueryClient = () =>
  new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        // never retry client errors; allow one retry for network/5xx
        retry: (count, err) =>
          count < 1 && !(isApiError(err) && err.status >= 400 && err.status < 500),
      },
    },
  })
