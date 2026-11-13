<script setup lang="ts">
import { useRoute } from 'vue-router'
import EntryDialog from './components/EntryDialog.vue'
import { api } from './lib/api'
import { auth } from './lib/auth'
import { expenseModal, openExpenseModal } from './lib/expenseModal'

const route = useRoute()

const links = [
  { to: '/', label: 'Übersicht', icon: '📊' },
  { to: '/expenses', label: 'Buchungen', icon: '🧾' },
  { to: '/budgets', label: 'Budgets', icon: '🎯' },
  { to: '/recurring', label: 'Wiederkehrend', icon: '🔁' },
  { to: '/categories', label: 'Kategorien', icon: '🏷️' },
]
</script>

<template>
  <RouterView v-if="route.meta.public" />
  <div v-else class="layout">
    <aside class="sidebar">
      <div class="brand">💶 <span>Ausgaben</span></div>
      <nav>
        <RouterLink v-for="link in links" :key="link.to" :to="link.to" class="nav-link">
          <span class="icon">{{ link.icon }}</span><span class="label">{{ link.label }}</span>
        </RouterLink>
      </nav>
      <button class="primary add" title="Ausgabe, Einnahme oder Kontoauszug erfassen" @click="openExpenseModal()">
        ＋ <span class="label">Erfassen</span>
      </button>
      <div class="account">
        <RouterLink to="/account" class="user" :title="`Konto: ${auth.user?.username}`">
          👤 <span class="label">{{ auth.user?.username }}</span>
        </RouterLink>
        <button class="ghost" title="Abmelden" @click="api.logout()">⎋ <span class="label">Abmelden</span></button>
      </div>
    </aside>
    <main class="content">
      <RouterView />
    </main>
    <EntryDialog v-if="expenseModal.open" />
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

.account {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 4px 0 10px;
  border-top: 1px solid var(--border);
}
.user {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--muted);
  font-size: 0.9rem;
  text-decoration: none;
  border-radius: 8px;
  padding: 4px 6px;
  margin-left: -6px;
}
.user:hover, .user.router-link-active { background: var(--surface-2); color: var(--text); }

.content { flex: 1; min-width: 0; padding: 28px 32px 48px; max-width: 1280px; }

/* Phones: slim top bar, tab bar at the bottom within thumb reach, floating add button */
@media (max-width: 760px) {
  .layout { flex-direction: column; }
  .sidebar {
    width: 100%;
    height: auto;
    z-index: 10;
    flex-direction: row;
    align-items: center;
    padding: calc(8px + env(safe-area-inset-top)) 16px 8px;
    border-right: none;
    border-bottom: 1px solid var(--border);
    gap: 8px;
  }
  .brand span { display: inline; }
  .brand { padding: 0; flex: 1; }
  .account { margin-top: 0; padding: 0; border-top: none; }
  .account .label { display: none; }

  nav {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 20;
    flex-direction: row;
    justify-content: space-around;
    gap: 0;
    background: var(--surface);
    border-top: 1px solid var(--border);
    padding: 4px 4px calc(4px + env(safe-area-inset-bottom));
  }
  .nav-link { flex: 1; flex-direction: column; gap: 0; padding: 6px 2px; font-size: 0.68rem; text-align: center; }
  .nav-link .icon { font-size: 1.25rem; line-height: 1.3; }
  .nav-link.router-link-exact-active { background: transparent; }

  .add {
    position: fixed;
    right: 16px;
    bottom: calc(76px + env(safe-area-inset-bottom));
    z-index: 30;
    width: 56px;
    height: 56px;
    border-radius: 50%;
    font-size: 1.6rem;
    padding: 0;
    box-shadow: 0 6px 18px rgba(79, 70, 229, 0.4);
  }
  .add .label { display: none; }

  .content { padding: 20px 16px calc(150px + env(safe-area-inset-bottom)); }
}
</style>
