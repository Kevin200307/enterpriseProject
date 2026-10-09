import { beforeEach, describe, expect, it } from 'vitest'
import { tokenStore } from './tokenStore'

beforeEach(() => tokenStore.clear())

describe('tokenStore.refresh', () => {
  it('shares one in-flight refresh between concurrent callers', async () => {
    let calls = 0
    tokenStore.setRefresher(async () => (calls++, 'fresh'))
    const results = await Promise.all([
      tokenStore.refresh(),
      tokenStore.refresh(),
      tokenStore.refresh(),
    ])
    expect(results).toEqual(['fresh', 'fresh', 'fresh'])
    expect(calls).toBe(1)
  })

  it('clears the token when refresh fails', async () => {
    tokenStore.set('stale')
    tokenStore.setRefresher(async () => {
      throw new Error('401')
    })
    expect(await tokenStore.refresh()).toBeNull()
    expect(tokenStore.get()).toBeNull()
  })

  it('does not wipe a sign-in that completed while a refresh was in flight', async () => {
    let fail!: () => void
    tokenStore.setRefresher(
      () =>
        new Promise((_, reject) => {
          fail = () => reject(new Error('401'))
        }),
    )
    const pending = tokenStore.refresh()
    tokenStore.set('signed-in-meanwhile')
    fail()
    await pending
    expect(tokenStore.get()).toBe('signed-in-meanwhile')
  })
})
