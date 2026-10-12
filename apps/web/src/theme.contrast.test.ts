import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const css = readFileSync(resolve(__dirname, 'index.css'), 'utf8')

function token(name: string): [number, number, number] {
  const m = new RegExp(`--${name}:\\s*([\\d.]+)\\s+([\\d.]+)%\\s+([\\d.]+)%`).exec(css)
  if (!m) throw new Error(`token --${name} not found or not in "H S% L%" form`)
  return [Number(m[1]), Number(m[2]), Number(m[3])]
}

function rgb([h, s, l]: [number, number, number]): [number, number, number] {
  const S = s / 100
  const L = l / 100
  const k = (n: number) => (n + h / 30) % 12
  const a = S * Math.min(L, 1 - L)
  const f = (n: number) => L - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)))
  return [Math.round(f(0) * 255), Math.round(f(8) * 255), Math.round(f(4) * 255)]
}

const lum = (c: [number, number, number]) => {
  const [r, g, b] = c.map((v) => {
    const x = v / 255
    return x <= 0.03928 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4
  }) as [number, number, number]
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}
const contrast = (a: string, b: string) => {
  const [hi, lo] = [lum(rgb(token(a))), lum(rgb(token(b)))].sort((x, y) => y - x) as [
    number,
    number,
  ]
  return (hi + 0.05) / (lo + 0.05)
}

describe('theme', () => {
  it('is a near-black primary with white text on it', () => {
    const [, s, l] = token('primary')
    expect(s).toBeLessThanOrEqual(5) // neutral, not tinted
    expect(l).toBeLessThanOrEqual(20) // near-black
    expect(rgb(token('primary-foreground'))).toEqual([255, 255, 255])
  })

  it('accents with lime that carries dark text, never white', () => {
    const [h, s, l] = token('accent')
    expect(h).toBeGreaterThanOrEqual(60)
    expect(h).toBeLessThanOrEqual(85)
    expect(s).toBeGreaterThanOrEqual(70)
    expect(l).toBeGreaterThanOrEqual(55) // bright enough that white text would fail
    expect(token('accent-foreground')[2]).toBeLessThanOrEqual(20)
  })

  // WCAG AA for normal text is 4.5:1.
  it.each([
    ['foreground', 'background'],
    ['card-foreground', 'card'],
    ['primary-foreground', 'primary'], // white text on the orange: buttons, brand panel, chat bubbles
    ['primary-foreground', 'primary-hover'], // and still readable while hovered
    ['primary', 'background'], // orange links and labels on white
    ['secondary-foreground', 'secondary'],
    ['muted-foreground', 'background'],
    ['muted-foreground', 'muted'],
    ['accent-foreground', 'accent'],
    ['destructive-foreground', 'destructive'],
  ])('%s on %s meets WCAG AA (4.5:1)', (fg, bg) => {
    expect(contrast(fg, bg)).toBeGreaterThanOrEqual(4.5)
  })

  it('hover is darker than the resting colour, never lighter', () => {
    expect(token('primary-hover')[2]).toBeLessThan(token('primary')[2])
  })

  it('defines a soft near-black dark colour scheme', () => {
    expect(css).toContain('color-scheme: light')
    expect(css).toContain('.dark')
    expect(css).toContain('color-scheme: dark')
    expect(token('background')[2]).toBeGreaterThan(0)
  })
})
