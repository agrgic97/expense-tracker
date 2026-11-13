<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { api, type Category, type CategoryKind } from '../lib/api'
import { closeExpenseModal, expenseModal } from '../lib/expenseModal'
import { parseAmount, today } from '../lib/format'

const editing = expenseModal.expense
const categories = ref<Category[]>([])
const saving = ref(false)
const error = ref('')
// Once the user picks a category, rule suggestions no longer overwrite it
const categoryTouched = ref(editing !== null)

const form = reactive({
  kind: expenseModal.kind as CategoryKind,
  amount: editing ? String(editing.amount).replace('.', ',') : '',
  date: editing?.date ?? today(),
  description: editing?.description ?? '',
  categoryId: editing?.categoryId ?? (null as number | null),
})

const isIncome = computed(() => form.kind === 'INCOME')
const options = computed(() => categories.value.filter((c) => c.kind === form.kind))
const amountValue = computed(() => parseAmount(form.amount))
const valid = computed(() =>
  amountValue.value > 0 && form.date !== '' && form.description.trim() !== '' && form.categoryId !== null,
)

function defaultCategory() {
  if (!options.value.some((c) => c.id === form.categoryId)) form.categoryId = options.value[0]?.id ?? null
}

onMounted(async () => {
  try {
    categories.value = await api.categories()
    defaultCategory()
  } catch (e) {
    error.value = (e as Error).message
  }
})

watch(() => form.kind, () => {
  categoryTouched.value = false
  defaultCategory()
  suggest()
})

let debounce: ReturnType<typeof setTimeout> | undefined
watch(() => form.description, () => {
  clearTimeout(debounce)
  debounce = setTimeout(suggest, 300)
})

async function suggest() {
  const description = form.description.trim()
  if (categoryTouched.value || description.length < 3) return
  try {
    const { categoryId } = await api.suggestCategory(description, form.kind)
    if (categoryId !== null && !categoryTouched.value) form.categoryId = categoryId
  } catch {
    // a suggestion is optional
  }
}

async function save() {
  if (!valid.value) return
  saving.value = true
  error.value = ''
  const body = {
    amount: Math.round(amountValue.value * 100) / 100,
    date: form.date,
    description: form.description.trim(),
    categoryId: form.categoryId!,
  }
  try {
    if (editing) await api.updateExpense(editing.id, body)
    else await api.createExpense(body)
    closeExpenseModal(true)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="backdrop" @mousedown.self="closeExpenseModal(false)" @keydown.esc="closeExpenseModal(false)">
    <form class="dialog card" @submit.prevent="save">
      <div class="head">
        <h2>{{ editing ? 'Buchung bearbeiten' : isIncome ? 'Neue Einnahme' : 'Neue Ausgabe' }}</h2>
        <div class="segmented">
          <button type="button" :class="{ active: !isIncome }" @click="form.kind = 'EXPENSE'">Ausgabe</button>
          <button type="button" :class="{ active: isIncome }" @click="form.kind = 'INCOME'">Einnahme</button>
        </div>
      </div>
      <div class="fields">
        <label class="field">
          Betrag (€)
          <input v-model="form.amount" inputmode="decimal" placeholder="0,00" autofocus required />
        </label>
        <label class="field">
          Datum
          <input v-model="form.date" type="date" required />
        </label>
        <label class="field wide">
          Beschreibung
          <input v-model="form.description" maxlength="255" required
                 :placeholder="isIncome ? 'z. B. Gehalt November' : 'z. B. Wocheneinkauf'" />
        </label>
        <label class="field wide">
          Kategorie
          <select v-model="form.categoryId" required @change="categoryTouched = true">
            <option v-for="c in options" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
          <span v-if="!options.length && categories.length" class="small">
            Noch keine {{ isIncome ? 'Einnahme' : 'Ausgabe' }}-Kategorie – lege eine unter „Kategorien“ an.
          </span>
        </label>
      </div>
      <p v-if="editing?.recurringId" class="small muted">
        🔁 Automatisch aus einer wiederkehrenden Buchung angelegt. Änderungen gelten nur für diesen Eintrag.
      </p>
      <p v-if="error" class="error">{{ error }}</p>
      <div class="actions">
        <button type="button" @click="closeExpenseModal(false)">Abbrechen</button>
        <button type="submit" class="primary" :disabled="!valid || saving">
          {{ saving ? 'Speichern…' : 'Speichern' }}
        </button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.backdrop {
  position: fixed;
  inset: 0;
  background: rgba(10, 14, 24, 0.5);
  display: grid;
  place-items: center;
  padding: 16px;
  z-index: 50;
}
.dialog { width: min(480px, 100%); }
.head { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 14px; }
.head h2 { margin: 0; }
.fields { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 16px; }
.wide { grid-column: 1 / -1; }
.actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
@media (max-width: 760px) {
  .backdrop { place-items: end center; padding: 0; }
  .dialog { border-radius: var(--radius) var(--radius) 0 0; padding-bottom: calc(20px + env(safe-area-inset-bottom)); }
}
</style>
