const lkr = new Intl.NumberFormat('en-LK', { style: 'currency', currency: 'LKR' })

/** Display only — amounts arrive from the server as numbers/strings, never computed here. */
export const formatLKR = (v: number | string) => lkr.format(Number(v))

export const maskPhone = (p: string) =>
  p.length > 3 ? `${'•'.repeat(p.length - 3)}${p.slice(-3)}` : p

export const TRACKING_NUMBER_RE = /^SD-[A-HJ-NP-Z2-9]{8}$/
