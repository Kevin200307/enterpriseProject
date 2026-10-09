import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { ApiError } from '@/api/ApiError'
import { ErrorState } from '@/components/common/ErrorState'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/server'
import { API, mockSession, renderApp } from '@/test/utils'
import { navFor } from './nav'

describe('app shell', () => {
  it('resolves a deep link for a signed-in customer instead of 404ing', async () => {
    mockSession(['CUSTOMER'])
    server.use(http.get(`${API}/api/v1/pricing/zones`, () => HttpResponse.json([])))
    renderApp('/deliveries/new')
    expect(
      await screen.findByRole('heading', { name: 'New delivery', level: 1 }),
    ).toBeInTheDocument()
  })

  it('renders the public tracking page without app chrome', async () => {
    mockSession()
    renderApp('/track/SD-ABCD2345')
    expect(await screen.findByRole('heading', { name: 'Live tracking' })).toBeInTheDocument()
    expect(screen.queryByRole('navigation', { name: 'Main' })).not.toBeInTheDocument()
  })

  it('shows a not-found page for unknown routes', async () => {
    mockSession()
    renderApp('/nope')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
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
