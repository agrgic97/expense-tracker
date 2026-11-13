<script setup lang="ts">
import { computed, ref } from 'vue'
import { api, type Category, type CategoryKind } from '../lib/api'
import { formatDate, money } from '../lib/format'
import { fallbackCategory, type FileSummary, type ReviewItem } from '../lib/statementImport'

const props = defineProps<{ items: ReviewItem[]; files: FileSummary[]; categories: Category[] }>()
const emit = defineEmits<{ imported: [count: number]; back: [] }>()

const busy = ref(false)
const error = ref('')
const onlyAttention = ref(false)

const selected = computed(() => props.items.filter((i) => i.selected))
const duplicates = computed(() => props.items.filter((i) => i.duplicate).length)
const uncertain = computed(() => props.items.filter((i) => i.uncertain && i.selected).length)
const balance = computed(() => selected.value.reduce((sum, i) => sum + (i.kind === 'INCOME' ? i.amount : -i.amount), 0))
const visible = computed(() => onlyAttention.value ? props.items.filter((i) => i.uncertain || i.duplicate) : props.items)

function options(kind: CategoryKind) {
  return props.categories.filter((c) => c.kind === kind)
}

function toggleKind(item: ReviewItem) {
  item.kind = item.kind === 'EXPENSE' ? 'INCOME' : 'EXPENSE'
  item.categoryId = fallbackCategory(props.categories, item.kind)
  item.uncertain = false
}

function selectAll(value: boolean) {
  props.items.forEach((i) => (i.selected = value))
}

async function confirm() {
  const rows = selected.value
  if (rows.some((r) => r.categoryId === null || !r.description.trim())) {
    error.value = 'Bitte für alle ausgewählten Einträge Beschreibung und Kategorie angeben.'
    return
  }
  busy.value = true
  error.value = ''
  try {
    const result = await api.importRows(rows.map((r) => ({
      date: r.date, amount: r.amount, description: r.description.trim(), categoryId: r.categoryId!,
    })))
    emit('imported', result.imported)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="review">
    <ul class="files">
      <li v-for="f in files" :key="f.name" :class="{ failed: f.error }">
        <span class="file-name">📄 {{ f.name }}</span>
        <span v-if="f.error" class="small">– {{ f.error }}</span>
        <span v-else class="small muted">– {{ f.count }} Einträge</span>
      </li>
    </ul>

    <template v-if="items.length">
      <div class="hints small">
        <span v-if="duplicates" class="hint dup">{{ duplicates }} schon vorhanden – abgewählt</span>
        <span v-if="uncertain" class="hint unsure">
          {{ uncertain }} ohne Vorzeichen im Auszug – bitte prüfen, ob Ausgabe oder Einnahme
        </span>
        <label v-if="duplicates || uncertain" class="check">
          <input v-model="onlyAttention" type="checkbox" /> Nur diese zeigen
        </label>
        <span class="spacer" />
        <button class="ghost small" @click="selectAll(true)">Alle</button>
        <button class="ghost small" @click="selectAll(false)">Keine</button>
      </div>

      <ul class="items">
        <li v-for="(item, i) in visible" :key="i" :class="{ off: !item.selected, unsure: item.uncertain && item.selected }">
          <input v-model="item.selected" type="checkbox" class="pick" :aria-label="`${item.description} übernehmen`" />
          <span class="date muted small">{{ formatDate(item.date) }}</span>
          <input v-model="item.description" class="text" maxlength="255" aria-label="Beschreibung" />
          <span class="badges">
            <span v-if="item.duplicate" class="badge dup" title="Gleiches Datum, gleicher Betrag, gleiche Beschreibung">vorhanden</span>
          </span>
          <select v-model="item.categoryId" class="category" aria-label="Kategorie">
            <option v-if="!options(item.kind).length" :value="null" disabled>
              Keine {{ item.kind === 'INCOME' ? 'Einnahme' : 'Ausgabe' }}-Kategorie angelegt
            </option>
            <option v-for="c in options(item.kind)" :key="c.id" :value="c.id">{{ c.name }}</option>
          </select>
          <button type="button" class="amount num" :class="{ income: item.kind === 'INCOME' }"
                  :title="`Als ${item.kind === 'INCOME' ? 'Ausgabe' : 'Einnahme'} übernehmen`" @click="toggleKind(item)">
            {{ item.kind === 'INCOME' ? '+' : '−' }}{{ money(item.amount) }}
          </button>
        </li>
      </ul>
      <p class="small muted note">
        Tipp: Ein Klick auf den Betrag wechselt zwischen Ausgabe und Einnahme. Kategorien kommen aus deinen Regeln.
      </p>
    </template>
    <p v-else class="empty">In den Dateien wurden keine Buchungen gefunden.</p>

    <p v-if="error" class="error">{{ error }}</p>
    <div class="footer">
      <button type="button" @click="emit('back')">‹ Zurück</button>
      <span class="small muted sum">{{ selected.length }} ausgewählt · Saldo {{ money(balance) }}</span>
      <button class="primary" :disabled="!selected.length || busy" @click="confirm">
        {{ busy ? 'Übernehme…' : `${selected.length} übernehmen` }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.review { display: flex; flex-direction: column; gap: 12px; min-height: 0; }
.files { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: 6px 16px; }
.files .failed { color: var(--danger); }
.file-name { font-weight: 500; }
.hints { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.hint { padding: 3px 10px; border-radius: 99px; }
.hint.dup { color: var(--muted); background: var(--surface-2); }
.hint.unsure { color: var(--warning); background: color-mix(in srgb, var(--warning) 14%, transparent); }
.check { display: flex; align-items: center; gap: 6px; }
.spacer { flex: 1; }
.items { list-style: none; margin: 0; padding: 0; overflow-y: auto; max-height: 55vh; border-top: 1px solid var(--border); }
.items li {
  display: grid;
  grid-template-columns: auto 88px minmax(0, 1fr) auto 170px 120px;
  align-items: center;
  gap: 10px;
  padding: 8px 4px;
  border-bottom: 1px solid var(--border);
}
.items li.off > :not(.pick) { opacity: 0.45; }
.items li.unsure { background: color-mix(in srgb, var(--warning) 8%, transparent); }
.text { padding: 5px 8px; border-color: transparent; background: transparent; }
.text:hover, .text:focus { border-color: var(--border); background: var(--surface); }
.category { padding: 5px 8px; font-size: 0.88rem; }
.amount { justify-content: flex-end; font-weight: 600; padding: 5px 8px; }
.dup { color: var(--muted); background: var(--surface-2); }
.note { margin: 0; }
.footer { display: flex; align-items: center; gap: 12px; padding-top: 12px; border-top: 1px solid var(--border); }
.sum { flex: 1; text-align: right; }

/* Phones: each entry as a small card instead of a table row */
@media (max-width: 760px) {
  .items { max-height: none; }
  .items li {
    grid-template-columns: auto 1fr auto;
    grid-template-areas:
      'pick date amount'
      'text text text'
      'badges category category';
    row-gap: 6px;
  }
  .pick { grid-area: pick; justify-self: start; }
  .date { grid-area: date; }
  .amount { grid-area: amount; }
  .text { grid-area: text; border-color: var(--border); }
  .badges { grid-area: badges; }
  .category { grid-area: category; }
  .sum { display: none; }
  .footer { position: sticky; bottom: 0; background: var(--surface); padding-bottom: env(safe-area-inset-bottom); }
  .footer .primary { margin-left: auto; }
}
</style>
