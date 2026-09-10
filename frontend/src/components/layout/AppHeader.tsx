import { LogOut, Wallet } from 'lucide-react'

import { Button } from '@/components/ui/button'

import { useAuth } from '@/features/auth'

export function AppHeader() {
  const { user, logout } = useAuth()

  return (
    <header className="flex items-center justify-between border-b border-border px-6 py-4">
      <div className="flex items-center gap-2.5">
        <div className="flex size-9 items-center justify-center rounded-lg bg-primary text-primary-foreground">
          <Wallet className="size-5" />
        </div>
        <span className="font-heading text-lg font-semibold tracking-tight">FinanceTracker</span>
      </div>
      <div className="flex items-center gap-3">
        {user && <span className="text-sm text-muted-foreground">{user.displayName}</span>}
        <Button variant="outline" size="sm" onClick={() => void logout()}>
          <LogOut data-icon="inline-start" />
          Log out
        </Button>
      </div>
    </header>
  )
}
