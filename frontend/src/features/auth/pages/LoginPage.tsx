import { Wallet } from 'lucide-react'
import { Navigate } from 'react-router-dom'

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { ROUTES } from '@/lib/routes'

import { LoginForm } from '../components/LoginForm'
import { useAuth } from '../useAuth'

export function LoginPage() {
  const { status } = useAuth()

  if (status === 'authenticated') {
    return <Navigate to={ROUTES.accounts} replace />
  }

  return (
    <main className="flex min-h-screen flex-col items-center justify-center bg-background p-6 text-foreground">
      <div className="flex w-full max-w-sm flex-col gap-6">
        <div className="flex items-center justify-center gap-2.5">
          <div className="flex size-9 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <Wallet className="size-5" />
          </div>
          <h1 className="font-heading text-lg font-semibold tracking-tight">FinanceTracker</h1>
        </div>
        <Card>
          <CardHeader>
            <CardTitle>Log in</CardTitle>
            <CardDescription>Enter your email and password to continue.</CardDescription>
          </CardHeader>
          <CardContent>
            <LoginForm />
          </CardContent>
        </Card>
      </div>
    </main>
  )
}
