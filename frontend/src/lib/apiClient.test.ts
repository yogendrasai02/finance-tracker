import { HttpResponse, http } from 'msw'
import { describe, expect, it } from 'vitest'

import { problemResponse } from '@/test/handlers'
import { server } from '@/test/server'
import { TEST_CSRF_TOKEN } from '@/test/setup'

import { ApiError, CsrfTokenMissingError, apiFetch, isUnauthorized } from './apiClient'

describe('apiFetch', () => {
  it('returns the parsed body of a successful call', async () => {
    server.use(http.get('/api/v1/thing', () => HttpResponse.json({ value: 42 })))

    await expect(apiFetch<{ value: number }>('/thing')).resolves.toEqual({ value: 42 })
  })

  it('sends the session cookie and no CSRF header on a GET', async () => {
    let sentToken: string | null = null
    server.use(
      http.get('/api/v1/thing', ({ request }) => {
        sentToken = request.headers.get('X-XSRF-TOKEN')
        return HttpResponse.json({})
      }),
    )

    await apiFetch('/thing')

    expect(sentToken).toBeNull()
  })

  it('sends the CSRF token from the cookie on a state-changing call', async () => {
    let sentToken: string | null = null
    server.use(
      http.post('/api/v1/thing', ({ request }) => {
        sentToken = request.headers.get('X-XSRF-TOKEN')
        return HttpResponse.json({})
      }),
    )

    await apiFetch('/thing', { method: 'POST', body: { a: 1 } })

    expect(sentToken).toBe(TEST_CSRF_TOKEN)
  })

  it('fails before sending when there is no CSRF cookie to send', async () => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'

    await expect(apiFetch('/thing', { method: 'POST' })).rejects.toBeInstanceOf(CsrfTokenMissingError)
  })

  it('turns a problem+json body into an ApiError that keeps the detail', async () => {
    server.use(http.get('/api/v1/thing', () => problemResponse(404, 'Not found.')))

    await expect(apiFetch('/thing')).rejects.toMatchObject({
      name: 'ApiError',
      message: 'Not found.',
      problem: { status: 404 },
    })
  })

  it('still produces an ApiError when the failure carries no problem body', async () => {
    server.use(http.get('/api/v1/thing', () => new HttpResponse(null, { status: 500 })))

    const error = await apiFetch('/thing').catch((cause: unknown) => cause)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).problem.status).toBe(500)
  })

  it('resolves to undefined for a 204, which is what logout returns', async () => {
    server.use(http.post('/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })))

    await expect(apiFetch<void>('/auth/logout', { method: 'POST' })).resolves.toBeUndefined()
  })

  it('passes an abort signal through to fetch', async () => {
    server.use(http.get('/api/v1/thing', () => HttpResponse.json({})))
    const controller = new AbortController()
    controller.abort()

    await expect(apiFetch('/thing', { signal: controller.signal })).rejects.toThrow()
  })
})

describe('isUnauthorized', () => {
  it('is true only for a 401 ApiError', () => {
    expect(isUnauthorized(new ApiError({ status: 401 }))).toBe(true)
    expect(isUnauthorized(new ApiError({ status: 403 }))).toBe(false)
    expect(isUnauthorized(new Error('network down'))).toBe(false)
  })
})
