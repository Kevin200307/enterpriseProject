import { createBrowserRouter } from 'react-router-dom'
import { RequireAuth } from '@/auth/RequireAuth'
import { Home } from '@/features/auth/Home'
import { ComingSoon, ForbiddenPage, NotFoundPage } from '@/features/system/pages'
import { AppShell, PublicShell } from './AppShell'
import { AuthShell } from './AuthShell'
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

      {
        path: 'track',
        ...page(() => import('@/features/tracking/TrackLookupPage'), 'TrackLookupPage'),
      },
      { path: '403', element: <ForbiddenPage /> },
      {
        element: <RequireAuth />,
        children: [
          {
            path: 'account/notifications',
            ...page(
              () => import('@/features/notifications/NotificationsPage'),
              'NotificationsPage',
            ),
          },
          {
            path: 'merchant/register',
            ...page(
              () => import('@/features/merchant/MerchantRegisterPage'),
              'MerchantRegisterPage',
            ),
          },
          {
            path: 'merchant/pending',
            ...page(() => import('@/features/merchant/MerchantPendingPage'), 'MerchantPendingPage'),
          },
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
        children: [
          {
            path: 'merchant/orders',
            ...page(() => import('@/features/merchant/MerchantOrdersPage'), 'MerchantOrdersPage'),
          },
          {
            path: 'merchant/orders/new',
            ...page(
              () => import('@/features/merchant/MerchantNewOrderPage'),
              'MerchantNewOrderPage',
            ),
          },
          {
            path: 'merchant/api-keys',
            ...page(() => import('@/features/merchant/ApiKeysPage'), 'ApiKeysPage'),
          },
          {
            path: 'merchant/webhook',
            ...page(() => import('@/features/merchant/WebhookPage'), 'WebhookPage'),
          },
          {
            path: 'merchant/webhook/deliveries',
            ...page(
              () => import('@/features/merchant/WebhookDeliveriesPage'),
              'WebhookDeliveriesPage',
            ),
          },
          { path: 'merchant/*', element: soon('Merchant console', 'FE-4') },
        ],
      },
      {
        element: <RequireAuth roles={['COURIER']} />,
        children: [
          {
            path: 'courier/today',
            ...page(() => import('@/features/courier/CourierTodayPage'), 'CourierTodayPage'),
          },
        ],
      },
      {
        element: <RequireAuth roles={['ADMIN', 'DISPATCHER']} />,
        children: [
          {
            path: 'admin',
            ...page(() => import('@/features/admin/DashboardPage'), 'DashboardPage'),
          },
          {
            path: 'admin/dispatch',
            ...page(() => import('@/features/admin/DispatchBoardPage'), 'DispatchBoardPage'),
          },
          {
            path: 'admin/workload',
            ...page(() => import('@/features/admin/WorkloadPage'), 'WorkloadPage'),
          },
          {
            path: 'admin/couriers',
            ...page(() => import('@/features/admin/CouriersPage'), 'CouriersPage'),
          },
          {
            path: 'admin/parcels',
            ...page(() => import('@/features/admin/AdminParcelsPage'), 'AdminParcelsPage'),
          },
          { path: 'admin/*', element: soon('Admin', 'FE-4') },
        ],
      },
      {
        element: <RequireAuth roles={['ADMIN']} />,
        children: [
          {
            path: 'admin/notifications',
            ...page(() => import('@/features/admin/NotificationLogPage'), 'NotificationLogPage'),
          },
          {
            path: 'admin/reports',
            ...page(() => import('@/features/admin/ReportsPage'), 'ReportsPage'),
          },
          { path: 'admin/zones', ...page(() => import('@/features/admin/ZonesPage'), 'ZonesPage') },
          {
            path: 'admin/pricing',
            ...page(() => import('@/features/admin/PricingPage'), 'PricingPage'),
          },
          { path: 'admin/users', ...page(() => import('@/features/admin/UsersPage'), 'UsersPage') },
          {
            path: 'admin/merchants',
            ...page(() => import('@/features/admin/MerchantsPage'), 'MerchantsPage'),
          },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
  {
    path: 'ui',
    errorElement: <RouteError />,
    ...page(() => import('@/features/furniture/ShowcasePage'), 'ShowcasePage'),
  },
  {
    element: <AuthShell />,
    errorElement: <RouteError />,
    children: [
      { path: 'login', ...page(() => import('@/features/auth/LoginPage'), 'LoginPage') },
      { path: 'signup', ...page(() => import('@/features/auth/SignupPage'), 'SignupPage') },
      {
        path: 'oauth/callback',
        ...page(() => import('@/features/auth/OAuthCallbackPage'), 'OAuthCallbackPage'),
      },
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
