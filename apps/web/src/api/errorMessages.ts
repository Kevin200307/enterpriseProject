import { isApiError, type ErrorCodeName } from './ApiError'

const MESSAGES: Partial<Record<ErrorCodeName, { title: string; message: string }>> = {
  INVALID_CREDENTIALS: { title: 'Sign-in failed', message: 'Email or password is incorrect.' },
  UNAUTHENTICATED: { title: 'Please sign in', message: 'Your session is no longer valid.' },
  ACCESS_DENIED: { title: 'Access denied', message: "You don't have permission to do that." },
  MERCHANT_NOT_APPROVED: {
    title: 'Merchant not approved',
    message: 'Your merchant account is awaiting approval.',
  },
  RESOURCE_NOT_FOUND: {
    title: 'Not found',
    message: "We couldn't find what you were looking for.",
  },
  RATE_LIMITED: { title: 'Too many requests', message: 'Please wait a moment and try again.' },
  UPSTREAM_UNAVAILABLE: {
    title: 'Temporarily unavailable',
    message: 'Part of SwiftDrop is temporarily unavailable. Please try again shortly.',
  },
  NETWORK_ERROR: {
    title: 'You appear to be offline',
    message: 'Could not reach SwiftDrop. Check your connection and try again.',
  },
}

/** Single mapping from any error to user-facing copy. Server `detail` wins when it is specific. */
export function describeError(error: unknown): { title: string; message: string } {
  if (isApiError(error)) {
    const base = MESSAGES[error.errorCode]
    const specific = [
      'VALIDATION_FAILED',
      'CONFLICT',
      'INVALID_STATUS_TRANSITION',
      'MALFORMED_REQUEST',
    ].includes(error.errorCode)
    return {
      title: base?.title ?? error.title,
      message: specific || !base ? error.detail : base.message,
    }
  }
  return {
    title: 'Something went wrong',
    message: 'An unexpected error occurred. Please try again.',
  }
}
