import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { ErrorBoundary } from '@/app/ErrorBoundary'
import { Providers } from '@/app/providers'
import { createRouter } from '@/app/router'
import { LoadingScreen } from '@/components/common/LoadingScreen'
import { LOADING_THRESHOLD_MS } from '@/hooks/useMinimumLoading'
import { useEffect, useState } from 'react'
import './index.css'

const root = createRoot(document.getElementById('root')!)

function BootScreen() {
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    const id = window.setTimeout(() => setVisible(true), LOADING_THRESHOLD_MS)
    return () => window.clearTimeout(id)
  }, [])

  return visible ? <LoadingScreen /> : null
}

root.render(<BootScreen />)

async function boot() {
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
