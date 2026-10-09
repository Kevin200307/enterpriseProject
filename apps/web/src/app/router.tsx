import { createBrowserRouter } from 'react-router-dom'
import { RequireAuth } from '@/auth/RequireAuth'
import { Home } from '@/features/auth/Home'
import { ComingSoon, ForbiddenPage, NotFoundPage } from '@/features/system/pages'
import { AppShell, PublicShell } from './AppShell'
import { RouteError } from './RouteError'

/** Route-level code splitting: each persona/page ships in its own chunk, so /track stays tiny. */
const page = <T extends Record<string, React.ComponentType>>(
  load: () => Promise<T>,
  name: keyof T,
) => ({
  lazy: async () => ({ Component: (await load())[name] }),
})

const soon = (title: string, issue: string) => <ComingSoon title={title} issue={issue} />

export const routes = [
  {
    element: <AppShell />,
    errorElement: <RouteError />,
    children: [
      { index: true, element: <Home /> },
      { path: 'login', ...page(() => import('@/features/auth/LoginPage'), 'LoginPage') },
      { path: 'signup', ...page(() => import('@/features/auth/SignupPage'), 'SignupPage') },
      {
        path: 'oauth/callback',
        ...page(() => import('@/features/auth/OAuthCallbackPage'), 'OAuthCallbackPage'),
      },
      {
        path: 'track',
        ...page(() => import('@/features/tracking/TrackLookupPage'), 'TrackLookupPage'),
      },
      { path: '403', element: <ForbiddenPage /> },
      {
        element: <RequireAuth />,
        children: [
          {
            path: 'account',
            ...page(() => import('@/features/account/ProfilePage'), 'ProfilePage'),
          },
        ],
      },
      {
        element: <RequireAuth roles={['CUSTOMER']} />,
        children: [
          {
            path: 'deliveries',
            ...page(() => import('@/features/customer/DeliveriesPage'), 'DeliveriesPage'),
          },
          {
            path: 'deliveries/new',
            ...page(() => import('@/features/customer/NewDeliveryPage'), 'NewDeliveryPage'),
          },
          {
            path: 'deliveries/:id',
            ...page(() => import('@/features/customer/DeliveryDetailPage'), 'DeliveryDetailPage'),
          },
        ],
      },
      {
        element: <RequireAuth roles={['MERCHANT']} />,
        children: [{ path: 'merchant/*', element: soon('Merchant console', 'FE-4') }],
      },
      {
        element: <RequireAuth roles={['COURIER']} />,
        children: [{ path: 'courier/*', element: soon('Courier', 'FE-4') }],
      },
      {
        element: <RequireAuth roles={['ADMIN', 'DISPATCHER']} />,
        children: [{ path: 'admin/*', element: soon('Admin', 'FE-4') }],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
  {
    element: <PublicShell />,
    errorElement: <RouteError />,
    children: [
      {
        path: 'track/:trackingNumber',
        ...page(() => import('@/features/tracking/PublicTrackPage'), 'PublicTrackPage'),
      },
    ],
  },
]

export const createRouter = () => createBrowserRouter(routes)
