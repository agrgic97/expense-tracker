import { createRouter, createWebHistory } from 'vue-router'
import { api } from './lib/api'
import { isAuthenticated } from './lib/auth'
import DashboardView from './views/DashboardView.vue'

const LoginView = () => import('./views/LoginView.vue')

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    { path: '/register', name: 'register', component: LoginView, meta: { public: true } },
    { path: '/', name: 'dashboard', component: DashboardView },
    { path: '/expenses', name: 'expenses', component: () => import('./views/ExpensesView.vue') },
    { path: '/categories', name: 'categories', component: () => import('./views/CategoriesView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

let sessionChecked: Promise<void> | null = null

router.beforeEach(async (to) => {
  // On the first navigation ask the server whether the cookies still hold a valid session
  await (sessionChecked ??= api.restoreSession())
  const loggedIn = isAuthenticated()
  if (to.meta.public) return loggedIn ? '/' : true
  return loggedIn ? true : { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
})
