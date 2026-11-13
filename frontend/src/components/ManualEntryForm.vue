<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { api, type Category, type CategoryKind, type Expense } from '../lib/api'
import { parseAmount, today } from '../lib/format'

const props = defineProps<{ categories: Category[]; expense: Expense | null; kind: CategoryKind }>()
const emit = defineEmits<{ saved: []; cancel: [] }>()

const saving = ref(false)
const error = ref('')
// Once the user picks a category, rule suggestions no longer overwrite it
const categoryTouched = ref(props.expense !== null)

const form = reactive({
  kind: props.kind,
  amount: props.expense ? String(props.expense.amount).replace('.', ',') : '',
  date: props.expense?.date ?? today(),
  description: props.expense?.description ?? '',
  categoryId: props.expense?.categoryId ?? (null as number | null),
})

const isIncome = computed(() => form.kind === 'INCOME')
const options = computed(() => props.categories.filter((c) => c.kind === form.kind))
const amountValue = computed(() => parseAmount(form.amount))
const valid = computed(() =>
  amountValue.value > 0 && form.date !== '' && form.description.trim() !== '' && form.categoryId !== null,
)

function defaultCategory() {
  if (!options.value.some((c) => c.id === form.categoryId)) form.categoryId = options.value[0]?.id ?? null
}

watch(() => props.categories, defaultCategory, { immediate: true })

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
    if (props.expense) await api.updateExpense(props.expense.id, body)
    else await api.createExpense(body)
    emit('saved')
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <form @submit.prevent="save">
    <div class="segmented kind">
      <button type="button" :class="{ active: !isIncome }" @click="form.kind = 'EXPENSE'">Ausgabe</button>
      <button type="button" :class="{ active: isIncome }" @click="form.kind = 'INCOME'">Einnahme</button>
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
    <p v-if="expense?.recurringId" class="small muted">
      🔁 Automatisch aus einer wiederkehrenden Buchung angelegt. Änderungen gelten nur für diesen Eintrag.
    </p>
    <p v-if="error" class="error">{{ error }}</p>
    <div class="actions">
      <button type="button" @click="emit('cancel')">Abbrechen</button>
      <button type="submit" class="primary" :disabled="!valid || saving">
        {{ saving ? 'Speichern…' : expense ? 'Speichern' : isIncome ? 'Einnahme speichern' : 'Ausgabe speichern' }}
      </button>
    </div>
  </form>
</template>

<style scoped>
.kind { margin-bottom: 14px; }
.fields { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; margin-bottom: 16px; }
.wide { grid-column: 1 / -1; }
.actions { display: flex; justify-content: flex-end; gap: 8px; }
</style>
