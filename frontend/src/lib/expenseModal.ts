import { reactive } from 'vue'
import type { Expense } from './api'

/** Shared state so the expense dialog can be opened from anywhere and views can reload after saving. */
export const expenseModal = reactive({
  open: false,
  expense: null as Expense | null,
  version: 0,
})

export function openExpenseModal(expense: Expense | null = null) {
  expenseModal.expense = expense
  expenseModal.open = true
}

export function closeExpenseModal(saved: boolean) {
  expenseModal.open = false
  expenseModal.expense = null
  if (saved) expenseModal.version++
}
