import { Navigate, Outlet } from 'react-router-dom'

import { Spinner } from '@/components/ui/spinner'
import { ROUTES } from '@/lib/routes'

import { useAuth } from '../useAuth'

/** Gates every route nested under it (D-41): unauthenticated goes to the login page, loading shows a spinner. */
export function ProtectedRoute() {
  const { status } = useAuth()

  if (status === 'loading') {
    return (
      <div className="flex min-h-screen items-center justify-center" role="status" aria-label="Loading">
        <Spinner className="size-6" />
      </div>
    )
  }

  if (status === 'unauthenticated') {
    return <Navigate to={ROUTES.login} replace />
  }

  return <Outlet />
}
