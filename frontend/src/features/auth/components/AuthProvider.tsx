import { useCallback, useEffect, useMemo, useState } from 'react'

import { getMe, login as loginRequest, logout as logoutRequest } from '../authApi'
import { AuthContext } from '../authContext'

import type { AuthContextValue, AuthStatus } from '../authContext'
import type { UserProfile } from '../types'
import type { ReactNode } from 'react'

/**
 * Holds who is logged in, and is the only part of the application that fetches without going through the query client.
 * It has to be: the query client is built from this provider's `handleUnauthorized`, so it does not exist yet when this first call runs.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading')
  const [user, setUser] = useState<UserProfile | null>(null)

  useEffect(() => {
    const controller = new AbortController()

    getMe(controller.signal)
      .then((profile) => {
        if (controller.signal.aborted) return
        setUser(profile)
        setStatus('authenticated')
      })
      .catch(() => {
        if (controller.signal.aborted) return
        setUser(null)
        setStatus('unauthenticated')
      })

    return () => {
      controller.abort()
    }
  }, [])

  const login = useCallback(async (email: string, password: string): Promise<void> => {
    const profile = await loginRequest(email, password)
    setUser(profile)
    setStatus('authenticated')
  }, [])

  const logout = useCallback(async (): Promise<void> => {
    try {
      await logoutRequest()
    } finally {
      // The local state is cleared even if the call failed, because a user who pressed log out must not be left looking at their data.
      setUser(null)
      setStatus('unauthenticated')
    }
  }, [])

  const handleUnauthorized = useCallback((): void => {
    setUser(null)
    setStatus('unauthenticated')
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ status, user, login, logout, handleUnauthorized }),
    [status, user, login, logout, handleUnauthorized],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
