import { type ReactNode } from 'react'
import { createBrowserRouter, Navigate, Outlet, RouterProvider } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider, useAuth } from '@/components/auth-provider'
import { AppShell } from '@/components/app-shell'
import { ErrorBoundary } from '@/components/error-boundary'
import { ThemeProvider } from '@/components/theme-provider'
import { Toaster } from '@/components/ui/sonner'
import { LoginPage, RegisterPage } from '@/pages/auth-pages'
import { NotFoundPage, WorkspacePage } from '@/pages/workspace-pages'
import { StyleguidePage } from '@/pages/styleguide'
import { TooltipProvider } from '@/components/ui/tooltip'

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 30_000 } } })
function ProtectedLayout() {
  const { user, loading } = useAuth()
  if (loading) return <main className="grid min-h-screen place-items-center"><p className="text-sm text-muted-foreground" role="status">Opening your vault…</p></main>
  return user ? <AppShell /> : <Navigate to="/login" replace />
}
function GuestOnly({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth()
  if (loading) return <main className="grid min-h-screen place-items-center"><p role="status" className="text-sm text-muted-foreground">Loading…</p></main>
  return user ? <Navigate to="/" replace /> : children
}
const router = createBrowserRouter([
  { path: '/login', element: <GuestOnly><LoginPage /></GuestOnly> },
  { path: '/register', element: <GuestOnly><RegisterPage /></GuestOnly> },
  { path: '/styleguide', element: <StyleguidePage /> },
  { element: <ProtectedLayout />, children: [
    { path: '/', element: <WorkspacePage /> },
    ...['shared', 'recent', 'trash', 'storage', 'activity', 'settings'].map(path => ({ path, element: <WorkspacePage /> })),
  ] },
  { path: '*', element: <NotFoundPage /> },
])

export function App() {
  return <ErrorBoundary><ThemeProvider><QueryClientProvider client={queryClient}><AuthProvider><TooltipProvider delayDuration={300}><RouterProvider router={router} /><Toaster /></TooltipProvider></AuthProvider></QueryClientProvider></ThemeProvider></ErrorBoundary>
}
