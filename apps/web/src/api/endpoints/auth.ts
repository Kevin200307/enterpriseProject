import { api } from '../client'

export interface TokenResponse {
  accessToken: string
  expiresIn?: number
}

export interface RegisterInput {
  email: string
  password: string
  fullName: string
  phone: string
}

export interface UserProfile {
  id: string
  email: string
  fullName: string
  phone: string
  roles: string[]
  status?: string
}

const noRetry = { skipAuthRetry: true }

export const authApi = {
  login: (email: string, password: string) =>
    api.post<TokenResponse>('/api/v1/auth/login', { email, password }, noRetry),
  register: (input: RegisterInput) => api.post<void>('/api/v1/auth/register', input, noRetry),
  refresh: () => api.post<TokenResponse>('/api/v1/auth/refresh', undefined, noRetry),
  logout: () => api.post<void>('/api/v1/auth/logout', undefined, noRetry),
  googleAuthorize: (codeChallenge: string, redirectUri: string) =>
    api.get<{ authorizationUrl: string; state: string }>(
      '/api/v1/auth/oauth2/google/authorize',
      { codeChallenge, redirectUri },
      noRetry,
    ),
  googleCallback: (code: string, state: string, codeVerifier: string, redirectUri: string) =>
    api.get<TokenResponse>(
      '/api/v1/auth/oauth2/callback/google',
      { code, state, codeVerifier, redirectUri },
      noRetry,
    ),
}

export const usersApi = {
  me: () => api.get<UserProfile>('/api/v1/users/me'),
  updateMe: (input: { fullName: string; phone: string }) =>
    api.patch<UserProfile>('/api/v1/users/me', input),
}
