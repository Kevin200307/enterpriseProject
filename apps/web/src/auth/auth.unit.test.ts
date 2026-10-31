import { describe, expect, it } from 'vitest'
import { makeJwt } from '@/test/utils'
import { decodeClaims } from './jwt'
import { roleLanding, safeReturnTo, safeRoleReturnTo } from './landing'
import { challengeFor } from './pkce'

describe('decodeClaims', () => {
  it('reads sub, roles and merchantId; drops unknown roles', () => {
    const c = decodeClaims(
      makeJwt(['MERCHANT'], { merchantId: 'm-1', roles: ['MERCHANT', 'WIZARD'] }),
    )
    expect(c?.roles).toEqual(['MERCHANT'])
    expect(c?.merchantId).toBe('m-1')
  })
  it('returns null for garbage', () => {
    expect(decodeClaims('nope')).toBeNull()
    expect(decodeClaims(null)).toBeNull()
  })
})

describe('safeReturnTo', () => {
  it.each(['https://evil.example', '//evil.example', '/\\evil', 'javascript:alert(1)', '', null])(
    'rejects %s',
    (v) => expect(safeReturnTo(v, '/home')).toBe('/home'),
  )
  it('keeps same-origin paths with query strings', () => {
    expect(safeReturnTo('/deliveries?status=FAILED', '/home')).toBe('/deliveries?status=FAILED')
  })
})

describe('roleLanding', () => {
  it.each([
    [['CUSTOMER'], '/home'],
    [['CUSTOMER', 'MERCHANT'], '/merchant/orders'],
    [['COURIER'], '/courier/today'],
    [['DISPATCHER'], '/admin'],
    [['CUSTOMER', 'ADMIN'], '/admin'],
  ] as const)('%j -> %s', (roles, path) => expect(roleLanding([...roles])).toBe(path))
})

describe('safeRoleReturnTo', () => {
  it.each([
    [['CUSTOMER'], '/admin', '/home'],
    [['MERCHANT'], '/home', '/merchant/orders'],
    [['COURIER'], '/admin', '/courier/today'],
    [['DISPATCHER'], '/admin/users', '/admin'],
    [['ADMIN'], '/home', '/admin'],
  ] as const)('uses the role home for %j opening %s', (roles, destination, fallback) => {
    expect(safeRoleReturnTo(destination, fallback, [...roles])).toBe(fallback)
  })

  it('preserves a return path the role can access', () => {
    expect(safeRoleReturnTo('/deliveries?status=FAILED', '/home', ['CUSTOMER'])).toBe(
      '/deliveries?status=FAILED',
    )
  })
})

describe('pkce', () => {
  it('matches the RFC 7636 test vector', async () => {
    expect(await challengeFor('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk')).toBe(
      'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM',
    )
  })
})
