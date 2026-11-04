<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, type Category, type Expense, type ExpenseQuery, type Page } from '../lib/api'
import { expenseModal, openExpenseModal } from '../lib/expenseModal'
import { formatDate, money } from '../lib/format'

const categories = ref<Category[]>([])
const result = ref<Page<Expense> | null>(null)
const error = ref('')
const confirmId = ref<number | null>(null)

const filter = reactive({ q: '', categoryId: '' as number | '', from: '', to: '' })
const page = ref(0)
const size = 25

const query = computed<ExpenseQuery>(() => ({
  q: filter.q || undefined,
  categoryId: filter.categoryId === '' ? undefined : filter.categoryId,
  from: filter.from || undefined,
  to: filter.to || undefined,
  page: page.value,
  size,
}))

const pageSum = computed(() => result.value?.content.reduce((sum, e) => sum + e.amount, 0) ?? 0)
const hasFilter = computed(() => !!(filter.q || filter.categoryId !== '' || filter.from || filter.to))

async function load() {
  error.value = ''
  try {
    result.value = await api.expenses(query.value)
  } catch (e) {
    error.value = (e as Error).message
  }
}

let debounce: ReturnType<typeof setTimeout> | undefined
watch(filter, () => {
  clearTimeout(debounce)
  debounce = setTimeout(() => {
    if (page.value === 0) load()
    else page.value = 0
  }, 250)
})
watch([page, () => expenseModal.version], load)

onMounted(async () => {
  load()
  try {
    categories.value = await api.categories()
  } catch (e) {
    error.value = (e as Error).message
  }
})

async function remove(expense: Expense) {
  if (confirmId.value !== expense.id) {
    confirmId.value = expense.id
    return
  }
  try {
    await api.deleteExpense(expense.id)
    confirmId.value = null
    if (result.value?.content.length === 1 && page.value > 0) page.value--
    else load()
  } catch (e) {
    error.value = (e as Error).message
  }
}

async function exportCsv() {
  try {
    await api.downloadExport(query.value)
  } catch (e) {
    error.value = (e as Error).message
  }
}

function resetFilter() {
  Object.assign(filter, { q: '', categoryId: '', from: '', to: '' })
}
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <h1>Ausgaben</h1>
      <div class="row">
        <button @click="exportCsv">⬇ CSV-Export</button>
        <button class="primary" @click="openExpenseModal()">＋ Neue Ausgabe</button>
      </div>
    </div>

    <div class="card filters">
      <label class="field grow">
        Suche
        <input v-model="filter.q" type="search" placeholder="Beschreibung…" />
      </label>
      <label class="field">
        Kategorie
        <select v-model="filter.categoryId">
          <option value="">Alle</option>
          <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
      </label>
      <label class="field">
        Von
        <input v-model="filter.from" type="date" />
      </label>
      <label class="field">
        Bis
        <input v-model="filter.to" type="date" />
      </label>
      <button v-if="hasFilter" class="ghost reset" @click="resetFilter">Zurücksetzen</button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <section class="card">
      <div v-if="result && result.content.length" class="table-wrap">
        <table>
          <thead>
            <tr><th>Datum</th><th>Beschreibung</th><th>Kategorie</th><th class="num">Betrag</th><th /></tr>
          </thead>
          <tbody>
            <tr v-for="e in result.content" :key="e.id">
              <td class="nowrap muted">{{ formatDate(e.date) }}</td>
              <td>{{ e.description }}</td>
              <td><span class="chip"><span class="dot" :style="{ background: e.categoryColor }" />{{ e.categoryName }}</span></td>
              <td class="num"><strong>{{ money(e.amount) }}</strong></td>
              <td class="actions">
                <button class="ghost" title="Bearbeiten" @click="openExpenseModal(e)">✏️</button>
                <button class="ghost danger" :title="confirmId === e.id ? 'Wirklich löschen?' : 'Löschen'"
                        @click="remove(e)" @blur="confirmId = null">
                  {{ confirmId === e.id ? 'Löschen?' : '🗑' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="result" class="empty">
        {{ hasFilter ? 'Keine Ausgaben für diese Filter gefunden.' : 'Noch keine Ausgaben erfasst.' }}
      </div>

      <div v-if="result && result.totalElements > 0" class="footer row">
        <span class="muted small">
          {{ result.totalElements }} Einträge · Summe dieser Seite: <strong>{{ money(pageSum) }}</strong>
        </span>
        <div v-if="result.totalPages > 1" class="row">
          <button :disabled="page === 0" @click="page--">‹ Zurück</button>
          <span class="small muted">Seite {{ page + 1 }} / {{ result.totalPages }}</span>
          <button :disabled="page >= result.totalPages - 1" @click="page++">Weiter ›</button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.filters { display: flex; flex-wrap: wrap; gap: 12px; align-items: flex-end; }
.grow { flex: 1; min-width: 200px; }
.reset { align-self: flex-end; }
.nowrap { white-space: nowrap; }
.actions { white-space: nowrap; text-align: right; width: 1%; }
.footer { justify-content: space-between; margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--border); }
</style>
