import { useQuery } from '@tanstack/react-query'

import { Alert, AlertDescription } from '@/components/ui/alert'
import { Spinner } from '@/components/ui/spinner'
import { ApiError, isUnauthorized } from '@/lib/apiClient'

import { AccountList } from './AccountList'
import { accountQueryKeys, listAccounts } from './accountsApi'

export function AccountsPage() {
  const { data, isPending, isError, error } = useQuery({
    queryKey: accountQueryKeys.list(),
    queryFn: ({ signal }) => listAccounts(signal),
  })

  // A 401 is not this page's problem: the query client has already told the auth feature, and the route guard is about to send the user to the login page (D-41).
  const failedForAnotherReason = isError && !isUnauthorized(error)

  return (
    <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 p-6">
      <div>
        <h1 className="font-heading text-2xl font-semibold tracking-tight">Accounts</h1>
        <p className="text-sm text-muted-foreground">Your linked accounts.</p>
      </div>

      {isPending && (
        <div className="flex justify-center py-12" role="status" aria-label="Loading accounts">
          <Spinner className="size-6" />
        </div>
      )}

      {failedForAnotherReason && (
        <Alert variant="destructive">
          <AlertDescription>
            {error instanceof ApiError ? error.message : 'Could not load accounts.'}
          </AlertDescription>
        </Alert>
      )}

      {data && data.length === 0 && (
        <p className="text-sm text-muted-foreground">No accounts yet.</p>
      )}

      {data && data.length > 0 && <AccountList accounts={data} />}
    </main>
  )
}
