import { addressSchema, type AddressInput, emptyAddress } from './bookingSchema'

const STORAGE_KEY = 'swiftdrop.saved-delivery-details'

function keyFor(email: string) {
  return `${STORAGE_KEY}:${email.toLowerCase()}`
}

export function readSavedDetails(email: string): AddressInput {
  try {
    const raw = localStorage.getItem(keyFor(email))
    if (!raw) return { ...emptyAddress }
    const parsed: unknown = JSON.parse(raw)
    const result = addressSchema.safeParse(parsed)
    return result.success ? result.data : { ...emptyAddress }
  } catch (error) {
    if (error instanceof SyntaxError) return { ...emptyAddress }
    throw error
  }
}

export function saveSavedDetails(email: string, details: AddressInput) {
  localStorage.setItem(keyFor(email), JSON.stringify(details))
}
