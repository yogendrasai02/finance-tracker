import { HttpResponse, http } from 'msw'

import type { AccountResponse } from '@/features/accounts'
import type { UserProfile } from '@/features/auth'

export const TEST_USER: UserProfile = {
  userId: 1,
  email: 'owner@example.com',
  displayName: 'Owner',
}

export const TEST_ACCOUNTS: AccountResponse[] = [
  { id: 1, name: 'SBI Savings', type: 'ASSET', active: true },
  { id: 2, name: 'HDFC Millenia', type: 'LIABILITY', active: true },
]

/** Builds the same RFC 7807 shape the backend returns, including the content type the client keys off. */
export function problemResponse(status: number, detail: string): Response {
  return HttpResponse.json(
    { type: 'about:blank', title: 'Error', status, detail },
    { status, headers: { 'content-type': 'application/problem+json' } },
  )
}

/** The logged-in happy path. A test that needs another answer overrides just the one route with `server.use(...)`. */
export const defaultHandlers = [
  http.get('/api/v1/me', () => HttpResponse.json(TEST_USER)),
  http.get('/api/v1/accounts', () => HttpResponse.json(TEST_ACCOUNTS)),
  http.post('/api/v1/auth/login', () => HttpResponse.json(TEST_USER)),
  http.post('/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })),
]
