import { api } from '../client'
import type { Assignment } from '../types'

export const couriersApi = {
  myAssignments: () => api.get<Assignment[]>('/api/v1/couriers/me/assignments'),
}
