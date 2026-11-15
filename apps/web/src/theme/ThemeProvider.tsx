import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'

const THEME_KEY = 'swiftdrop.theme'

export type ThemePreference = 'light' | 'dark' | 'system'

type ThemeContextValue = {
  preference: ThemePreference
  setPreference: (preference: ThemePreference) => void
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

function initialPreference(): ThemePreference {
  const saved = localStorage.getItem(THEME_KEY)
  if (saved === 'dark' || saved === 'light' || saved === 'system') return saved
  return 'light'
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [preference, setPreference] = useState<ThemePreference>(initialPreference)

  useEffect(() => {
    const isDark =
      preference === 'dark' ||
      (preference === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)
    document.documentElement.classList.toggle('dark', isDark)
    document.documentElement.style.colorScheme = isDark ? 'dark' : 'light'
    localStorage.setItem(THEME_KEY, preference)
  }, [preference])

  return (
    <ThemeContext.Provider value={{ preference, setPreference }}>
      {children}
    </ThemeContext.Provider>
  )
}

export function useTheme() {
  const value = useContext(ThemeContext)
  if (!value) throw new Error('useTheme must be used inside ThemeProvider')
  return value
}
