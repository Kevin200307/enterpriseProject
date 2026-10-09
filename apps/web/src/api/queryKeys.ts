/** Every TanStack Query key comes from here so invalidation is exact and greppable. */
export const qk = {
  me: ['me'] as const,
  parcels: {
    all: ['parcels'] as const,
    list: (filters: object) => ['parcels', 'list', filters] as const,
    detail: (id: string) => ['parcels', 'detail', id] as const,
  },
  dispatch: {
    queue: (zone?: string) => ['dispatch', 'queue', zone ?? ''] as const,
    suggest: (parcelId: string) => ['dispatch', 'suggest', parcelId] as const,
    workload: ['dispatch', 'workload'] as const,
    all: ['dispatch'] as const,
  },
  admin: {
    dashboard: ['admin', 'dashboard'] as const,
    users: (f: object) => ['admin', 'users', f] as const,
    merchants: (f: object) => ['admin', 'merchants', f] as const,
    all: ['admin'] as const,
  },
  courier: { today: ['courier', 'today'] as const },
  tracking: { public: (tn: string) => ['tracking', tn] as const },
  pricing: {
    zones: ['pricing', 'zones'] as const,
    estimate: (input: object) => ['pricing', 'estimate', input] as const,
  },
}
