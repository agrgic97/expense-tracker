<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type Category } from '../lib/api'
import { closeExpenseModal, expenseModal } from '../lib/expenseModal'
import { today } from '../lib/format'

const editing = expenseModal.expense
const categories = ref<Category[]>([])
const saving = ref(false)
const error = ref('')

const form = reactive({
  amount: editing ? String(editing.amount).replace('.', ',') : '',
  date: editing?.date ?? today(),
  description: editing?.description ?? '',
  categoryId: editing?.categoryId ?? (null as number | null),
})

// Accepts "1.234,56", "12,50" and "12.50"
const amountValue = computed(() => {
  const raw = form.amount.trim()
  return Number(raw.includes(',') ? raw.replace(/\./g, '').replace(',', '.') : raw)
})
const valid = computed(() =>
  amountValue.value > 0 && form.date !== '' && form.description.trim() !== '' && form.categoryId !== null,
)

onMounted(async () => {
  try {
    categories.value = await api.categories()
    if (form.categoryId === null && categories.value.length) form.categoryId = categories.value[0].id
  } catch (e) {
    error.value = (e as Error).message
  }
})

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
      <h2>{{ editing ? 'Ausgabe bearbeiten' : 'Neue Ausgabe' }}</h2>
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
          <input v-model="form.description" maxlength="255" placeholder="z. B. Wocheneinkauf" required />
        </label>
        <label class="field wide">
          Kategorie
          <select v-model="form.categoryId" required>
            <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
        </label>
      </div>
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
.fields { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 16px; }
.wide { grid-column: 1 / -1; }
.actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
</style>
