import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AuthProvider, useAuth } from '@/components/auth-provider'
import { ThemeProvider } from '@/components/theme-provider'
import { TooltipProvider } from '@/components/ui/tooltip'
import { AppShell } from '@/components/app-shell'
import { LoginPage, RegisterPage } from '@/pages/auth-pages'
import { WorkspacePage } from '@/pages/workspace-pages'

function PrivateRoute() {
  const { user, loading } = useAuth()
  if (loading) return <p role="status">Loading</p>
  return user ? <AppShell /> : <p>Not signed in</p>
}
function TestApp() {
  return <ThemeProvider><AuthProvider><TooltipProvider><MemoryRouter initialEntries={['/register']}><Routes>
    <Route path="/register" element={<RegisterPage />} /><Route path="/login" element={<LoginPage />} />
    <Route element={<PrivateRoute />}><Route path="/" element={<WorkspacePage />} /></Route>
  </Routes></MemoryRouter></TooltipProvider></AuthProvider></ThemeProvider>
}

describe('auth flow', () => {
  it('registers and opens the empty vault shell', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url.endsWith('/auth/refresh')) return { ok: false, status: 401, json: async () => ({ detail: 'Unauthorized' }) } as Response
      if (url.endsWith('/auth/register')) return { ok: true, status: 201, json: async () => ({ accessToken: 'access-in-memory', tokenType: 'Bearer', expiresIn: 900, user: { id: 'usr-1', email: 'person@example.com', role: 'USER' } }) } as Response
      throw new Error(`Unexpected request ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    render(<TestApp />)
    await user.type(await screen.findByLabelText('Email address'), 'person@example.com')
    await user.type(screen.getByLabelText('Password'), 'Password1!')
    await user.type(screen.getByLabelText('Confirm password'), 'Password1!')
    await user.click(screen.getByRole('button', { name: 'Create account' }))
    expect(await screen.findByText('Your vault is ready')).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Main navigation' })).toBeInTheDocument()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/auth/register'), expect.objectContaining({ method: 'POST' })))
  })
})
