import { QueryClientProvider } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Toaster } from 'sonner'
import { makeQueryClient } from '@/api/queryClient'
import { AuthProvider } from '@/auth/AuthProvider'
import { ThemeProvider } from './theme'
import { useTheme } from './useTheme'

function ThemedToaster() {
  const { theme } = useTheme()
  return <Toaster theme={theme} richColors position="top-right" />
}

export function Providers({ children }: { children: ReactNode }) {
  const [client] = useState(makeQueryClient)
  return (
    <ThemeProvider>
      <QueryClientProvider client={client}>
        <AuthProvider>{children}</AuthProvider>
        <ThemedToaster />
      </QueryClientProvider>
    </ThemeProvider>
  )
}
