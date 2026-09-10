import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'

import { AppErrorBoundary } from '@/components/AppErrorBoundary'
import { QueryProvider } from '@/components/QueryProvider'
import { AppLayout } from '@/components/layout/AppLayout'
import { AccountsPage } from '@/features/accounts'
import { AuthProvider, LoginPage, ProtectedRoute } from '@/features/auth'
import { ROUTES } from '@/lib/routes'

function App() {
  return (
    <AppErrorBoundary>
      <BrowserRouter>
        <AuthProvider>
          <QueryProvider>
            <Routes>
              <Route path={ROUTES.login} element={<LoginPage />} />
              <Route element={<ProtectedRoute />}>
                <Route element={<AppLayout />}>
                  <Route path={ROUTES.accounts} element={<AccountsPage />} />
                </Route>
              </Route>
              <Route path="*" element={<Navigate to={ROUTES.accounts} replace />} />
            </Routes>
          </QueryProvider>
        </AuthProvider>
      </BrowserRouter>
    </AppErrorBoundary>
  )
}

export default App
