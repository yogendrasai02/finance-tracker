export type AccountType = 'ASSET' | 'LIABILITY' | 'VIRTUAL'

/** FR-1's read side (backend `AccountResponse`): no balance field yet and no dedup method. */
export interface AccountResponse {
  id: number
  name: string
  type: AccountType
  active: boolean
}
