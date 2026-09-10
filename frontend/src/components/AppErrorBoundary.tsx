import { Component } from 'react'

import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

import type { ErrorInfo, ReactNode } from 'react'

interface AppErrorBoundaryProps {
  children: ReactNode
}

interface AppErrorBoundaryState {
  failed: boolean
}

/**
 * The one class component the codebase allows (FRONTEND_CONVENTIONS §2.1).
 * React still offers no hook that catches a render error, so a boundary has to be a class.
 *
 * Without it a single throw anywhere in the tree unmounts the whole application and leaves a blank page.
 */
export class AppErrorBoundary extends Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = { failed: false }

  static getDerivedStateFromError(): AppErrorBoundaryState {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // Only in a development build: an error carrying a transaction narration must not reach a production console (SR §7.4).
    if (import.meta.env.DEV) {
      console.error('Unhandled render error', error, info.componentStack)
    }
  }

  render(): ReactNode {
    if (!this.state.failed) {
      return this.props.children
    }

    // The message is deliberately generic, because the error text can carry backend detail the user should not see.
    return (
      <div className="flex min-h-screen items-center justify-center bg-background p-6 text-foreground">
        <Card className="w-full max-w-sm">
          <CardHeader>
            <CardTitle>Something went wrong</CardTitle>
            <CardDescription>The page could not be displayed. Reloading usually fixes it.</CardDescription>
          </CardHeader>
          <CardContent>
            <Button onClick={() => window.location.reload()}>Reload</Button>
          </CardContent>
        </Card>
      </div>
    )
  }
}
