import { z } from 'zod'
import { phoneSchema } from '@/features/auth/schemas'

export const SIZES = [
  { value: 'SMALL', label: 'Small', hint: 'Envelope / box up to 2 kg' },
  { value: 'MEDIUM', label: 'Medium', hint: 'Shoebox to backpack' },
  { value: 'LARGE', label: 'Large', hint: 'Bulky or heavy items' },
] as const

const address = z.object({
  contactName: z.string().trim().min(1, 'Contact name is required').max(100),
  phone: phoneSchema,
  line1: z.string().trim().min(1, 'Address line is required').max(200),
  line2: z.string().trim().max(200).optional(),
  city: z.string().trim().min(1, 'City is required').max(80),
  postalCode: z.string().trim().min(1, 'Postal code is required').max(10),
})

export const WEIGHT_RE = /^\d+(\.\d{1,2})?$/

/** weightKg stays a string in the form (text input) and is converted on submit. */
export const bookingSchema = z.object({
  pickup: address,
  dropoff: address,
  size: z.enum(['SMALL', 'MEDIUM', 'LARGE']),
  weightKg: z
    .string()
    .refine(
      (v) => WEIGHT_RE.test(v.trim()) && Number(v) > 0,
      'Enter a weight above 0 (up to 2 decimals)',
    ),
  express: z.boolean(),
})

export type BookingForm = z.infer<typeof bookingSchema>

export const emptyAddress = {
  contactName: '',
  phone: '+94',
  line1: '',
  line2: '',
  city: '',
  postalCode: '',
}

export const bookingDefaults: BookingForm = {
  pickup: emptyAddress,
  dropoff: emptyAddress,
  size: 'SMALL',
  weightKg: '',
  express: false,
}

export const ADDRESS_FIELDS = ['contactName', 'phone', 'line1', 'line2', 'city', 'postalCode']
export const BOOKING_FIELDS = [
  ...['pickup', 'dropoff'].flatMap((p) => ADDRESS_FIELDS.map((f) => `${p}.${f}`)),
  'size',
  'weightKg',
  'express',
]
