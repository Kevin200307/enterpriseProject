import type { DailyStat } from '@/api/types'

/** RFC 4180: quote fields containing comma, quote or newline; double any embedded quotes; CRLF line ends. */
const cell = (v: string | number) => {
  const s = String(v)
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

export function toCsv(header: string[], rows: (string | number)[][]): string {
  return [header, ...rows].map((r) => r.map(cell).join(',')).join('\r\n') + '\r\n'
}

export function downloadCsv(filename: string, csv: string) {
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }))
  const a = Object.assign(document.createElement('a'), { href: url, download: filename })
  a.click()
  URL.revokeObjectURL(url)
}

export const statsCsv = (rows: DailyStat[]) =>
  toCsv(
    ['date', 'created', 'completed', 'failed'],
    rows.map((r) => [r.date, r.created, r.completed, r.failed]),
  )
