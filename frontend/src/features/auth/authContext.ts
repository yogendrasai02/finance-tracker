import { createContext } from 'react'

import type { UserProfile } from './types'

export type AuthStatus = 'loading' | 'authenticated' | 'unauthenticated'

export interface AuthContextValue {
  status: AuthStatus
  user: UserProfile | null
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  /** Called once, from the query client's error handler, when any request comes back 401 after the initial load (D-41). */
  handleUnauthorized: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)
