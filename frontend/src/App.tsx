import { useState } from 'react'
import { ArrowUpRight, Building2, Search, Wallet } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from '@/components/ui/card'
import { Input } from '@/components/ui/input'

function App() {
  const [searchTerm, setSearchTerm] = useState('')

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background p-6 text-foreground">
      <div className="flex w-full max-w-md flex-col gap-6">
        <header className="flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="flex size-9 items-center justify-center rounded-lg bg-primary text-primary-foreground">
              <Wallet className="size-5" />
            </div>
            <div>
              <h1 className="font-heading text-lg font-semibold tracking-tight">
                FinanceTracker
              </h1>
              <p className="text-xs text-muted-foreground">
                Personal Finance Platform
              </p>
            </div>
          </div>
          <Badge variant="outline">Wiring Demo</Badge>
        </header>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Building2 className="size-4 text-muted-foreground" />
              Primary Savings
            </CardTitle>
            <CardDescription>HDFC Bank •••• 4821</CardDescription>
            <CardAction>
              <Badge variant="secondary">Active</Badge>
            </CardAction>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            <div>
              <span className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
                Available Balance
              </span>
              <p className="font-heading text-3xl font-bold tracking-tight">
                ₹1,45,280.50
              </p>
            </div>
            <div className="relative">
              <Input
                type="text"
                placeholder="Filter transactions..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="pl-8"
              />
              <Search className="pointer-events-none absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
            </div>
          </CardContent>
          <CardFooter className="flex justify-end gap-2">
            <Button variant="outline">Statement</Button>
            <Button variant="default">
              Quick Entry
              <ArrowUpRight data-icon="inline-end" />
            </Button>
          </CardFooter>
        </Card>

        <Card size="sm">
          <CardHeader>
            <CardTitle>Shadcn UI Components</CardTitle>
            <CardDescription>
              Tailwind CSS v4 + Base UI setup verification
            </CardDescription>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button size="sm" variant="default">
              Primary
            </Button>
            <Button size="sm" variant="secondary">
              Secondary
            </Button>
            <Button size="sm" variant="outline">
              Outline
            </Button>
            <Button size="sm" variant="destructive">
              Destructive
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

export default App
