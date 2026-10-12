import { useEffect, useRef, useState } from 'react'

export const LOADING_THRESHOLD_MS = 400

export function useMinimumLoading(active: boolean) {
  const [visible, setVisible] = useState(false)
  const visibleAt = useRef<number | null>(null)

  useEffect(() => {
    if (active) {
      const id = window.setTimeout(() => {
        visibleAt.current = Date.now()
        setVisible(true)
      }, LOADING_THRESHOLD_MS)
      return () => window.clearTimeout(id)
    }

    if (!visible) return

    const elapsed = visibleAt.current ? Date.now() - visibleAt.current : LOADING_THRESHOLD_MS
    const remaining = Math.max(LOADING_THRESHOLD_MS - elapsed, 0)
    const id = window.setTimeout(() => {
      visibleAt.current = null
      setVisible(false)
    }, remaining)
    return () => window.clearTimeout(id)
  }, [active])

  // Slow operations stay visible until they finish, and brief operations still get a
  // complete 400 ms animation once the loader has appeared.
  return visible
}
