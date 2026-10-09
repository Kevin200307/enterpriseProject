import { useEffect, useState, type ReactNode } from 'react'
import { ThemeContext, type Theme } from './useTheme'

const read = (): Theme => {
  try {
    const saved = localStorage.getItem('sd-theme')
    if (saved === 'light' || saved === 'dark') return saved
  } catch {
    // storage unavailable — fall back to system preference
  }
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setTheme] = useState<Theme>(read)
  useEffect(() => {
    document.documentElement.classList.toggle('dark', theme === 'dark')
    try {
      localStorage.setItem('sd-theme', theme)
    } catch {
      // ignore
    }
  }, [theme])
  return (
    <ThemeContext.Provider
      value={{ theme, toggle: () => setTheme((t) => (t === 'dark' ? 'light' : 'dark')) }}
    >
      {children}
    </ThemeContext.Provider>
  )
}
