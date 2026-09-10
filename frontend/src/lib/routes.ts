/** Every path the router knows, in one place, so a rename is one edit rather than a search. */
export const ROUTES = {
  login: '/login',
  accounts: '/accounts',
} as const

export type AppRoute = (typeof ROUTES)[keyof typeof ROUTES]
