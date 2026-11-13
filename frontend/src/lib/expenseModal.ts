import { reactive } from 'vue'
import type { CategoryKind, Expense } from './api'

/** Shared state so the entry dialog can be opened from anywhere and views can reload after saving. */
export const expenseModal = reactive({
  open: false,
  expense: null as Expense | null,
  /** Kind of a new entry; an edited entry keeps its own */
  kind: 'EXPENSE' as CategoryKind,
  version: 0,
})

export function openExpenseModal(expense: Expense | null = null, kind: CategoryKind = 'EXPENSE') {
  expenseModal.expense = expense
  expenseModal.kind = expense?.kind ?? kind
  expenseModal.open = true
}

export function closeExpenseModal(saved: boolean) {
  expenseModal.open = false
  expenseModal.expense = null
  if (saved) expenseModal.version++
}
