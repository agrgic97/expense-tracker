import { createApp, watch } from 'vue'
import App from './App.vue'
import { auth } from './lib/auth'
import { closeExpenseModal } from './lib/expenseModal'
import { router } from './router'
import './style.css'

// Leave protected pages as soon as the session ends (logout, expiry or a rejected token)
watch(
  () => auth.user,
  (user) => {
    if (user) return
    closeExpenseModal(false)
    const route = router.currentRoute.value
    if (!route.meta.public) {
      router.replace({ name: 'login', query: route.fullPath === '/' ? {} : { redirect: route.fullPath } })
    }
  },
)

createApp(App).use(router).mount('#app')

// Only in production builds: in development the service worker would serve stale modules
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(() => {
      // the app works without it, just not offline or installable
    })
  })
}
