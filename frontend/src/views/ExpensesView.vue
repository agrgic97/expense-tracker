<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, type Category, type CategoryKind, type Expense, type ExpenseQuery, type Page } from '../lib/api'
import { expenseModal, openExpenseModal } from '../lib/expenseModal'
import { formatDate, money, signedMoney } from '../lib/format'

const categories = ref<Category[]>([])
const result = ref<Page<Expense> | null>(null)
const error = ref('')
const confirmId = ref<number | null>(null)

const filter = reactive({ q: '', kind: '' as CategoryKind | '', categoryId: '' as number | '', from: '', to: '' })
const page = ref(0)
const size = 25

const query = computed<ExpenseQuery>(() => ({
  q: filter.q || undefined,
  kind: filter.kind || undefined,
  categoryId: filter.categoryId === '' ? undefined : filter.categoryId,
  from: filter.from || undefined,
  to: filter.to || undefined,
  page: page.value,
  size,
}))

// Net sum: income counts positive, expenses negative
const pageSum = computed(() =>
  result.value?.content.reduce((sum, e) => sum + (e.kind === 'INCOME' ? e.amount : -e.amount), 0) ?? 0)
const hasFilter = computed(() => !!(filter.q || filter.kind || filter.categoryId !== '' || filter.from || filter.to))
const categoryOptions = computed(() => categories.value.filter((c) => !filter.kind || c.kind === filter.kind))

// A category of the other kind cannot match anymore
watch(() => filter.kind, () => {
  if (filter.categoryId !== '' && !categoryOptions.value.some((c) => c.id === filter.categoryId)) filter.categoryId = ''
})

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
  Object.assign(filter, { q: '', kind: '', categoryId: '', from: '', to: '' })
}
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <h1>Buchungen</h1>
      <div class="row">
        <button @click="exportCsv">⬇ CSV-Export</button>
        <button class="primary" @click="openExpenseModal()">＋ Erfassen</button>
      </div>
    </div>

    <div class="card filters">
      <label class="field grow">
        Suche
        <input v-model="filter.q" type="search" placeholder="Beschreibung…" />
      </label>
      <label class="field">
        Typ
        <select v-model="filter.kind">
          <option value="">Alle</option>
          <option value="EXPENSE">Ausgaben</option>
          <option value="INCOME">Einnahmen</option>
        </select>
      </label>
      <label class="field">
        Kategorie
        <select v-model="filter.categoryId">
          <option value="">Alle</option>
          <option v-for="c in categoryOptions" :key="c.id" :value="c.id">{{ c.name }}</option>
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
              <td>
                {{ e.description }}
                <span v-if="e.recurringId" class="recurring" title="Aus wiederkehrender Buchung">🔁</span>
              </td>
              <td><span class="chip"><span class="dot" :style="{ background: e.categoryColor }" />{{ e.categoryName }}</span></td>
              <td class="num" :class="{ income: e.kind === 'INCOME' }">
                <strong>{{ signedMoney(e.amount, e.kind === 'INCOME') }}</strong>
              </td>
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
        <template v-if="hasFilter">Keine Buchungen für diese Filter gefunden.</template>
        <template v-else>
          <p>Noch keine Buchungen erfasst.</p>
          <button class="primary" @click="openExpenseModal()">＋ Buchung erfassen oder Kontoauszug importieren</button>
        </template>
      </div>

      <div v-if="result && result.totalElements > 0" class="footer row">
        <span class="muted small">
          {{ result.totalElements }} Einträge · Saldo dieser Seite:
          <strong :class="{ income: pageSum > 0 }">{{ money(pageSum) }}</strong>
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
.recurring { font-size: 0.8rem; margin-left: 4px; }

.footer { justify-content: space-between; margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--border); }
</style>
