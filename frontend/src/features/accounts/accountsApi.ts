import { apiFetch } from '@/lib/apiClient'

import type { AccountResponse } from './types'

/** Query keys live beside the calls they belong to, so a mutation elsewhere in this feature can invalidate them without guessing the string. */
export const accountQueryKeys = {
  all: ['accounts'] as const,
  list: () => [...accountQueryKeys.all, 'list'] as const,
}

export function listAccounts(signal?: AbortSignal): Promise<AccountResponse[]> {
  return apiFetch<AccountResponse[]>('/accounts', { signal })
}
