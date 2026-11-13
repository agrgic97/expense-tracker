<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type Category, type CategoryKind, type StatementRow } from '../lib/api'
import { decode, detectDelimiter, findHeaderRow, guessColumns, parseCsv, parseStatementDate } from '../lib/csv'
import { expenseModal } from '../lib/expenseModal'
import { formatDate, money, parseAmount, signedMoney } from '../lib/format'

const MAX_ROWS = 5000

interface PreviewItem extends StatementRow {
  kind: CategoryKind
  categoryId: number | null
  duplicate: boolean
  selected: boolean
}

const categories = ref<Category[]>([])
const error = ref('')
const busy = ref(false)
const fileName = ref('')
const header = ref<string[]>([])
const dataRows = ref<string[][]>([])
const mapping = reactive({ date: -1, amount: -1, description: -1, extra: -1, invert: false })
const preview = ref<PreviewItem[] | null>(null)
const skipped = ref(0)
const imported = ref<number | null>(null)

const mappingValid = computed(() => mapping.date >= 0 && mapping.amount >= 0 && mapping.description >= 0)
const selected = computed(() => preview.value?.filter((r) => r.selected) ?? [])
const duplicates = computed(() => preview.value?.filter((r) => r.duplicate).length ?? 0)
const selectedSum = computed(() =>
  selected.value.reduce((sum, r) => sum + (r.kind === 'INCOME' ? r.amount : -r.amount), 0))

onMounted(async () => {
  try {
    categories.value = await api.categories()
  } catch (e) {
    error.value = (e as Error).message
  }
})

function options(kind: CategoryKind) {
  return categories.value.filter((c) => c.kind === kind)
}

/** Category for rows no rule matches: "Sonstiges"/"Sonstige Einnahmen" if present, else the first one. */
function fallbackCategory(kind: CategoryKind): number | null {
  const list = options(kind)
  return (list.find((c) => c.name.toLowerCase().startsWith('sonstig')) ?? list[0])?.id ?? null
}

async function onFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  reset()
  fileName.value = file.name
  try {
    const text = decode(await file.arrayBuffer())
    const rows = parseCsv(text, detectDelimiter(text))
    const headerIndex = findHeaderRow(rows)
    header.value = rows[headerIndex] ?? []
    dataRows.value = rows.slice(headerIndex + 1).filter((r) => r.length >= header.value.length - 1)
    if (!dataRows.value.length) throw new Error('In der Datei wurden keine Buchungen gefunden.')
    Object.assign(mapping, guessColumns(header.value), { invert: false })
  } catch (e) {
    error.value = (e as Error).message || 'Die Datei konnte nicht gelesen werden.'
  }
}

function reset() {
  error.value = ''
  header.value = []
  dataRows.value = []
  preview.value = null
  imported.value = null
  skipped.value = 0
}

function statementRows(): StatementRow[] {
  const rows: StatementRow[] = []
  skipped.value = 0
  for (const row of dataRows.value) {
    const date = parseStatementDate(row[mapping.date] ?? '')
    let amount = parseAmount(row[mapping.amount] ?? '')
    const parts = [row[mapping.description], mapping.extra >= 0 ? row[mapping.extra] : '']
      .map((p) => (p ?? '').replace(/\s+/g, ' ').trim())
      .filter((p, i, all) => p !== '' && all.indexOf(p) === i)
    const description = parts.join(' · ').slice(0, 255)
    if (!date || !Number.isFinite(amount) || amount === 0 || !description) {
      skipped.value++
      continue
    }
    if (mapping.invert) amount = -amount
    rows.push({ date, amount: Math.round(amount * 100) / 100, description })
  }
  return rows
}

async function loadPreview() {
  error.value = ''
  const rows = statementRows()
  if (!rows.length) {
    error.value = 'Keine Zeile ließ sich lesen. Stimmt die Zuordnung der Spalten?'
    return
  }
  if (rows.length > MAX_ROWS) {
    error.value = `Höchstens ${MAX_ROWS} Buchungen pro Import – bitte die Datei aufteilen.`
    return
  }
  busy.value = true
  try {
    const result = await api.importPreview(rows)
    preview.value = rows.map((row, i) => {
      const kind: CategoryKind = row.amount < 0 ? 'EXPENSE' : 'INCOME'
      return {
        ...row,
        amount: Math.abs(row.amount),
        kind,
        categoryId: result.rows[i].categoryId ?? fallbackCategory(kind),
        duplicate: result.rows[i].duplicate,
        selected: !result.rows[i].duplicate,
      }
    })
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}

function selectAll(value: boolean) {
  preview.value?.forEach((r) => (r.selected = value))
}

async function runImport() {
  const rows = selected.value
  if (rows.some((r) => r.categoryId === null)) {
    error.value = 'Bitte für alle ausgewählten Buchungen eine Kategorie wählen.'
    return
  }
  busy.value = true
  error.value = ''
  try {
    const result = await api.importRows(rows.map((r) => ({
      date: r.date, amount: r.amount, description: r.description, categoryId: r.categoryId!,
    })))
    imported.value = result.imported
    preview.value = null
    expenseModal.version++
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div>
        <h1>CSV-Import</h1>
        <div class="muted">Kontoauszug deiner Bank als CSV hochladen – die Datei wird im Browser gelesen</div>
      </div>
      <RouterLink to="/expenses" class="btn">‹ Zu den Buchungen</RouterLink>
    </div>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="imported !== null" class="notice">
      ✅ {{ imported }} Buchung(en) importiert. <RouterLink to="/expenses" class="link">Ansehen ›</RouterLink>
    </p>

    <section class="card">
      <h2>1. Datei wählen</h2>
      <label class="file">
        <input type="file" accept=".csv,.txt,text/csv" @change="onFile" />
        <span class="btn primary">Datei auswählen…</span>
        <span class="muted small">{{ fileName || 'CSV-Export von Sparkasse, ING, DKB, N26, comdirect …' }}</span>
      </label>
    </section>

    <section v-if="header.length" class="card">
      <h2>2. Spalten zuordnen <span class="muted small">({{ dataRows.length }} Zeilen erkannt)</span></h2>
      <div class="mapping">
        <label class="field">
          Datum
          <select v-model.number="mapping.date">
            <option :value="-1">—</option>
            <option v-for="(h, i) in header" :key="i" :value="i">{{ h || `Spalte ${i + 1}` }}</option>
          </select>
        </label>
        <label class="field">
          Betrag
          <select v-model.number="mapping.amount">
            <option :value="-1">—</option>
            <option v-for="(h, i) in header" :key="i" :value="i">{{ h || `Spalte ${i + 1}` }}</option>
          </select>
        </label>
        <label class="field">
          Beschreibung
          <select v-model.number="mapping.description">
            <option :value="-1">—</option>
            <option v-for="(h, i) in header" :key="i" :value="i">{{ h || `Spalte ${i + 1}` }}</option>
          </select>
        </label>
        <label class="field">
          Zusätzlich (optional)
          <select v-model.number="mapping.extra">
            <option :value="-1">—</option>
            <option v-for="(h, i) in header" :key="i" :value="i">{{ h || `Spalte ${i + 1}` }}</option>
          </select>
        </label>
      </div>
      <label class="check small">
        <input v-model="mapping.invert" type="checkbox" />
        Vorzeichen umkehren (wenn Ausgaben in der Datei positiv stehen, z. B. bei Kreditkarten)
      </label>
      <div class="table-wrap sample">
        <table>
          <thead><tr><th v-for="(h, i) in header" :key="i">{{ h }}</th></tr></thead>
          <tbody>
            <tr v-for="(row, r) in dataRows.slice(0, 3)" :key="r"><td v-for="(cell, i) in row" :key="i">{{ cell }}</td></tr>
          </tbody>
        </table>
      </div>
      <div class="row end">
        <button class="primary" :disabled="!mappingValid || busy" @click="loadPreview">
          {{ busy && !preview ? 'Wird geprüft…' : 'Vorschau anzeigen' }}
        </button>
      </div>
    </section>

    <section v-if="preview" class="card">
      <div class="preview-head">
        <h2>3. Prüfen und importieren</h2>
        <div class="row small">
          <button class="ghost" @click="selectAll(true)">Alle</button>
          <button class="ghost" @click="selectAll(false)">Keine</button>
        </div>
      </div>
      <p class="small muted">
        {{ preview.length }} Buchungen gelesen<template v-if="skipped">, {{ skipped }} Zeile(n) ohne gültiges Datum/Betrag übersprungen</template>.
        <template v-if="duplicates"> {{ duplicates }} sind bereits vorhanden und abgewählt.</template>
        Kategorien kommen aus deinen <RouterLink to="/categories" class="link">Regeln</RouterLink>.
      </p>
      <div class="table-wrap">
        <table>
          <thead><tr><th /><th>Datum</th><th>Beschreibung</th><th>Kategorie</th><th class="num">Betrag</th></tr></thead>
          <tbody>
            <tr v-for="(r, i) in preview" :key="i" :class="{ off: !r.selected }">
              <td><input v-model="r.selected" type="checkbox" :aria-label="`Zeile ${i + 1} importieren`" /></td>
              <td class="nowrap muted">{{ formatDate(r.date) }}</td>
              <td>
                {{ r.description }}
                <span v-if="r.duplicate" class="badge dup" title="Gleiches Datum, gleicher Betrag, gleiche Beschreibung">schon vorhanden</span>
              </td>
              <td>
                <select v-model="r.categoryId" class="category">
                  <option v-for="c in options(r.kind)" :key="c.id" :value="c.id">{{ c.name }}</option>
                </select>
              </td>
              <td class="num" :class="{ income: r.kind === 'INCOME' }">{{ signedMoney(r.amount, r.kind === 'INCOME') }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="row footer">
        <span class="small muted">{{ selected.length }} ausgewählt · Saldo {{ money(selectedSum) }}</span>
        <button class="primary" :disabled="!selected.length || busy" @click="runImport">
          {{ busy ? 'Importiere…' : `${selected.length} Buchung(en) importieren` }}
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.file { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; cursor: pointer; }
.file input { position: absolute; opacity: 0; width: 1px; height: 1px; }
.file:focus-within .btn { outline: 2px solid var(--accent); outline-offset: 1px; }
.mapping { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 14px; margin-bottom: 12px; }
.check { display: flex; align-items: center; gap: 8px; margin-bottom: 14px; }
.sample { margin-bottom: 14px; font-size: 0.82rem; }
.sample td, .sample th { white-space: nowrap; max-width: 260px; overflow: hidden; text-overflow: ellipsis; }
.end { justify-content: flex-end; }
.preview-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.preview-head h2 { margin: 0; }
.nowrap { white-space: nowrap; }
.category { padding: 4px 8px; font-size: 0.88rem; }
.off td { opacity: 0.5; }
.dup { margin-left: 6px; color: var(--warning); background: color-mix(in srgb, var(--warning) 14%, transparent); }
.footer { justify-content: space-between; margin-top: 14px; padding-top: 14px; border-top: 1px solid var(--border); }
</style>
