import { render, screen } from '@testing-library/react'
import { HttpResponse, http } from 'msw'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { QueryProvider } from '@/components/QueryProvider'
import { AuthProvider, ProtectedRoute } from '@/features/auth'
import { problemResponse } from '@/test/handlers'
import { renderWithProviders } from '@/test/renderWithProviders'
import { server } from '@/test/server'

import { AccountsPage } from './AccountsPage'

describe('AccountsPage', () => {
  it('renders the accounts the API returned', async () => {
    renderWithProviders(<AccountsPage />)

    expect(await screen.findByText('SBI Savings')).toBeInTheDocument()
    expect(screen.getByText('HDFC Millenia')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1, name: 'Accounts' })).toBeInTheDocument()
  })

  it('says so when the user has no accounts', async () => {
    server.use(http.get('/api/v1/accounts', () => HttpResponse.json([])))
    renderWithProviders(<AccountsPage />)

    expect(await screen.findByText('No accounts yet.')).toBeInTheDocument()
  })

  it('shows an alert when the load fails for a reason other than the session', async () => {
    server.use(http.get('/api/v1/accounts', () => problemResponse(500, 'Could not load accounts.')))
    renderWithProviders(<AccountsPage />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load accounts.')
  })
})

/**
 * The whole chain for a session that expires between page load and the next call (D-41).
 * It needs the route guard in the tree, because the guard is what turns the auth state change into a redirect.
 */
describe('AccountsPage with an expired session', () => {
  it('sends the user to the login page instead of showing an error', async () => {
    server.use(http.get('/api/v1/accounts', () => problemResponse(401, 'Authentication required.')))

    render(
      <MemoryRouter initialEntries={['/accounts']}>
        <AuthProvider>
          <QueryProvider>
            <Routes>
              <Route path="/login" element={<p>Login page</p>} />
              <Route element={<ProtectedRoute />}>
                <Route path="/accounts" element={<AccountsPage />} />
              </Route>
            </Routes>
          </QueryProvider>
        </AuthProvider>
      </MemoryRouter>,
    )

    expect(await screen.findByText('Login page')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
