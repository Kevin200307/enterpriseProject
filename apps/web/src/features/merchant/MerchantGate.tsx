import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { isApiError } from '@/api/ApiError'

/** Any merchant API answering MERCHANT_NOT_APPROVED sends the user to the application-status page. */
export function MerchantGate({ error, children }: { error: unknown; children: ReactNode }) {
  if (isApiError(error) && error.errorCode === 'MERCHANT_NOT_APPROVED')
    return <Navigate to="/merchant/pending" replace />
  return <>{children}</>
}
