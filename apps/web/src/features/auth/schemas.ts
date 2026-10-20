import { z } from 'zod'

/** Mirrors identity-service IDN-1 so users see errors before the request is sent. */
export const passwordRules = [
  { id: 'len', label: 'At least 8 characters', test: (p: string) => p.length >= 8 },
  { id: 'letter', label: 'A letter', test: (p: string) => /[A-Za-z]/.test(p) },
  { id: 'digit', label: 'A digit', test: (p: string) => /\d/.test(p) },
]

export const phoneSchema = z.string().regex(/^\+94\d{9}$/, 'Use the format +94XXXXXXXXX')

export const loginSchema = z.object({
  email: z.string().min(1, 'Email is required').email('Enter a valid email'),
  password: z.string().min(1, 'Password is required'),
})

export const signupSchema = z.object({
  fullName: z.string().trim().min(1, 'Full name is required').max(100),
  email: z.string().min(1, 'Email is required').email('Enter a valid email'),
  phone: phoneSchema,
  password: z
    .string()
    .refine(
      (p) => passwordRules.every((r) => r.test(p)),
      'Password does not meet the requirements',
    ),
})

export const profileSchema = z.object({
  fullName: z.string().trim().min(1, 'Full name is required').max(100),
  phone: phoneSchema,
})

export type LoginInput = z.infer<typeof loginSchema>
export type SignupInput = z.infer<typeof signupSchema>
export type ProfileInput = z.infer<typeof profileSchema>
