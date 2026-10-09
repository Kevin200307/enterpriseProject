/** Every TanStack Query key comes from here so invalidation is exact and greppable. */
export const qk = {
  me: ['me'] as const,
  parcels: {
    all: ['parcels'] as const,
    list: (filters: object) => ['parcels', 'list', filters] as const,
    detail: (id: string) => ['parcels', 'detail', id] as const,
  },
  tracking: { public: (tn: string) => ['tracking', tn] as const },
  pricing: {
    zones: ['pricing', 'zones'] as const,
    estimate: (input: object) => ['pricing', 'estimate', input] as const,
  },
}
