import { QueryClientProvider } from '@tanstack/react-query'
import { useEffect, useState } from 'react'

import { useAuth } from '@/features/auth'
import { createQueryClient } from '@/lib/queryClient'

import type { ReactNode } from 'react'

/**
 * Sits under `AuthProvider` so the query client can be built with the auth feature's own 401 handler.
 *
 * The client is created once through the lazy `useState` initialiser, never on a re-render, because recreating it would throw away every cached query.
 */
export function QueryProvider({ children }: { children: ReactNode }) {
  const { status, handleUnauthorized } = useAuth()
  const [queryClient] = useState(() => createQueryClient(handleUnauthorized))

  useEffect(() => {
    // Logout and an expired session both land here, so no account data is left in memory for whoever logs in next.
    if (status === 'unauthenticated') {
      queryClient.clear()
    }
  }, [status, queryClient])

  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
}
