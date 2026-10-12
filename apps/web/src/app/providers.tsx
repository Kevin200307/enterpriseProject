import { QueryClientProvider } from '@tanstack/react-query'
import { useState, type ReactNode } from 'react'
import { Toaster } from 'sonner'
import { makeQueryClient } from '@/api/queryClient'
import { AuthProvider } from '@/auth/AuthProvider'
import { ThemeProvider } from '@/theme/ThemeProvider'

export function Providers({ children }: { children: ReactNode }) {
  const [client] = useState(makeQueryClient)
  return (
    <ThemeProvider>
      <QueryClientProvider client={client}>
        <AuthProvider>{children}</AuthProvider>
        <Toaster theme="system" richColors position="top-right" />
      </QueryClientProvider>
    </ThemeProvider>
  )
}
