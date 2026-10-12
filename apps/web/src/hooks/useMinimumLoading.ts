import { useEffect, useState } from 'react'

export const LOADING_THRESHOLD_MS = 400

export function useMinimumLoading(active: boolean) {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    if (active) {
      const id = window.setTimeout(() => setVisible(true), LOADING_THRESHOLD_MS)
      return () => window.clearTimeout(id)
    }
    setVisible(false)
  }, [active])

  // Once the threshold is crossed, keep the loader mounted until the real operation finishes.
  return visible
}
