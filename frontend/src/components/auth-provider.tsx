import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { authApi, tokenStore, type AuthResponse, type User } from '@/lib/api'

type AuthValue = { user: User | null; loading: boolean; accept: (response: AuthResponse) => void; signOut: () => Promise<void> }
const AuthContext = createContext<AuthValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  useEffect(() => {
    let mounted = true
    void authApi.refresh().then(async (session) => {
      if (!mounted || !session) return
      try { const profile = await authApi.me(); if (mounted) setUser(profile) }
      catch { tokenStore.set(null) }
    }).finally(() => { if (mounted) setLoading(false) })
    const expired = () => { setUser(null); setLoading(false) }
    window.addEventListener('auth:expired', expired)
    return () => { mounted = false; window.removeEventListener('auth:expired', expired) }
  }, [])
  const value = useMemo<AuthValue>(() => ({
    user, loading,
    accept: (response) => { tokenStore.set(response.accessToken); setUser(response.user) },
    signOut: async () => { try { await authApi.logout() } finally { tokenStore.set(null); setUser(null) } },
  }), [user, loading])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used within AuthProvider')
  return value
}
