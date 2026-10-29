import { config } from './config'

export type User = { id: string; email: string; role: string }
export type AuthResponse = { accessToken: string; tokenType: string; expiresIn: number; user: User }

let accessToken: string | null = null
let refreshPromise: Promise<AuthResponse | null> | null = null

export const tokenStore = {
  get: () => accessToken,
  set: (token: string | null) => { accessToken = token },
}

async function refresh(): Promise<AuthResponse | null> {
  if (!refreshPromise) {
    refreshPromise = fetch(`${config.apiBaseUrl}/api/v1/auth/refresh`, {
      method: 'POST', credentials: 'include', headers: { Accept: 'application/json' },
    }).then(async (response) => {
      if (!response.ok) throw new Error('Session expired')
      const body = await response.json() as AuthResponse
      tokenStore.set(body.accessToken)
      return body
    }).catch(() => {
      tokenStore.set(null)
      window.dispatchEvent(new Event('auth:expired'))
      return null
    }).finally(() => { refreshPromise = null })
  }
  return refreshPromise
}

export async function apiRequest<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  let response = await fetch(`${config.apiBaseUrl}${path}`, { ...init, headers, credentials: 'include' })
  if (response.status === 401 && retry && !path.endsWith('/auth/refresh')) {
    const renewed = await refresh()
    if (renewed) return apiRequest<T>(path, init, false)
  }
  if (!response.ok) {
    let message = response.status === 401 ? 'Please sign in to continue.' : 'Something went wrong. Please try again.'
    try {
      const problem = await response.json() as { detail?: string; title?: string }
      if (problem.detail) message = problem.detail
    } catch { /* non-JSON error body */ }
    throw Object.assign(new Error(message), { status: response.status })
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const authApi = {
  login: (email: string, password: string) => apiRequest<AuthResponse>('/api/v1/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }, false),
  register: (email: string, password: string) => apiRequest<AuthResponse>('/api/v1/auth/register', { method: 'POST', body: JSON.stringify({ email, password }) }, false),
  refresh,
  me: () => apiRequest<User>('/api/v1/auth/me'),
  logout: () => apiRequest<void>('/api/v1/auth/logout', { method: 'POST' }),
}
