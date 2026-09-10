import '@testing-library/jest-dom/vitest'

import { cleanup } from '@testing-library/react'
import { afterAll, afterEach, beforeAll } from 'vitest'

import { server } from './server'

export const TEST_CSRF_TOKEN = 'test-csrf-token'

// Node's fetch rejects a relative URL, but the application deliberately calls same-origin paths like `/api/v1/me`.
// This resolves them the way a browser would, so the code under test is the code that ships.
const nodeFetch = globalThis.fetch
globalThis.fetch = ((input: RequestInfo | URL, init?: RequestInit) => {
  if (typeof input === 'string' && input.startsWith('/')) {
    return nodeFetch(new URL(input, window.location.origin), init)
  }
  return nodeFetch(input, init)
}) as typeof fetch

/** The backend loads the CSRF token eagerly, so a real browser always has this cookie by the time anything is posted. */
function setCsrfCookie(): void {
  document.cookie = `XSRF-TOKEN=${TEST_CSRF_TOKEN}`
}

beforeAll(() => {
  server.listen({ onUnhandledRequest: 'error' })
  setCsrfCookie()
})

afterEach(() => {
  cleanup()
  server.resetHandlers()
  setCsrfCookie()
})

afterAll(() => {
  server.close()
})
