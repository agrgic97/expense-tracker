import { createRouter, createWebHistory } from 'vue-router'
import { isAuthenticated } from './lib/auth'
import DashboardView from './views/DashboardView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('./views/LoginView.vue'), meta: { public: true } },
    { path: '/', name: 'dashboard', component: DashboardView },
    { path: '/expenses', name: 'expenses', component: () => import('./views/ExpensesView.vue') },
    { path: '/categories', name: 'categories', component: () => import('./views/CategoriesView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach((to) => {
  const loggedIn = isAuthenticated()
  if (to.meta.public) return loggedIn && to.name === 'login' ? '/' : true
  return loggedIn ? true : { name: 'login', query: to.fullPath === '/' ? {} : { redirect: to.fullPath } }
})
