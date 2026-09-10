/**
 * The auth feature's public surface (FRONTEND_CONVENTIONS §5.1).
 * Everything else in this folder is internal, and the `no-restricted-imports` rule in `eslint.config.js` enforces that.
 */
export { AuthProvider } from './components/AuthProvider'
export { ProtectedRoute } from './components/ProtectedRoute'
export { LoginPage } from './pages/LoginPage'
export { useAuth } from './useAuth'
export type { AuthContextValue, AuthStatus } from './authContext'
export type { UserProfile } from './types'
