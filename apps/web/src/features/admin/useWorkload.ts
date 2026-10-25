import { useQuery } from '@tanstack/react-query'
import { dispatchApi } from '@/api/endpoints/dispatch'
import { qk } from '@/api/queryKeys'

export function useWorkload() {
  return useQuery({
    queryKey: qk.dispatch.workload,
    queryFn: dispatchApi.workload,
    refetchInterval: 30_000,
  })
}
