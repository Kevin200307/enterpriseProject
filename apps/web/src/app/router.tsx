import { createBrowserRouter } from 'react-router-dom'
import { RequireAuth } from '@/auth/RequireAuth'
import { ProfilePage } from '@/features/account/ProfilePage'
import { NewDeliveryPage } from '@/features/customer/NewDeliveryPage'
import { Home } from '@/features/auth/Home'
import { LoginPage } from '@/features/auth/LoginPage'
import { OAuthCallbackPage } from '@/features/auth/OAuthCallbackPage'
import { SignupPage } from '@/features/auth/SignupPage'
import { ComingSoon, ForbiddenPage, NotFoundPage } from '@/features/system/pages'
import { AppShell, PublicShell } from './AppShell'
import { RouteError } from './RouteError'

const soon = (title: string, issue: string) => <ComingSoon title={title} issue={issue} />

export const routes = [
  {
    element: <AppShell />,
    errorElement: <RouteError />,
    children: [
      { index: true, element: <Home /> },
      { path: 'login', element: <LoginPage /> },
      { path: 'signup', element: <SignupPage /> },
      { path: 'oauth/callback', element: <OAuthCallbackPage /> },
      { path: 'track', element: soon('Track a parcel', 'FE-3') },
      { path: '403', element: <ForbiddenPage /> },
      {
        element: <RequireAuth />,
        children: [{ path: 'account', element: <ProfilePage /> }],
      },
      {
        element: <RequireAuth roles={['CUSTOMER']} />,
        children: [
          { path: 'deliveries', element: soon('My deliveries', 'FE-3') },
          { path: 'deliveries/new', element: <NewDeliveryPage /> },
          { path: 'deliveries/:id', element: soon('Delivery details', 'FE-3') },
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
    children: [{ path: 'track/:trackingNumber', element: soon('Live tracking', 'FE-3') }],
  },
]

export const createRouter = () => createBrowserRouter(routes)
