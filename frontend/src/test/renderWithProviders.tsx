import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

import { QueryProvider } from '@/components/QueryProvider'
import { AuthProvider } from '@/features/auth'

import type { RenderResult } from '@testing-library/react'
import type { ReactNode } from 'react'

interface RenderOptions {
  /** Where the memory router starts. Defaults to the accounts page, the one authenticated route that exists today. */
  route?: string
}

/**
 * Renders a component inside the same provider stack `App` uses, in the same order.
 * A test that renders through this proves the real wiring, not a simplified copy of it.
 */
export function renderWithProviders(ui: ReactNode, { route = '/accounts' }: RenderOptions = {}): RenderResult {
  return render(
    <MemoryRouter initialEntries={[route]}>
      <AuthProvider>
        <QueryProvider>{ui}</QueryProvider>
      </AuthProvider>
    </MemoryRouter>,
  )
}
