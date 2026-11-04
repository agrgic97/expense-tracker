import { reactive } from 'vue'

export interface Session {
  token: string
  username: string
  expiresAt: string
}

const STORAGE_KEY = 'expense-tracker.session'

function isValid(session: Session | null): session is Session {
  return !!session && new Date(session.expiresAt).getTime() > Date.now()
}

function restore(): Session | null {
  try {
    const session = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null') as Session | null
    return isValid(session) ? session : null
  } catch {
    return null
  }
}

export const auth = reactive({ session: restore() })

export function setSession(session: Session) {
  auth.session = session
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
  } catch {
    // storage unavailable: session lives only in memory
  }
}

export function clearSession() {
  auth.session = null
  try {
    localStorage.removeItem(STORAGE_KEY)
  } catch {
    // ignore
  }
}

/** Returns the bearer token, or null (and logs out) once it has expired. */
export function currentToken(): string | null {
  if (!auth.session) return null
  if (!isValid(auth.session)) {
    clearSession()
    return null
  }
  return auth.session.token
}

export const isAuthenticated = () => currentToken() !== null
