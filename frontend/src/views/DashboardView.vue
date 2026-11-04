<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Bar, Doughnut } from 'vue-chartjs'
import KpiCard from '../components/KpiCard.vue'
import { api, type PeriodType, type Summary, type Trend } from '../lib/api'
import { moneyAxis } from '../lib/chart'
import { expenseModal, openExpenseModal } from '../lib/expenseModal'
import { breakdownLabel, formatDate, money, periodLabel, periodNames, shift, today, trendLabel } from '../lib/format'

const periods: PeriodType[] = ['WEEK', 'MONTH', 'YEAR']
const period = ref<PeriodType>('MONTH')
const date = ref(today())
const summary = ref<Summary | null>(null)
const trend = ref<Trend | null>(null)
const error = ref('')
const loading = ref(false)

const accent = getComputedStyle(document.documentElement).getPropertyValue('--accent').trim() || '#4f46e5'

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [s, t] = await Promise.all([api.summary(period.value, date.value), api.trend(period.value, date.value, 12)])
    summary.value = s
    trend.value = t
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

watch([period, date, () => expenseModal.version], load, { immediate: true })

const names = computed(() => periodNames[period.value])
const label = computed(() => (summary.value ? periodLabel(period.value, summary.value.start, summary.value.end) : ''))
const isCurrent = computed(() => summary.value?.current ?? false)

const change = computed(() => {
  const value = summary.value?.changePercent
  if (value === null || value === undefined) return null
  return { value, text: `${value > 0 ? '+' : ''}${value.toLocaleString('de-DE')} %`, up: value > 0 }
})

const breakdownData = computed(() => ({
  labels: summary.value?.breakdown.map((b) => breakdownLabel(period.value, b.start)) ?? [],
  datasets: [{
    label: 'Ausgaben',
    data: summary.value?.breakdown.map((b) => b.total) ?? [],
    backgroundColor: accent,
    borderRadius: 6,
    maxBarThickness: 42,
  }],
}))

const categoryData = computed(() => ({
  labels: summary.value?.byCategory.map((c) => c.name) ?? [],
  datasets: [{
    data: summary.value?.byCategory.map((c) => c.total) ?? [],
    backgroundColor: summary.value?.byCategory.map((c) => c.color) ?? [],
    borderWidth: 0,
  }],
}))

const trendData = computed(() => ({
  labels: trend.value?.buckets.map((b) => trendLabel(period.value, b.start)) ?? [],
  datasets: [
    {
      type: 'bar' as const,
      label: 'Summe',
      data: trend.value?.buckets.map((b) => b.total) ?? [],
      backgroundColor: trend.value?.buckets.map((_, i, all) => (i === all.length - 1 ? accent : `${accent}66`)) ?? [],
      borderRadius: 6,
      maxBarThickness: 42,
    },
    {
      type: 'line' as const,
      label: 'Durchschnitt',
      data: trend.value?.buckets.map(() => trend.value!.average) ?? [],
      borderColor: '#f59e0b',
      borderDash: [6, 4],
      borderWidth: 2,
      pointRadius: 0,
    },
  ],
}))

const barOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: { legend: { display: false } },
  scales: { y: moneyAxis, x: { grid: { display: false } } },
}
const trendOptions = { ...barOptions, plugins: { legend: { display: true, position: 'bottom' as const } } }
const doughnutOptions = {
  responsive: true,
  maintainAspectRatio: false,
  cutout: '68%',
  plugins: { legend: { display: false } },
}
</script>

<template>
  <div class="stack">
    <div class="page-header">
      <div>
        <h1>Übersicht</h1>
        <div class="muted">{{ label }}</div>
      </div>
      <div class="row">
        <div class="segmented">
          <button v-for="p in periods" :key="p" :class="{ active: p === period }" @click="period = p">
            {{ periodNames[p].label }}
          </button>
        </div>
        <div class="row nav">
          <button class="ghost" title="Zurück" @click="date = shift(date, period, -1)">‹</button>
          <button :disabled="isCurrent" @click="date = today()">Heute</button>
          <button class="ghost" title="Weiter" @click="date = shift(date, period, 1)">›</button>
        </div>
      </div>
    </div>

    <p v-if="error" class="error">Daten konnten nicht geladen werden: {{ error }}</p>

    <template v-if="summary">
      <div class="grid grid-kpi" :class="{ loading }">
        <KpiCard :label="`Ausgaben ${names.label}`" :value="money(summary.total)">
          <span v-if="change" class="badge" :class="change.up ? 'up' : 'down'">{{ change.text }}</span>
          <span v-if="change">vs. {{ names.previous }}{{ isCurrent ? ' (gleicher Zeitraum)' : '' }}</span>
          <span v-else>Keine Vergleichsdaten</span>
        </KpiCard>
        <KpiCard label="Ø pro Tag" :value="money(summary.averagePerDay)"
                 :hint="isCurrent ? 'bis heute' : `über ${summary.breakdown.length} ${period === 'YEAR' ? 'Monate' : 'Tage'}`" />
        <KpiCard label="Anzahl Ausgaben" :value="String(summary.count)"
                 :hint="`Ø ${money(summary.averagePerExpense)} pro Ausgabe`" />
        <KpiCard v-if="summary.projectedTotal !== null" :label="`Hochrechnung ${names.label}`"
                 :value="money(summary.projectedTotal)" hint="bei gleichbleibendem Tempo" />
        <KpiCard v-else :label="names.previous" :value="money(summary.previousTotal)" hint="Gesamtsumme" />
      </div>

      <div class="grid grid-2">
        <section class="card">
          <h2>Verlauf {{ period === 'YEAR' ? 'pro Monat' : 'pro Tag' }}</h2>
          <div class="chart-box"><Bar :data="breakdownData" :options="barOptions" /></div>
        </section>
        <section class="card">
          <h2>Nach Kategorie</h2>
          <div v-if="summary.byCategory.length" class="chart-box doughnut">
            <Doughnut :data="categoryData" :options="doughnutOptions" />
            <div class="doughnut-center">
              <div class="muted small">Gesamt</div>
              <strong>{{ money(summary.total) }}</strong>
            </div>
          </div>
          <div v-else class="empty">Keine Ausgaben in diesem Zeitraum</div>
        </section>
      </div>

      <div class="grid grid-2">
        <section class="card">
          <h2>{{ names.plural }} im Vergleich <span class="muted small">(letzte 12)</span></h2>
          <div class="chart-box"><Bar :data="trendData as any" :options="trendOptions" /></div>
          <div v-if="trend" class="row small muted trend-stats">
            <span>Ø {{ money(trend.average) }}</span>
            <span>Max {{ money(trend.max) }}</span>
            <span>Min {{ money(trend.min) }}</span>
          </div>
        </section>
        <section class="card">
          <h2>Größte Ausgaben</h2>
          <ul v-if="summary.topExpenses.length" class="top-list">
            <li v-for="e in summary.topExpenses" :key="e.id" @click="openExpenseModal(e)">
              <span class="dot" :style="{ background: e.categoryColor }" />
              <div class="top-text">
                <div class="ellipsis">{{ e.description }}</div>
                <div class="muted small">{{ formatDate(e.date) }} · {{ e.categoryName }}</div>
              </div>
              <strong class="num">{{ money(e.amount) }}</strong>
            </li>
          </ul>
          <div v-else class="empty">—</div>
        </section>
      </div>

      <section v-if="summary.byCategory.length" class="card">
        <h2>Kategorien im Detail</h2>
        <div class="table-wrap">
          <table>
            <thead>
              <tr><th>Kategorie</th><th>Anteil</th><th class="num">Anzahl</th><th class="num">Summe</th></tr>
            </thead>
            <tbody>
              <tr v-for="c in summary.byCategory" :key="c.categoryId">
                <td><span class="chip"><span class="dot" :style="{ background: c.color }" />{{ c.name }}</span></td>
                <td class="share-cell">
                  <div class="share-bar"><div :style="{ width: `${c.sharePercent}%`, background: c.color }" /></div>
                  <span class="small muted">{{ c.sharePercent.toLocaleString('de-DE') }} %</span>
                </td>
                <td class="num">{{ c.count }}</td>
                <td class="num"><strong>{{ money(c.total) }}</strong></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </template>
  </div>
</template>

<style scoped>
.nav button.ghost { font-size: 1.3rem; line-height: 1; padding: 4px 12px; }
.loading { opacity: 0.6; transition: opacity 0.2s; }
.doughnut { height: 240px; }
.doughnut-center {
  position: absolute;
  inset: 0;
  display: grid;
  place-content: center;
  text-align: center;
  pointer-events: none;
}
.trend-stats { gap: 18px; margin-top: 10px; }
.top-list { list-style: none; margin: 0; padding: 0; }
.top-list li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 6px;
  border-bottom: 1px solid var(--border);
  cursor: pointer;
  border-radius: 8px;
}
.top-list li:last-child { border-bottom: none; }
.top-list li:hover { background: var(--surface-2); }
.top-text { flex: 1; min-width: 0; }
.ellipsis { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.share-cell { display: flex; align-items: center; gap: 10px; min-width: 180px; }
.share-bar { flex: 1; height: 8px; background: var(--surface-2); border-radius: 99px; overflow: hidden; }
.share-bar div { height: 100%; border-radius: 99px; }
</style>
