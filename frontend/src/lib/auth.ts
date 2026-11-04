import { reactive } from 'vue'

export interface User {
  username: string
}

/**
 * Who is logged in. The tokens themselves live in httpOnly cookies that scripts cannot read,
 * so this only mirrors what the server confirmed.
 */
export const auth = reactive({ user: null as User | null })

export const isAuthenticated = () => auth.user !== null

export function setUser(user: User) {
  auth.user = user
}

export function clearUser() {
  auth.user = null
}

// Tokens were kept in localStorage before they moved to cookies; remove leftovers
try {
  localStorage.removeItem('expense-tracker.session')
} catch {
  // storage unavailable
}
