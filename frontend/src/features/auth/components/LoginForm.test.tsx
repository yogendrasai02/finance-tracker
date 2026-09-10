import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { HttpResponse, http } from 'msw'
import { describe, expect, it } from 'vitest'

import { problemResponse } from '@/test/handlers'
import { renderWithProviders } from '@/test/renderWithProviders'
import { server } from '@/test/server'

import { LoginForm } from './LoginForm'

/** Nobody is logged in on the login page, so `GET /me` answers the way the backend does for an anonymous caller. */
function startAnonymous(): void {
  server.use(http.get('/api/v1/me', () => problemResponse(401, 'Authentication required.')))
}

describe('LoginForm', () => {
  it('labels both fields so they can be filled by name', () => {
    startAnonymous()
    renderWithProviders(<LoginForm />, { route: '/login' })

    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toBeInTheDocument()
  })

  it('posts the credentials the user typed', async () => {
    startAnonymous()
    let submitted: unknown = null
    server.use(
      http.post('/api/v1/auth/login', async ({ request }) => {
        submitted = await request.json()
        return HttpResponse.json({ userId: 1, email: 'owner@example.com', displayName: 'Owner' })
      }),
    )
    renderWithProviders(<LoginForm />, { route: '/login' })

    await userEvent.type(screen.getByLabelText('Email'), 'owner@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'correct horse battery')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    await waitFor(() => {
      expect(submitted).toEqual({ email: 'owner@example.com', password: 'correct horse battery' })
    })
  })

  it('shows the backend uniform failure message and marks both fields invalid', async () => {
    startAnonymous()
    server.use(http.post('/api/v1/auth/login', () => problemResponse(401, 'Invalid email or password.')))
    renderWithProviders(<LoginForm />, { route: '/login' })

    await userEvent.type(screen.getByLabelText('Email'), 'owner@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'wrong')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByText('Invalid email or password.')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
  })

  it('never shows the reason when the failure is not an API error', async () => {
    startAnonymous()
    server.use(http.post('/api/v1/auth/login', () => HttpResponse.error()))
    renderWithProviders(<LoginForm />, { route: '/login' })

    await userEvent.type(screen.getByLabelText('Email'), 'owner@example.com')
    await userEvent.type(screen.getByLabelText('Password'), 'whatever')
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByText('Something went wrong. Please try again.')).toBeInTheDocument()
  })
})
