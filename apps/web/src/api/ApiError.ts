export type ErrorCodeName =
  | 'VALIDATION_FAILED'
  | 'MALFORMED_REQUEST'
  | 'UNAUTHENTICATED'
  | 'INVALID_CREDENTIALS'
  | 'INVALID_API_KEY'
  | 'ACCESS_DENIED'
  | 'MERCHANT_NOT_APPROVED'
  | 'RESOURCE_NOT_FOUND'
  | 'CONFLICT'
  | 'INVALID_STATUS_TRANSITION'
  | 'RATE_LIMITED'
  | 'INTERNAL_ERROR'
  | 'UPSTREAM_UNAVAILABLE'
  | 'NETWORK_ERROR'

export interface FieldViolation {
  field: string
  message: string
}

interface ProblemBody {
  title?: string
  detail?: string
  status?: number
  errorCode?: string
  traceId?: string
  violations?: FieldViolation[]
}

/** Typed RFC 7807 problem as produced by every SwiftDrop service. */
export class ApiError extends Error {
  readonly status: number
  readonly errorCode: ErrorCodeName
  readonly title: string
  readonly detail: string
  readonly violations: FieldViolation[]
  readonly traceId?: string
  readonly retryAfter?: number

  constructor(init: {
    status: number
    errorCode: ErrorCodeName
    title: string
    detail?: string
    violations?: FieldViolation[]
    traceId?: string
    retryAfter?: number
  }) {
    super(init.detail || init.title)
    this.name = 'ApiError'
    this.status = init.status
    this.errorCode = init.errorCode
    this.title = init.title
    this.detail = init.detail ?? init.title
    this.violations = init.violations ?? []
    this.traceId = init.traceId
    this.retryAfter = init.retryAfter
  }

  static async fromResponse(res: Response): Promise<ApiError> {
    const retry = Number(res.headers.get('Retry-After'))
    const retryAfter = Number.isFinite(retry) && retry > 0 ? retry : undefined
    let body: ProblemBody = {}
    try {
      body = (await res.json()) as ProblemBody
    } catch {
      // not JSON — fall through to the generic error below
    }
    return new ApiError({
      status: res.status,
      errorCode:
        (body.errorCode as ErrorCodeName | undefined) ??
        (res.status >= 500 ? 'INTERNAL_ERROR' : 'MALFORMED_REQUEST'),
      title: body.title ?? res.statusText ?? 'Request failed',
      detail: body.detail,
      violations: body.violations,
      traceId: body.traceId ?? res.headers.get('X-Request-Id') ?? undefined,
      retryAfter,
    })
  }

  static network(): ApiError {
    return new ApiError({
      status: 0,
      errorCode: 'NETWORK_ERROR',
      title: 'Network error',
      detail: 'Could not reach SwiftDrop. Check your connection and try again.',
    })
  }
}

export const isApiError = (e: unknown): e is ApiError => e instanceof ApiError
