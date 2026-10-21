import { formatLKR } from '@/lib/format'

export const MoneyLKR = ({ value }: { value: number | string }) => (
  <span className="tabular-nums">{formatLKR(value)}</span>
)
