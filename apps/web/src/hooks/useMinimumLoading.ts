import { useEffect, useState } from 'react'

export const MINIMUM_LOADING_MS = 1200

export function useMinimumLoading(active: boolean) {
  const [visible, setVisible] = useState(active)

  useEffect(() => {
    if (active) {
      setVisible(true)
      return
    }
    const id = window.setTimeout(() => setVisible(false), MINIMUM_LOADING_MS)
    return () => window.clearTimeout(id)
  }, [active])

  return visible
}
