import type { PeriodType } from './api'

const currency = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' })
const dateFmt = new Intl.DateTimeFormat('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' })
const dayMonth = new Intl.DateTimeFormat('de-DE', { day: '2-digit', month: '2-digit' })
const weekday = new Intl.DateTimeFormat('de-DE', { weekday: 'short' })
const monthLong = new Intl.DateTimeFormat('de-DE', { month: 'long', year: 'numeric' })
const monthShort = new Intl.DateTimeFormat('de-DE', { month: 'short' })
const monthYearShort = new Intl.DateTimeFormat('de-DE', { month: 'short', year: '2-digit' })

export const money = (value: number) => currency.format(value)

/** Amount with sign: income positive, expenses negative. */
export const signedMoney = (value: number, income: boolean) => `${income ? '+' : '−'}${currency.format(value)}`

/** Accepts "1.234,56", "12,50", "12.50" and "-3,99"; NaN if the text is not a number. */
export function parseAmount(text: string): number {
  const raw = text.trim().replace(/\s|€/g, '')
  if (!/^[+-]?[\d.,]+$/.test(raw)) return NaN
  const lastComma = raw.lastIndexOf(',')
  const lastDot = raw.lastIndexOf('.')
  // The separator further right is the decimal separator; the other one groups thousands
  const normalized = lastComma > lastDot
    ? raw.replace(/\./g, '').replace(',', '.')
    : raw.replace(/,/g, '')
  return Number(normalized)
}

/** Parses an ISO date (yyyy-mm-dd) as a local date, avoiding UTC shifts. */
export function parseDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function toIso(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export const today = () => toIso(new Date())

export const formatDate = (iso: string) => dateFmt.format(parseDate(iso))

export function isoWeek(date: Date): number {
  const d = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()))
  const day = d.getUTCDay() || 7
  d.setUTCDate(d.getUTCDate() + 4 - day)
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1))
  return Math.ceil(((d.getTime() - yearStart.getTime()) / 86400000 + 1) / 7)
}

/** Moves a date by n periods (weeks, months or years). */
export function shift(iso: string, period: PeriodType, n: number): string {
  const d = parseDate(iso)
  if (period === 'WEEK') d.setDate(d.getDate() + 7 * n)
  if (period === 'MONTH') {
    d.setDate(1)
    d.setMonth(d.getMonth() + n)
  }
  if (period === 'YEAR') {
    d.setMonth(0, 1)
    d.setFullYear(d.getFullYear() + n)
  }
  return toIso(d)
}

export function periodLabel(period: PeriodType, start: string, end: string): string {
  const s = parseDate(start)
  if (period === 'WEEK') return `KW ${isoWeek(s)} · ${dayMonth.format(s)} – ${dateFmt.format(parseDate(end))}`
  if (period === 'MONTH') return monthLong.format(s)
  return String(s.getFullYear())
}

/** Label of a chart bucket inside a period (days of a week/month, months of a year). */
export function breakdownLabel(period: PeriodType, start: string): string {
  const d = parseDate(start)
  if (period === 'WEEK') return `${weekday.format(d)} ${dayMonth.format(d)}`
  if (period === 'MONTH') return String(d.getDate())
  return monthShort.format(d)
}

/** Label of one period in the multi-period trend chart. */
export function trendLabel(period: PeriodType, start: string): string {
  const d = parseDate(start)
  if (period === 'WEEK') return `KW ${isoWeek(d)}`
  if (period === 'MONTH') return monthYearShort.format(d)
  return String(d.getFullYear())
}

export const recurrenceNames: Record<string, string> = {
  WEEKLY: 'Wöchentlich',
  MONTHLY: 'Monatlich',
  QUARTERLY: 'Vierteljährlich',
  YEARLY: 'Jährlich',
}

export const periodNames: Record<PeriodType, { label: string; plural: string; previous: string }> = {
  WEEK: { label: 'Woche', plural: 'Wochen', previous: 'Vorwoche' },
  MONTH: { label: 'Monat', plural: 'Monate', previous: 'Vormonat' },
  YEAR: { label: 'Jahr', plural: 'Jahre', previous: 'Vorjahr' },
}
