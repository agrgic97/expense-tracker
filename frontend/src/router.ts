import { createRouter, createWebHistory } from 'vue-router'
import { api } from './lib/api'
import { isAuthenticated } from './lib/auth'
import { openExpenseModal } from './lib/expenseModal'
import DashboardView from './views/DashboardView.vue'

const LoginView = () => import('./views/LoginView.vue')
const PasswordResetView = () => import('./views/PasswordResetView.vue')

declare module 'vue-router' {
  interface RouteMeta {
    /** Reachable without login */
    public?: boolean
    /** Only for logged-out users (login, registration); others are sent to the dashboard */
    guestOnly?: boolean
  }
}

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true, guestOnly: true } },
    { path: '/register', name: 'register', component: LoginView, meta: { public: true, guestOnly: true } },
    { path: '/forgot-password', name: 'forgot-password', component: PasswordResetView, meta: { public: true } },
    { path: '/reset-password', name: 'reset-password', component: PasswordResetView, meta: { public: true } },
    { path: '/', name: 'dashboard', component: DashboardView },
    { path: '/expenses', name: 'expenses', component: () => import('./views/ExpensesView.vue') },
    { path: '/import', name: 'import', component: () => import('./views/ImportView.vue') },
    { path: '/budgets', name: 'budgets', component: () => import('./views/BudgetsView.vue') },
    { path: '/recurring', name: 'recurring', component: () => import('./views/RecurringView.vue') },
    { path: '/categories', name: 'categories', component: () => import('./views/CategoriesView.vue') },
    { path: '/account', name: 'account', component: () => import('./views/AccountView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

let sessionChecked: Promise<void> | null = null

router.beforeEach(async (to) => {
  // On the first navigation ask the server whether the cookies still hold a valid session
  await (sessionChecked ??= api.restoreSession())
  const loggedIn = isAuthenticated()
  if (to.meta.public) return loggedIn && to.meta.guestOnly ? '/' : true
  return loggedIn ? true : { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
})

// "?new=expense" or "?new=income" (used by the home screen shortcuts) opens the entry dialog
router.afterEach((to) => {
  const kind = to.query.new
  if (to.meta.public || (kind !== 'expense' && kind !== 'income')) return
  openExpenseModal(null, kind === 'income' ? 'INCOME' : 'EXPENSE')
  const { new: _, ...query } = to.query
  router.replace({ path: to.path, query })
})
