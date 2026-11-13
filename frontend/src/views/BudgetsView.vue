<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import BudgetBar from '../components/BudgetBar.vue'
import { api, type BudgetOverview, type BudgetStatus } from '../lib/api'
import { expenseModal } from '../lib/expenseModal'
import { money, parseAmount, periodLabel, shift, today } from '../lib/format'

const date = ref(today())
const overview = ref<BudgetOverview | null>(null)
const error = ref('')
// Budget inputs by category id, as typed by the user
const drafts = ref<Record<number, string>>({})
const savingId = ref<number | null>(null)

async function load() {
  error.value = ''
  try {
    overview.value = await api.budgets(date.value)
    drafts.value = Object.fromEntries(overview.value.categories.map((c) => [c.categoryId, format(c.budget)]))
  } catch (e) {
    error.value = (e as Error).message
  }
}

watch([date, () => expenseModal.version], load, { immediate: true })

const label = computed(() => (overview.value ? periodLabel('MONTH', overview.value.start, overview.value.end) : ''))
const isCurrent = computed(() => overview.value !== null && overview.value.elapsedPercent > 0 && overview.value.elapsedPercent < 100)
const totalPercent = computed(() => {
  const o = overview.value
  return o && o.totalBudget > 0 ? (o.totalSpent * 100) / o.totalBudget : 0
})
const overCount = computed(() => overview.value?.categories.filter((c) => c.remaining !== null && c.remaining < 0).length ?? 0)

function format(value: number | null) {
  return value === null ? '' : String(value).replace('.', ',')
}

async function save(status: BudgetStatus) {
  const text = drafts.value[status.categoryId]?.trim() ?? ''
  const value = text === '' ? null : Math.round(parseAmount(text) * 100) / 100
  if (value !== null && !(value > 0)) {
    error.value = `Ungültiger Betrag für ${status.name}`
    return
  }
  if (value === status.budget) return
  savingId.value = status.categoryId
  error.value = ''
  try {
    await api.setBudget(status.categoryId, value)
    await load()
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    savingId.value = null
  }
}

/** Expected spending by today if the budget is used evenly over the month */
function onTrack(status: BudgetStatus) {
  if (!overview.value || status.budget === null || status.percent === null) return null
  return status.percent <= overview.value.elapsedPercent
}
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div>
        <h1>Budgets</h1>
        <div class="muted">{{ label }}</div>
      </div>
      <div class="row nav">
        <button class="ghost" title="Vormonat" @click="date = shift(date, 'MONTH', -1)">‹</button>
        <button :disabled="isCurrent" @click="date = today()">Heute</button>
        <button class="ghost" title="Nächster Monat" @click="date = shift(date, 'MONTH', 1)">›</button>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <template v-if="overview">
      <section v-if="overview.totalBudget > 0" class="card total">
        <div class="total-line">
          <div>
            <div class="muted small">Ausgegeben von budgetierten Kategorien</div>
            <div class="big">{{ money(overview.totalSpent) }} <span class="muted">/ {{ money(overview.totalBudget) }}</span></div>
          </div>
          <div class="right">
            <div class="muted small">{{ overview.totalBudget - overview.totalSpent >= 0 ? 'Noch verfügbar' : 'Überschritten um' }}</div>
            <div class="big" :class="overview.totalBudget - overview.totalSpent >= 0 ? 'income' : 'over'">
              {{ money(Math.abs(overview.totalBudget - overview.totalSpent)) }}
            </div>
          </div>
        </div>
        <BudgetBar :percent="totalPercent" color="var(--accent)" :elapsed-percent="overview.elapsedPercent" />
        <p class="small muted hint">
          Der Strich markiert, wie viel vom Monat vergangen ist.
          <template v-if="overCount"> · <strong class="over">{{ overCount }} Budget(s) überschritten</strong></template>
        </p>
      </section>
      <p v-else class="card muted intro">
        Lege für Ausgabe-Kategorien ein monatliches Limit fest. Ab 80 % wird der Balken gelb, über 100 % rot.
      </p>

      <section class="card">
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>Kategorie</th><th class="progress-col">Verbrauch</th><th class="num">Ausgegeben</th><th class="num">Budget / Monat</th></tr>
            </thead>
            <tbody>
              <tr v-for="c in overview.categories" :key="c.categoryId">
                <td><span class="chip"><span class="dot" :style="{ background: c.color }" />{{ c.name }}</span></td>
                <td class="progress-col">
                  <template v-if="c.percent !== null">
                    <BudgetBar :percent="c.percent" :color="c.color" :elapsed-percent="overview.elapsedPercent" />
                    <div class="small muted">
                      {{ c.percent.toLocaleString('de-DE') }} %
                      <template v-if="c.remaining! >= 0"> · noch {{ money(c.remaining!) }}</template>
                      <strong v-else class="over"> · {{ money(-c.remaining!) }} drüber</strong>
                      <template v-if="isCurrent && onTrack(c) === false && c.remaining! >= 0"> · schneller als geplant</template>
                    </div>
                  </template>
                  <span v-else class="small muted">Kein Budget</span>
                </td>
                <td class="num">{{ money(c.spent) }}</td>
                <td class="num">
                  <form class="budget-input" @submit.prevent="save(c)">
                    <input v-model="drafts[c.categoryId]" inputmode="decimal" placeholder="—" :disabled="savingId === c.categoryId"
                           :aria-label="`Budget für ${c.name}`" @blur="save(c)" />
                    <span class="muted">€</span>
                  </form>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="small muted hint">Betrag eintragen und mit Enter bestätigen. Leeres Feld entfernt das Budget.</p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.nav button.ghost { font-size: 1.3rem; line-height: 1; padding: 4px 12px; }
.total { display: flex; flex-direction: column; gap: 12px; }
.total-line { display: flex; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.right { text-align: right; }
.big { font-size: 1.5rem; font-weight: 700; font-variant-numeric: tabular-nums; }
.big .muted { font-size: 1rem; font-weight: 500; }
.hint { margin: 10px 0 0; }
.intro { margin: 0; }
.over { color: var(--danger); }
.progress-col { min-width: 200px; width: 40%; }
.progress-col .small { margin-top: 4px; }
.budget-input { display: inline-flex; align-items: center; gap: 6px; }
.budget-input input { width: 110px; text-align: right; }
</style>
