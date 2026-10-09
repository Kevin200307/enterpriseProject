const lkr = new Intl.NumberFormat('en-LK', { style: 'currency', currency: 'LKR' })

/** Display only — amounts arrive from the server as numbers/strings, never computed here. */
export const formatLKR = (v: number | string) => lkr.format(Number(v))

export const maskPhone = (p: string) =>
  p.length > 3 ? `${'•'.repeat(p.length - 3)}${p.slice(-3)}` : p

export const TRACKING_NUMBER_RE = /^SD-[A-HJ-NP-Z2-9]{8}$/

const TZ = 'Asia/Colombo'
const dateTime = new Intl.DateTimeFormat('en-GB', {
  dateStyle: 'medium',
  timeStyle: 'short',
  timeZone: TZ,
})
const dateOnly = new Intl.DateTimeFormat('en-GB', { dateStyle: 'medium', timeZone: TZ })

export const formatDateTime = (iso: string) => dateTime.format(new Date(iso))
export const formatDate = (iso: string) => dateOnly.format(new Date(iso))

/** "3 min ago" style label for freshness indicators. */
export function timeAgo(iso: string, now = Date.now()) {
  const s = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000))
  if (s < 45) return 'just now'
  if (s < 3600) return `${Math.round(s / 60)} min ago`
  if (s < 86400) return `${Math.round(s / 3600)} h ago`
  return `${Math.round(s / 86400)} d ago`
}

export type AgeTone = 'ok' | 'warn' | 'late'

/** Dispatch urgency: amber after 4 h, red after 24 h waiting for a courier. */
export function ageTone(iso: string, now = Date.now()): AgeTone {
  const hours = (now - new Date(iso).getTime()) / 3_600_000
  return hours >= 24 ? 'late' : hours >= 4 ? 'warn' : 'ok'
}
