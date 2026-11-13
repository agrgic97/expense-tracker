<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type Category, type CategoryKind, type Recurrence, type RecurringExpense } from '../lib/api'
import { expenseModal } from '../lib/expenseModal'
import { formatDate, money, parseAmount, recurrenceNames, signedMoney, today } from '../lib/format'

const items = ref<RecurringExpense[]>([])
const categories = ref<Category[]>([])
const error = ref('')
const saving = ref(false)
const confirmId = ref<number | null>(null)
const showForm = ref(false)
const editingId = ref<number | null>(null)

const empty = () => ({
  kind: 'EXPENSE' as CategoryKind,
  amount: '',
  description: '',
  categoryId: null as number | null,
  recurrence: 'MONTHLY' as Recurrence,
  startDate: today(),
  endDate: '',
  active: true,
})
const form = reactive(empty())

const options = computed(() => categories.value.filter((c) => c.kind === form.kind))
const amountValue = computed(() => parseAmount(form.amount))
const valid = computed(() =>
  amountValue.value > 0 && form.description.trim() !== '' && form.categoryId !== null && form.startDate !== ''
  && (form.endDate === '' || form.endDate >= form.startDate))
const startsInPast = computed(() => editingId.value === null && form.startDate !== '' && form.startDate < today())

/** Approximate monthly amount of active templates, as a rough fixed-cost overview */
const perMonth = computed(() => {
  const factor: Record<Recurrence, number> = { WEEKLY: 52 / 12, MONTHLY: 1, QUARTERLY: 1 / 3, YEARLY: 1 / 12 }
  const active = items.value.filter((r) => r.active && r.nextDate !== null)
  const sum = (kind: CategoryKind) =>
    active.filter((r) => r.kind === kind).reduce((total, r) => total + r.amount * factor[r.recurrence], 0)
  return { expenses: sum('EXPENSE'), income: sum('INCOME') }
})

async function load() {
  error.value = ''
  try {
    ;[items.value, categories.value] = await Promise.all([api.recurring(), api.categories()])
  } catch (e) {
    error.value = (e as Error).message
  }
}

onMounted(load)

function openNew() {
  Object.assign(form, empty())
  form.categoryId = options.value[0]?.id ?? null
  editingId.value = null
  showForm.value = true
}

function edit(r: RecurringExpense) {
  Object.assign(form, {
    kind: r.kind,
    amount: String(r.amount).replace('.', ','),
    description: r.description,
    categoryId: r.categoryId,
    recurrence: r.recurrence,
    startDate: r.startDate,
    endDate: r.endDate ?? '',
    active: r.active,
  })
  editingId.value = r.id
  showForm.value = true
}

function switchKind(kind: CategoryKind) {
  form.kind = kind
  if (!options.value.some((c) => c.id === form.categoryId)) form.categoryId = options.value[0]?.id ?? null
}

async function save() {
  if (!valid.value) return
  saving.value = true
  error.value = ''
  const body = {
    amount: Math.round(amountValue.value * 100) / 100,
    description: form.description.trim(),
    categoryId: form.categoryId!,
    recurrence: form.recurrence,
    startDate: form.startDate,
    endDate: form.endDate || null,
    active: form.active,
  }
  try {
    if (editingId.value === null) await api.createRecurring(body)
    else await api.updateRecurring(editingId.value, body)
    showForm.value = false
    // Saving may have booked due entries, so other views reload as well
    expenseModal.version++
    await load()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    saving.value = false
  }
}

async function toggleActive(r: RecurringExpense) {
  try {
    await api.updateRecurring(r.id, { ...r, active: !r.active })
    expenseModal.version++
    await load()
  } catch (e) {
    error.value = (e as Error).message
  }
}

async function remove(r: RecurringExpense) {
  if (confirmId.value !== r.id) {
    confirmId.value = r.id
    return
  }
  try {
    await api.deleteRecurring(r.id)
    confirmId.value = null
    await load()
  } catch (e) {
    error.value = (e as Error).message
  }
}

function status(r: RecurringExpense) {
  if (r.nextDate === null) return 'Beendet'
  if (!r.active) return 'Pausiert'
  return `Nächste: ${formatDate(r.nextDate)}`
}
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div>
        <h1>Wiederkehrend</h1>
        <div class="muted">Miete, Abos, Gehalt – wird am Fälligkeitstag automatisch gebucht</div>
      </div>
      <button class="primary" @click="openNew">＋ Neue Vorlage</button>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <form v-if="showForm" class="card" @submit.prevent="save">
      <div class="form-head">
        <h2>{{ editingId === null ? 'Neue wiederkehrende Buchung' : 'Wiederkehrende Buchung bearbeiten' }}</h2>
        <div class="segmented">
          <button type="button" :class="{ active: form.kind === 'EXPENSE' }" @click="switchKind('EXPENSE')">Ausgabe</button>
          <button type="button" :class="{ active: form.kind === 'INCOME' }" @click="switchKind('INCOME')">Einnahme</button>
        </div>
      </div>
      <div class="fields">
        <label class="field wide">
          Beschreibung
          <input v-model="form.description" maxlength="255" required placeholder="z. B. Miete, Netflix, Gehalt" />
        </label>
        <label class="field">
          Betrag (€)
          <input v-model="form.amount" inputmode="decimal" placeholder="0,00" required />
        </label>
        <label class="field">
          Kategorie
          <select v-model="form.categoryId" required>
            <option v-for="c in options" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </label>
        <label class="field">
          Rhythmus
          <select v-model="form.recurrence">
            <option v-for="(name, key) in recurrenceNames" :key="key" :value="key">{{ name }}</option>
          </select>
        </label>
        <label class="field">
          Erste Buchung am
          <input v-model="form.startDate" type="date" required />
        </label>
        <label class="field">
          Endet am (optional)
          <input v-model="form.endDate" type="date" :min="form.startDate" />
        </label>
        <label class="check">
          <input v-model="form.active" type="checkbox" /> Aktiv
        </label>
      </div>
      <p v-if="startsInPast" class="small muted">
        Das Startdatum liegt in der Vergangenheit: Alle Termine bis heute werden beim Speichern nachgebucht.
      </p>
      <div class="form-actions">
        <button type="button" @click="showForm = false">Abbrechen</button>
        <button type="submit" class="primary" :disabled="!valid || saving">{{ saving ? 'Speichern…' : 'Speichern' }}</button>
      </div>
    </form>

    <div v-if="items.length" class="grid grid-kpi">
      <div class="card kpi">
        <div class="muted small">Fixkosten pro Monat</div>
        <div class="value">{{ money(perMonth.expenses) }}</div>
      </div>
      <div class="card kpi">
        <div class="muted small">Feste Einnahmen pro Monat</div>
        <div class="value income">{{ money(perMonth.income) }}</div>
      </div>
    </div>

    <section class="card">
      <div v-if="items.length" class="table-wrap">
        <table>
          <thead>
            <tr><th>Beschreibung</th><th>Kategorie</th><th>Rhythmus</th><th>Status</th><th class="num">Betrag</th><th /></tr>
          </thead>
          <tbody>
            <tr v-for="r in items" :key="r.id" :class="{ inactive: !r.active || r.nextDate === null }">
              <td>{{ r.description }}</td>
              <td><span class="chip"><span class="dot" :style="{ background: r.categoryColor }" />{{ r.categoryName }}</span></td>
              <td class="muted">{{ recurrenceNames[r.recurrence] }}</td>
              <td class="small">{{ status(r) }}<span v-if="r.endDate" class="muted"> · bis {{ formatDate(r.endDate) }}</span></td>
              <td class="num" :class="{ income: r.kind === 'INCOME' }"><strong>{{ signedMoney(r.amount, r.kind === 'INCOME') }}</strong></td>
              <td class="actions">
                <button v-if="r.nextDate !== null" class="ghost" :title="r.active ? 'Pausieren' : 'Fortsetzen'" @click="toggleActive(r)">
                  {{ r.active ? '⏸' : '▶' }}
                </button>
                <button class="ghost" title="Bearbeiten" @click="edit(r)">✏️</button>
                <button class="ghost danger" :title="confirmId === r.id ? 'Wirklich löschen?' : 'Löschen'"
                        @click="remove(r)" @blur="confirmId = null">
                  {{ confirmId === r.id ? 'Löschen?' : '🗑' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="empty">
        Noch keine wiederkehrenden Buchungen. Lege z. B. deine Miete oder dein Gehalt an.
      </div>
      <p v-if="items.length" class="small muted note">
        Löschen entfernt nur die Vorlage – bereits gebuchte Einträge bleiben erhalten. Beim Fortsetzen nach einer Pause
        werden verpasste Termine nicht nachgebucht.
      </p>
    </section>
  </div>
</template>

<style scoped>
.form-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
.form-head h2 { margin: 0; }
.fields { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 14px; margin-bottom: 12px; }
.wide { grid-column: 1 / -1; }
.check { display: flex; align-items: center; gap: 8px; align-self: end; padding-bottom: 8px; }
.form-actions { display: flex; justify-content: flex-end; gap: 8px; }
td.actions { white-space: nowrap; text-align: right; width: 1%; }
.inactive td { opacity: 0.55; }
.kpi .value { font-size: 1.5rem; font-weight: 700; font-variant-numeric: tabular-nums; }
.note { margin: 14px 0 0; }
</style>
