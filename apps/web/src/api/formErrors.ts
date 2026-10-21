import type { FieldPath, FieldValues, UseFormSetError } from 'react-hook-form'
import { isApiError } from './ApiError'

/**
 * Maps server `violations[]` onto form fields. Returns the messages for fields the form
 * doesn't have, so the caller can surface them in a summary instead of dropping them.
 */
export function applyViolations<T extends FieldValues>(
  err: unknown,
  setError: UseFormSetError<T>,
  knownFields: string[],
): string[] {
  if (!isApiError(err)) return []
  const unmatched: string[] = []
  for (const v of err.violations) {
    if (knownFields.includes(v.field))
      setError(v.field as FieldPath<T>, { type: 'server', message: v.message })
    else unmatched.push(`${v.field}: ${v.message}`)
  }
  return unmatched
}
