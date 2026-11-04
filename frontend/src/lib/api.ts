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

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...init?.headers },
  })
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
  exportUrl: (query: ExpenseQuery) => `/api/expenses/export${queryString({ ...query, page: undefined, size: undefined })}`,

  summary: (period: PeriodType, date: string) => request<Summary>(`/stats/summary${queryString({ period, date })}`),
  trend: (period: PeriodType, date: string, count = 12) =>
    request<Trend>(`/stats/trend${queryString({ period, date, count })}`),
}
