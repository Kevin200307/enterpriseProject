import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { ApiError } from '@/api/ApiError'
import { ErrorState } from '@/components/common/ErrorState'
import { Providers } from './providers'
import { navFor } from './nav'
import { routes } from './router'

const renderAt = (path: string) =>
  render(
    <Providers>
      <RouterProvider router={createMemoryRouter(routes, { initialEntries: [path] })} />
    </Providers>,
  )

describe('app shell', () => {
  it('resolves a deep link instead of 404ing', () => {
    renderAt('/deliveries/new')
    expect(screen.getByRole('heading', { name: 'New delivery' })).toBeInTheDocument()
  })

  it('renders the public tracking page without app chrome', () => {
    renderAt('/track/SD-ABCD2345')
    expect(screen.queryByRole('navigation', { name: 'Main' })).not.toBeInTheDocument()
  })

  it('shows a not-found page for unknown routes', () => {
    renderAt('/nope')
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })
})

describe('navFor', () => {
  it('is the union of every role the user holds', () => {
    const labels = navFor(['CUSTOMER', 'COURIER']).map((i) => i.label)
    expect(labels).toEqual(expect.arrayContaining(['New delivery', 'Today', 'Track a parcel']))
    expect(labels).not.toContain('Users')
  })
})

describe('ErrorState', () => {
  it('renders an API error as readable text with the trace id', () => {
    const err = new ApiError({
      status: 503,
      errorCode: 'UPSTREAM_UNAVAILABLE',
      title: 'x',
      traceId: 'T-1',
    })
    render(<ErrorState error={err} />)
    expect(screen.getByRole('alert')).toHaveTextContent('Temporarily unavailable')
    expect(screen.getByText(/T-1/)).toBeInTheDocument()
  })
})
