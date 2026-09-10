import { MutationCache, QueryCache, QueryClient } from '@tanstack/react-query'

import { ApiError, isUnauthorized } from '@/lib/apiClient'

/** Two attempts for a server fault or a dropped connection, and none for a 4xx, which will fail the same way every time. */
function shouldRetry(failureCount: number, error: Error): boolean {
  if (error instanceof ApiError && error.problem.status < 500) {
    return false
  }
  return failureCount < 2
}

/**
 * Builds the single query client for the application.
 *
 * `onUnauthorized` is the reason this is a factory rather than a module-level constant: a session that expires mid-use is handled once here, instead of in every page that fetches (D-41).
 */
export function createQueryClient(onUnauthorized: () => void): QueryClient {
  const handleError = (error: Error): void => {
    if (isUnauthorized(error)) {
      onUnauthorized()
    }
  }

  return new QueryClient({
    defaultOptions: {
      queries: {
        // Retries are off under Vitest so a test that asserts a failure state does not sit through the backoff first.
        retry: import.meta.env.MODE === 'test' ? false : shouldRetry,
        staleTime: 30_000,
        refetchOnWindowFocus: false,
      },
      mutations: {
        retry: false,
      },
    },
    queryCache: new QueryCache({ onError: handleError }),
    mutationCache: new MutationCache({ onError: handleError }),
  })
}
