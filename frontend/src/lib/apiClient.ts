const API_BASE = '/api/v1'

const CSRF_COOKIE_PATTERN = /(?:^|;\s*)XSRF-TOKEN=([^;]*)/

/** RFC 7807 body every backend error returns (FRONTEND_CONVENTIONS §7.3). */
export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  errors?: FieldViolation[]
}

export interface FieldViolation {
  field: string
  message: string
}

export class ApiError extends Error {
  readonly problem: ProblemDetail

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? 'Request failed')
    this.name = 'ApiError'
    this.problem = problem
  }
}

/**
 * Thrown before the request leaves the browser when a state-changing call has no CSRF token to send.
 *
 * The backend loads the token eagerly, so every response carries the cookie, including the 401 from the first `GET /me`.
 * A missing cookie therefore means something is wrong on this side, and failing here says so instead of producing a 403 that looks like a permission problem.
 */
export class CsrfTokenMissingError extends Error {
  constructor() {
    super('No XSRF-TOKEN cookie is available for a state-changing request.')
    this.name = 'CsrfTokenMissingError'
  }
}

interface ApiRequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  /** Passed straight to `fetch`, so a caller can cancel an in-flight request. TanStack Query supplies one per query. */
  signal?: AbortSignal
}

/** True for the one status that means the session is gone rather than that the request was wrong. */
export function isUnauthorized(error: unknown): boolean {
  return error instanceof ApiError && error.problem.status === 401
}

function readCsrfToken(): string | null {
  const match = CSRF_COOKIE_PATTERN.exec(document.cookie)
  return match?.[1] === undefined ? null : decodeURIComponent(match[1])
}

async function toProblemDetail(response: Response): Promise<ProblemDetail> {
  const contentType = response.headers.get('content-type') ?? ''
  if (contentType.includes('application/problem+json')) {
    return (await response.json()) as ProblemDetail
  }
  return { status: response.status, title: response.statusText }
}

/**
 * The one HTTP wrapper every feature calls through (D-41).
 * Always same-origin credentials, a CSRF header on every state-changing method (FRONTEND_CONVENTIONS §7.2), and every non-2xx response turned into an {@link ApiError} carrying the server's problem detail.
 */
export async function apiFetch<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = {}

  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (method !== 'GET') {
    const csrfToken = readCsrfToken()
    if (csrfToken === null) {
      throw new CsrfTokenMissingError()
    }
    headers['X-XSRF-TOKEN'] = csrfToken
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers,
    credentials: 'include',
    signal: options.signal,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })

  if (!response.ok) {
    throw new ApiError(await toProblemDetail(response))
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}
