import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest, tokenStore } from '@/lib/api'

describe('API auth interceptor', () => {
  afterEach(() => tokenStore.set(null))
  it('refreshes once on 401, attaches the new token and retries the request', async () => {
    let protectedCalls = 0
    const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.endsWith('/auth/refresh')) return { ok: true, status: 200, json: async () => ({ accessToken: 'fresh-token', tokenType: 'Bearer', expiresIn: 900, user: { id: 'u1', email: 'a@b.test', role: 'USER' } }) } as Response
      protectedCalls += 1
      if (protectedCalls === 1) return { ok: false, status: 401, json: async () => ({ detail: 'Unauthorized' }) } as Response
      expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer fresh-token')
      return { ok: true, status: 200, json: async () => ({ value: 'ok' }) } as Response
    })
    vi.stubGlobal('fetch', fetchMock)
    await expect(apiRequest<{ value: string }>('/api/v1/private')).resolves.toEqual({ value: 'ok' })
    expect(protectedCalls).toBe(2)
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })
})
