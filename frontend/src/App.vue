<script setup lang="ts">
import ExpenseDialog from './components/ExpenseDialog.vue'
import { expenseModal, openExpenseModal } from './lib/expenseModal'

const links = [
  { to: '/', label: 'Übersicht', icon: '📊' },
  { to: '/expenses', label: 'Ausgaben', icon: '🧾' },
  { to: '/categories', label: 'Kategorien', icon: '🏷️' },
]
</script>

<template>
  <div class="layout">
    <aside class="sidebar">
      <div class="brand">💶 <span>Ausgaben</span></div>
      <nav>
        <RouterLink v-for="link in links" :key="link.to" :to="link.to" class="nav-link">
          <span class="icon">{{ link.icon }}</span><span class="label">{{ link.label }}</span>
        </RouterLink>
      </nav>
      <button class="primary add" @click="openExpenseModal()">＋ <span class="label">Neue Ausgabe</span></button>
    </aside>
    <main class="content">
      <RouterView />
    </main>
    <ExpenseDialog v-if="expenseModal.open" />
  </div>
</template>

<style scoped>
.layout { display: flex; min-height: 100vh; }

.sidebar {
  width: 230px;
  flex-shrink: 0;
  background: var(--surface);
  border-right: 1px solid var(--border);
  padding: 22px 14px;
  display: flex;
  flex-direction: column;
  gap: 22px;
  position: sticky;
  top: 0;
  height: 100vh;
}

.brand { font-weight: 700; font-size: 1.15rem; padding: 0 10px; }

nav { display: flex; flex-direction: column; gap: 4px; }

.nav-link {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: 10px;
  color: var(--text);
  text-decoration: none;
}
.nav-link:hover { background: var(--surface-2); }
.nav-link.router-link-exact-active { background: var(--accent-soft); color: var(--accent); font-weight: 600; }

.add { justify-content: center; }

.content { flex: 1; min-width: 0; padding: 28px 32px 48px; max-width: 1280px; }

@media (max-width: 760px) {
  .layout { flex-direction: column; }
  .sidebar {
    width: 100%;
    height: auto;
    position: sticky;
    z-index: 10;
    flex-direction: row;
    align-items: center;
    padding: 10px 16px;
    border-right: none;
    border-bottom: 1px solid var(--border);
    gap: 8px;
  }
  .brand span, .label { display: none; }
  .brand { padding: 0; }
  nav { flex-direction: row; flex: 1; }
  .content { padding: 20px 16px 40px; }
}
</style>
