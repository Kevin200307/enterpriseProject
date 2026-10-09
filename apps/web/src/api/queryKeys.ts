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
    stats: (f: object) => ['admin', 'stats', f] as const,
    couriers: (f: object) => ['admin', 'couriers', f] as const,
    parcels: (f: object) => ['admin', 'parcels', f] as const,
    dashboard: ['admin', 'dashboard'] as const,
    users: (f: object) => ['admin', 'users', f] as const,
    merchants: (f: object) => ['admin', 'merchants', f] as const,
    all: ['admin'] as const,
  },
  merchant: {
    me: ['merchant', 'me'] as const,
    orders: (f: object) => ['merchant', 'orders', f] as const,
    keys: ['merchant', 'keys'] as const,
    deliveries: (f: object) => ['merchant', 'deliveries', f] as const,
    all: ['merchant'] as const,
  },
  notifications: {
    prefs: ['notifications', 'prefs'] as const,
    mine: (page: number) => ['notifications', 'mine', page] as const,
    log: (f: object) => ['notifications', 'log', f] as const,
    all: ['notifications'] as const,
  },
  courier: { today: ['courier', 'today'] as const },
  tracking: { public: (tn: string) => ['tracking', tn] as const },
  pricing: {
    zones: ['pricing', 'zones'] as const,
    zonesAdmin: ['pricing', 'zones', 'admin'] as const,
    rules: ['pricing', 'rules'] as const,
    all: ['pricing'] as const,
    estimate: (input: object) => ['pricing', 'estimate', input] as const,
  },
}
