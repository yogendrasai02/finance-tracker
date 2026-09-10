import { Outlet } from 'react-router-dom'

import { AppHeader } from '@/components/layout/AppHeader'

export function AppLayout() {
  return (
    <div className="min-h-screen bg-background text-foreground">
      <AppHeader />
      {/* Each page renders its own <main> and <h1>, so the landmark wraps the page content rather than the header. */}
      <Outlet />
    </div>
  )
}
