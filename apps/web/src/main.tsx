import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { ErrorBoundary } from '@/app/ErrorBoundary'
import { Providers } from '@/app/providers'
import { createRouter } from '@/app/router'
import { env } from '@/config/env'
import './index.css'

async function boot() {
  // `import.meta.env.PROD` is a build constant: the mock backend is never bundled into a
  // production build unless that build is the standalone demo, which asks for it by name.
  if (env.useMocks && (!import.meta.env.PROD || env.demo)) {
    const { worker } = await import('@/mocks/browser')
    await worker.start({ onUnhandledRequest: 'bypass' })
  }
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <ErrorBoundary>
        <Providers>
          <RouterProvider router={createRouter()} />
        </Providers>
      </ErrorBoundary>
    </StrictMode>,
  )
}

void boot()
