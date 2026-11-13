import { api, type Category, type CategoryKind, type StatementRow } from './api'
import { decode, detectDelimiter, findHeaderRow, guessColumns, parseCsv, parseStatementDate } from './csv'
import { parseAmount } from './format'

/** Most entries one import may contain (the server accepts no more in one request). */
export const MAX_ROWS = 5000

/** One entry in the review list before it is imported. */
export interface ReviewItem {
  date: string
  /** Always positive; kind says whether it is spent or received */
  amount: number
  kind: CategoryKind
  description: string
  categoryId: number | null
  selected: boolean
  /** Already booked, or contained in another of the uploaded statements */
  duplicate: boolean
  /** The statement showed no sign, so expense or income was guessed */
  uncertain: boolean
  source: string
}

export interface FileSummary {
  name: string
  count: number
  error: string | null
}

interface SourcedRow extends StatementRow {
  signCertain: boolean
  source: string
}

const isCsv = (file: File) => /\.(csv|txt)$/i.test(file.name) || file.type === 'text/csv'
const isPdf = (file: File) => /\.pdf$/i.test(file.name) || file.type === 'application/pdf'

/** Reads all statements, then lets the server suggest categories and flag what is booked already. */
export async function readStatements(files: File[], categories: Category[]) {
  const summaries: FileSummary[] = []
  const rows: SourcedRow[] = []
  const add = (name: string, found: SourcedRow[], error: string | null = null) => {
    summaries.push({ name, count: found.length, error })
    rows.push(...found)
  }

  const pdfs = files.filter(isPdf)
  for (const file of files.filter((f) => !isPdf(f))) {
    if (!isCsv(file)) add(file.name, [], 'Nur PDF- oder CSV-Dateien werden unterstützt')
    else {
      try {
        add(file.name, await readCsv(file))
      } catch (e) {
        add(file.name, [], (e as Error).message)
      }
    }
  }
  if (pdfs.length) {
    const result = await api.readStatements(pdfs)
    for (const file of result.files) {
      add(file.fileName, file.rows.map((r) => ({ ...r, source: file.fileName })), file.error)
    }
  }

  if (rows.length > MAX_ROWS) {
    throw new Error(`Höchstens ${MAX_ROWS} Buchungen auf einmal – bitte weniger Dateien wählen.`)
  }
  if (!rows.length) return { items: [] as ReviewItem[], files: summaries }

  const preview = await api.importPreview(rows.map(({ date, amount, description }) => ({ date, amount, description })))
  const inOtherFile = duplicatesAcrossFiles(rows)
  const items = rows.map((row, i): ReviewItem => {
    const kind: CategoryKind = row.amount < 0 ? 'EXPENSE' : 'INCOME'
    const duplicate = preview.rows[i].duplicate || inOtherFile[i]
    return {
      date: row.date,
      amount: Math.abs(row.amount),
      kind,
      description: row.description,
      categoryId: preview.rows[i].categoryId ?? fallbackCategory(categories, kind),
      selected: !duplicate,
      duplicate,
      uncertain: !row.signCertain,
      source: row.source,
    }
  })
  items.sort((a, b) => b.date.localeCompare(a.date))
  return { items, files: summaries }
}

/** Category for entries no rule matches: "Sonstiges"/"Sonstige Einnahmen" if present, else the first one. */
export function fallbackCategory(categories: Category[], kind: CategoryKind): number | null {
  const options = categories.filter((c) => c.kind === kind)
  return (options.find((c) => c.name.toLowerCase().startsWith('sonstig')) ?? options[0])?.id ?? null
}

/**
 * Statements of overlapping periods contain the same entries. An entry is flagged when an earlier file had it
 * already; identical entries within one file (two coffees on the same day) stay.
 */
function duplicatesAcrossFiles(rows: SourcedRow[]): boolean[] {
  const seenIn = new Map<string, string>()
  return rows.map((row) => {
    const key = `${row.date}|${row.amount.toFixed(2)}|${row.description.toLowerCase().replace(/\s+/g, ' ')}`
    const first = seenIn.get(key)
    if (first === undefined) seenIn.set(key, row.source)
    return first !== undefined && first !== row.source
  })
}

async function readCsv(file: File): Promise<SourcedRow[]> {
  const text = decode(await file.arrayBuffer())
  const table = parseCsv(text, detectDelimiter(text))
  const headerIndex = findHeaderRow(table)
  const header = table[headerIndex] ?? []
  const columns = guessColumns(header)
  if (columns.date < 0 || columns.amount < 0 || columns.description < 0) {
    throw new Error('Spalten für Datum, Betrag und Beschreibung nicht erkannt')
  }
  const rows: SourcedRow[] = []
  for (const cells of table.slice(headerIndex + 1)) {
    const date = parseStatementDate(cells[columns.date] ?? '')
    const amount = parseAmount(cells[columns.amount] ?? '')
    const description = [cells[columns.description], columns.extra >= 0 ? cells[columns.extra] : '']
      .map((p) => (p ?? '').replace(/\s+/g, ' ').trim())
      .filter((p, i, all) => p !== '' && all.indexOf(p) === i)
      .join(' · ')
      .slice(0, 255)
    if (date && Number.isFinite(amount) && amount !== 0 && description) {
      rows.push({ date, amount: Math.round(amount * 100) / 100, description, signCertain: true, source: file.name })
    }
  }
  if (!rows.length) throw new Error('Keine Buchungen erkannt')
  return rows
}
