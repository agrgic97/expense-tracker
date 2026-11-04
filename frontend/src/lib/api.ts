import { clearUser, setUser, type User } from './auth'

export type PeriodType = 'WEEK' | 'MONTH' | 'YEAR'

export interface Category {
  id: number
  name: string
  color: string
  expenseCount: number
}

export interface Expense {
  id: number
  amount: number
  date: string
  description: string
  categoryId: number
  categoryName: string
  categoryColor: string
}

export interface ExpenseInput {
  amount: number
  date: string
  description: string
  categoryId: number
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Bucket {
  start: string
  end: string
  total: number
  count: number
}

export interface CategoryTotal {
  categoryId: number
  name: string
  color: string
  total: number
  count: number
  sharePercent: number
}

export interface Summary {
  period: PeriodType
  start: string
  end: string
  current: boolean
  total: number
  count: number
  averagePerDay: number
  averagePerExpense: number
  projectedTotal: number | null
  previousTotal: number
  previousComparableTotal: number
  changePercent: number | null
  byCategory: CategoryTotal[]
  breakdown: Bucket[]
  topExpenses: Expense[]
}

export interface Trend {
  period: PeriodType
  buckets: Bucket[]
  average: number
  max: number
  min: number
}

export interface ExpenseQuery {
  from?: string
  to?: string
  categoryId?: number
  q?: string
  page?: number
  size?: number
}

export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message)
  }
}

export interface Credentials {
  username: string
  password: string
}

const SAFE_METHODS = ['GET', 'HEAD', 'OPTIONS']
// Endpoints whose 401 means "wrong credentials" or "no session" rather than "access token expired"
const NO_REFRESH = ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout']

function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((c) => c.startsWith(`${name}=`))
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null
}

/** The server sets the XSRF-TOKEN cookie on every response; fetch one if we have none yet. */
async function csrfToken(): Promise<string | null> {
  if (!readCookie('XSRF-TOKEN')) await fetch('/api/auth/config', { credentials: 'same-origin' })
  return readCookie('XSRF-TOKEN')
}

let refreshing: Promise<boolean> | null = null

/** Exchanges the refresh cookie for new tokens. Concurrent callers share one request. */
function refreshSession(): Promise<boolean> {
  refreshing ??= send('/auth/refresh', { method: 'POST' }, false)
    .then((response) => response.ok)
    .catch(() => false)
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

async function send(path: string, init: RequestInit = {}, retryOn401 = true): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase()
  const csrf = SAFE_METHODS.includes(method) ? null : await csrfToken()
  const response = await fetch(`/api${path}`, {
    ...init,
    credentials: 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}),
      ...init.headers,
    },
  })
  if (response.status !== 401 || !retryOn401 || NO_REFRESH.includes(path)) return response
  // Access token expired (or missing): refresh once and retry, otherwise the session is over
  if (await refreshSession()) return send(path, init, false)
  clearUser()
  return response
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await send(path, init)
  if (!response.ok) {
    let message = `Fehler ${response.status}`
    try {
      const problem = await response.json()
      message = problem.detail ?? problem.title ?? message
    } catch {
      // body was not JSON
    }
    throw new ApiError(message, response.status)
  }
  return response.status === 204 ? (undefined as T) : response.json()
}

export function queryString(params: object): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  }
  const result = search.toString()
  return result ? `?${result}` : ''
}

export const api = {
  authConfig: () => request<{ registrationEnabled: boolean }>('/auth/config'),
  async login(body: Credentials) {
    setUser(await request<User>('/auth/login', { method: 'POST', body: JSON.stringify(body) }))
  },
  async register(body: Credentials) {
    setUser(await request<User>('/auth/register', { method: 'POST', body: JSON.stringify(body) }))
  },
  async logout() {
    try {
      await send('/auth/logout', { method: 'POST' })
    } finally {
      clearUser()
    }
  },
  /** Checks for an existing session (refreshing it if needed) after a page load. */
  async restoreSession() {
    try {
      setUser(await request<User>('/auth/me'))
    } catch {
      clearUser()
    }
  },

  categories: () => request<Category[]>('/categories'),
  createCategory: (body: Pick<Category, 'name' | 'color'>) =>
    request<Category>('/categories', { method: 'POST', body: JSON.stringify(body) }),
  updateCategory: (id: number, body: Pick<Category, 'name' | 'color'>) =>
    request<Category>(`/categories/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteCategory: (id: number) => request<void>(`/categories/${id}`, { method: 'DELETE' }),

  expenses: (query: ExpenseQuery) => request<Page<Expense>>(`/expenses${queryString(query)}`),
  createExpense: (body: ExpenseInput) =>
    request<Expense>('/expenses', { method: 'POST', body: JSON.stringify(body) }),
  updateExpense: (id: number, body: ExpenseInput) =>
    request<Expense>(`/expenses/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteExpense: (id: number) => request<void>(`/expenses/${id}`, { method: 'DELETE' }),
  /** Downloads the CSV export; done via fetch because a plain link cannot send the bearer token. */
  async downloadExport(query: ExpenseQuery) {
    const response = await send(`/expenses/export${queryString({ ...query, page: undefined, size: undefined })}`)
    if (!response.ok) throw new ApiError(`Export fehlgeschlagen (${response.status})`, response.status)
    const url = URL.createObjectURL(await response.blob())
    const link = Object.assign(document.createElement('a'), { href: url, download: 'ausgaben.csv' })
    link.click()
    URL.revokeObjectURL(url)
  },

  summary: (period: PeriodType, date: string) => request<Summary>(`/stats/summary${queryString({ period, date })}`),
  trend: (period: PeriodType, date: string, count = 12) =>
    request<Trend>(`/stats/trend${queryString({ period, date, count })}`),
}
