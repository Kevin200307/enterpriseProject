import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { ErrorBoundary } from '@/app/ErrorBoundary'
import { Providers } from '@/app/providers'
import { createRouter } from '@/app/router'
import { LoadingScreen } from '@/components/common/LoadingScreen'
import { env } from '@/config/env'
import { MINIMUM_LOADING_MS } from '@/hooks/useMinimumLoading'
import './index.css'

const root = createRoot(document.getElementById('root')!)
const bootStartedAt = performance.now()

root.render(<LoadingScreen />)

async function boot() {
  // `import.meta.env.PROD` is a build constant: the mock backend is never bundled into a
  // production build unless that build is the standalone demo, which asks for it by name.
  if (env.useMocks && (!import.meta.env.PROD || env.demo)) {
    const { worker } = await import('@/mocks/browser')
    await worker.start({ onUnhandledRequest: 'bypass' })
  }
  const remaining = MINIMUM_LOADING_MS - (performance.now() - bootStartedAt)
  if (remaining > 0) await new Promise((resolve) => window.setTimeout(resolve, remaining))
  root.render(
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
