<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, type Category } from '../lib/api'
import { closeExpenseModal, expenseModal } from '../lib/expenseModal'
import { readStatements, type FileSummary, type ReviewItem } from '../lib/statementImport'
import ImportReview from './ImportReview.vue'
import ManualEntryForm from './ManualEntryForm.vue'

/**
 * The one place to add entries: type one in by hand, or drop bank statements (PDF or CSV), review what was read
 * and confirm. Editing an existing entry shows only the form.
 */
const editing = expenseModal.expense
const categories = ref<Category[]>([])
const step = ref<'entry' | 'reading' | 'review' | 'done'>('entry')
const error = ref('')
const dragging = ref(false)
const items = ref<ReviewItem[]>([])
const files = ref<FileSummary[]>([])
const imported = ref(0)
const fileInput = ref<HTMLInputElement | null>(null)

onMounted(async () => {
  try {
    categories.value = await api.categories()
  } catch (e) {
    error.value = (e as Error).message
  }
})

async function read(list: FileList | null | undefined) {
  const selected = Array.from(list ?? [])
  if (!selected.length) return
  error.value = ''
  step.value = 'reading'
  try {
    const result = await readStatements(selected, categories.value)
    items.value = result.items
    files.value = result.files
    step.value = 'review'
  } catch (e) {
    error.value = (e as Error).message
    step.value = 'entry'
  } finally {
    if (fileInput.value) fileInput.value.value = ''
  }
}

function onDrop(event: DragEvent) {
  dragging.value = false
  read(event.dataTransfer?.files)
}

function onImported(count: number) {
  imported.value = count
  step.value = 'done'
  expenseModal.version++
}

const title = () => {
  if (editing) return 'Buchung bearbeiten'
  if (step.value === 'review') return 'Kontoauszüge prüfen'
  return 'Erfassen'
}
</script>

<template>
  <div class="backdrop" @mousedown.self="closeExpenseModal(false)" @keydown.esc="closeExpenseModal(false)">
    <div class="dialog card" :class="{ wide: step === 'review' }" role="dialog" :aria-label="title()">
      <div class="head">
        <h2>{{ title() }}</h2>
        <button class="ghost close" title="Schließen" @click="closeExpenseModal(false)">✕</button>
      </div>

      <template v-if="step === 'entry'">
        <ManualEntryForm :categories="categories" :expense="editing" :kind="expenseModal.kind"
                         @saved="closeExpenseModal(true)" @cancel="closeExpenseModal(false)" />

        <template v-if="!editing">
          <div class="divider"><span>oder Kontoauszüge importieren</span></div>
          <label class="dropzone" :class="{ dragging }" @dragover.prevent="dragging = true"
                 @dragleave="dragging = false" @drop.prevent="onDrop">
            <input ref="fileInput" type="file" multiple accept=".pdf,.csv,application/pdf,text/csv"
                   @change="read(($event.target as HTMLInputElement).files)" />
            <span class="icon">📄</span>
            <strong>PDF-Kontoauszüge hierher ziehen oder auswählen</strong>
            <span class="small muted">Mehrere Dateien möglich · auch CSV-Exporte · du prüfst alles vor dem Übernehmen</span>
          </label>
          <p v-if="error" class="error">{{ error }}</p>
        </template>
      </template>

      <div v-else-if="step === 'reading'" class="status">
        <div class="spinner" aria-hidden="true" />
        <p>Kontoauszüge werden gelesen…</p>
      </div>

      <ImportReview v-else-if="step === 'review'" :items="items" :files="files" :categories="categories"
                    @imported="onImported" @back="step = 'entry'" />

      <div v-else class="status">
        <p class="notice">✅ {{ imported }} Buchung(en) übernommen.</p>
        <div class="row">
          <button @click="step = 'entry'">Weitere erfassen</button>
          <RouterLink to="/expenses" class="btn primary" @click="closeExpenseModal(false)">Zu den Buchungen</RouterLink>
        </div>
      </div>
    </div>
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
.dialog { width: min(500px, 100%); max-height: calc(100vh - 32px); overflow-y: auto; transition: width 0.2s; }
.dialog.wide { width: min(1040px, 100%); }
.head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.head h2 { margin: 0; }
.close { font-size: 1rem; }
.divider { display: flex; align-items: center; gap: 12px; margin: 22px 0 14px; color: var(--muted); font-size: 0.85rem; }
.divider::before, .divider::after { content: ''; flex: 1; border-top: 1px solid var(--border); }
.dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  text-align: center;
  padding: 22px 16px;
  border: 2px dashed var(--border);
  border-radius: var(--radius);
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.dropzone:hover, .dropzone.dragging, .dropzone:focus-within { border-color: var(--accent); background: var(--accent-soft); }
.dropzone input { position: absolute; opacity: 0; width: 1px; height: 1px; }
.dropzone .icon { font-size: 1.8rem; }
.status { display: flex; flex-direction: column; align-items: center; gap: 14px; padding: 24px 0; }
.status p { margin: 0; }
.spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 760px) {
  .backdrop { place-items: end center; padding: 0; }
  .dialog { border-radius: var(--radius) var(--radius) 0 0; max-height: 92vh; padding-bottom: calc(20px + env(safe-area-inset-bottom)); }
  .dialog.wide { max-height: 100vh; height: 100vh; border-radius: 0; }
}
</style>
