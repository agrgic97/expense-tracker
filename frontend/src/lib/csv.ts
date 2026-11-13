import { toIso } from './format'

/**
 * Reading bank statement exports. German banks differ in encoding (UTF-8 or Windows-1252), delimiter and
 * number format, and some put account details above the actual table, so all of that is detected here.
 */

/** Decodes UTF-8 and falls back to Windows-1252, which older Sparkasse/Volksbank exports use. */
export function decode(buffer: ArrayBuffer): string {
  let text: string
  try {
    text = new TextDecoder('utf-8', { fatal: true }).decode(buffer)
  } catch {
    text = new TextDecoder('windows-1252').decode(buffer)
  }
  return text.replace(/^﻿/, '')
}

/** Splits CSV text into rows, honoring quotes (including quoted delimiters, line breaks and "" escapes). */
export function parseCsv(text: string, delimiter: string): string[][] {
  const rows: string[][] = []
  let row: string[] = []
  let field = ''
  let quoted = false
  for (let i = 0; i < text.length; i++) {
    const c = text[i]
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') {
        field += '"'
        i++
      } else if (c === '"') {
        quoted = false
      } else {
        field += c
      }
    } else if (c === '"') {
      quoted = true
    } else if (c === delimiter) {
      row.push(field)
      field = ''
    } else if (c === '\n' || c === '\r') {
      if (c === '\r' && text[i + 1] === '\n') i++
      row.push(field)
      rows.push(row)
      row = []
      field = ''
    } else {
      field += c
    }
  }
  if (field !== '' || row.length) {
    row.push(field)
    rows.push(row)
  }
  return rows
    .map((r) => r.map((f) => f.trim()))
    .filter((r) => r.some((f) => f !== ''))
}

/** Picks the delimiter that splits the most lines into the same number (≥ 3) of columns. */
export function detectDelimiter(text: string): string {
  const sample = text.split(/\r?\n/).slice(0, 50).join('\n')
  let best = ';'
  let bestScore = -1
  for (const delimiter of [';', ',', '\t']) {
    const counts = parseCsv(sample, delimiter).map((r) => r.length).filter((n) => n >= 3)
    const score = Math.max(0, ...Object.values(frequencies(counts)))
    if (score > bestScore) {
      best = delimiter
      bestScore = score
    }
  }
  return best
}

/**
 * Index of the header row: the first row having the column count most rows have. Lines with account details
 * above the table have fewer columns and are skipped this way.
 */
export function findHeaderRow(rows: string[][]): number {
  const counts = frequencies(rows.map((r) => r.length))
  const width = Number(Object.entries(counts).sort((a, b) => b[1] - a[1] || Number(b[0]) - Number(a[0]))[0]?.[0] ?? 0)
  return Math.max(0, rows.findIndex((r) => r.length === width))
}

export interface ColumnGuess {
  date: number
  amount: number
  description: number
  extra: number
}

/**
 * Guesses the columns from typical German and English header names; -1 if none fits. Patterns are tried in order
 * of preference, so e.g. "Verwendungszweck" wins over "Buchungstext" wherever the two columns stand.
 */
export function guessColumns(header: string[]): ColumnGuess {
  const names = header.map((h) => h.toLowerCase().trim())
  const find = (...patterns: RegExp[]) => {
    for (const pattern of patterns) {
      const index = names.findIndex((n) => pattern.test(n))
      if (index >= 0) return index
    }
    return -1
  }
  const date = find(/^buchung(stag|sdatum)?$/, /^(datum|date)\b/, /buchungstag|buchungsdatum|datum|date/, /valuta|wertstellung/)
  const amount = find(/^betrag\b/, /^amount\b/, /^umsatz( in|$)/, /betrag|amount/)
  const description = find(/verwendungszweck/, /beschreibung|description|reference|referenz/, /buchungstext/)
  const extra = find(/empfänger|begünstigter|beguenstigter|payee/, /auftraggeber|zahlungspflichtig/, /name/)
  return { date, amount, description: description >= 0 ? description : extra, extra: description >= 0 ? extra : -1 }
}

/** Parses dd.mm.yyyy, dd.mm.yy, dd/mm/yyyy and yyyy-mm-dd into an ISO date, or null. */
export function parseStatementDate(text: string): string | null {
  const value = text.trim()
  let match = /^(\d{4})-(\d{1,2})-(\d{1,2})/.exec(value)
  let year: number, month: number, day: number
  if (match) {
    ;[year, month, day] = [Number(match[1]), Number(match[2]), Number(match[3])]
  } else {
    match = /^(\d{1,2})[./](\d{1,2})[./](\d{2}|\d{4})$/.exec(value)
    if (!match) return null
    ;[day, month, year] = [Number(match[1]), Number(match[2]), Number(match[3])]
    if (year < 100) year += 2000
  }
  const date = new Date(year, month - 1, day)
  if (date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return null
  return toIso(date)
}

function frequencies(values: number[]): Record<number, number> {
  const result: Record<number, number> = {}
  for (const v of values) result[v] = (result[v] ?? 0) + 1
  return result
}
