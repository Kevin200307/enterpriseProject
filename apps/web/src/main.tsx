import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { ErrorBoundary } from '@/app/ErrorBoundary'
import { Providers } from '@/app/providers'
import { createRouter } from '@/app/router'
import { env } from '@/config/env'
import './index.css'

async function boot() {
  // `import.meta.env.PROD` is a build constant: the mock backend can never be bundled into a production build.
  if (!import.meta.env.PROD && env.useMocks) {
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
