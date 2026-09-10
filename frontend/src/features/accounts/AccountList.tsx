import { Building2, Landmark, PiggyBank } from 'lucide-react'

import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'

import type { AccountResponse, AccountType } from './types'

const TYPE_LABEL: Record<AccountType, string> = {
  ASSET: 'Asset',
  LIABILITY: 'Liability',
  VIRTUAL: 'Virtual',
}

const TYPE_ICON: Record<AccountType, typeof Landmark> = {
  ASSET: Landmark,
  LIABILITY: Building2,
  VIRTUAL: PiggyBank,
}

interface AccountListProps {
  accounts: AccountResponse[]
}

export function AccountList({ accounts }: AccountListProps) {
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      {accounts.map((account) => {
        const Icon = TYPE_ICON[account.type]
        return (
          <Card key={account.id}>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <Icon className="size-4 text-muted-foreground" />
                {account.name}
              </CardTitle>
            </CardHeader>
            <CardContent className="flex items-center gap-2">
              <Badge variant="secondary">{TYPE_LABEL[account.type]}</Badge>
              <Badge variant={account.active ? 'outline' : 'destructive'}>
                {account.active ? 'Active' : 'Inactive'}
              </Badge>
            </CardContent>
          </Card>
        )
      })}
    </div>
  )
}
