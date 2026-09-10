/** What login and `GET /api/v1/me` both return (backend `UserProfile`). */
export interface UserProfile {
  userId: number
  email: string
  displayName: string
}
