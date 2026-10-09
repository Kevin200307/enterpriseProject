import { createBrowserRouter, Navigate } from 'react-router-dom'
import { ComingSoon, ForbiddenPage, NotFoundPage } from '@/features/system/pages'
import { AppShell, PublicShell } from './AppShell'
import { RouteError } from './RouteError'

const soon = (title: string, issue: string) => <ComingSoon title={title} issue={issue} />

export const routes = [
  {
    element: <AppShell />,
    errorElement: <RouteError />,
    children: [
      { index: true, element: <Navigate to="/deliveries" replace /> },
      { path: 'login', element: soon('Sign in', 'FE-2') },
      { path: 'signup', element: soon('Create account', 'FE-2') },
      { path: 'deliveries', element: soon('My deliveries', 'FE-3') },
      { path: 'deliveries/new', element: soon('New delivery', 'FE-3') },
      { path: 'deliveries/:id', element: soon('Delivery details', 'FE-3') },
      { path: 'track', element: soon('Track a parcel', 'FE-3') },
      { path: 'merchant/*', element: soon('Merchant console', 'FE-4') },
      { path: 'courier/*', element: soon('Courier', 'FE-4') },
      { path: 'admin/*', element: soon('Admin', 'FE-4') },
      { path: '403', element: <ForbiddenPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
  {
    element: <PublicShell />,
    errorElement: <RouteError />,
    children: [{ path: 'track/:trackingNumber', element: soon('Live tracking', 'FE-3') }],
  },
]

export const createRouter = () => createBrowserRouter(routes)
