import { apiFetch } from '@/lib/apiClient'

import type { UserProfile } from './types'

export function login(email: string, password: string): Promise<UserProfile> {
  return apiFetch<UserProfile>('/auth/login', { method: 'POST', body: { email, password } })
}

export function logout(): Promise<void> {
  return apiFetch<void>('/auth/logout', { method: 'POST' })
}

export function getMe(signal?: AbortSignal): Promise<UserProfile> {
  return apiFetch<UserProfile>('/me', { signal })
}
