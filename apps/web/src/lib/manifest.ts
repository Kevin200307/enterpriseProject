import type { Assignment } from '@/api/types'

/** Manifest order: group by pickup city, then drop-off city (DSP-3). */
export function groupManifest(list: Assignment[]) {
  const sorted = [...list].sort(
    (x, y) =>
      x.pickup.city.localeCompare(y.pickup.city) || x.dropoff.city.localeCompare(y.dropoff.city),
  )
  const groups = new Map<string, Assignment[]>()
  for (const a of sorted) groups.set(a.pickup.city, [...(groups.get(a.pickup.city) ?? []), a])
  return [...groups.entries()]
}
