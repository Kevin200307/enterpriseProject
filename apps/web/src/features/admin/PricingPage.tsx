import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { pricingApi } from '@/api/endpoints/pricing'
import { describeError } from '@/api/errorMessages'
import { applyViolations } from '@/api/formErrors'
import { qk } from '@/api/queryKeys'
import type { PricingRule, Zone } from '@/api/types'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { FormField } from '@/components/common/FormField'
import { MoneyLKR } from '@/components/common/MoneyLKR'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'

const money = (label: string) =>
  z
    .string()
    .refine(
      (v) => /^\d+(\.\d{1,2})?$/.test(v.trim()),
      `${label}: a non-negative amount with up to 2 decimals`,
    )

const schema = z.object({
  zoneFromId: z.string().min(1, 'Choose a zone'),
  zoneToId: z.string().min(1, 'Choose a zone'),
  sizeBand: z.enum(['SMALL', 'MEDIUM', 'LARGE']),
  baseFee: money('Base fee'),
  perKg: money('Per kg'),
  minFee: money('Minimum fee'),
  maxWeightKg: z
    .string()
    .refine(
      (v) => /^\d+(\.\d{1,2})?$/.test(v.trim()) && Number(v) > 0,
      'Max weight must be above 0',
    ),
  effectiveFrom: z.string().min(1, 'Start date is required'),
  effectiveTo: z.string().optional(),
  active: z.boolean(),
})
type Form = z.infer<typeof schema>

const FIELDS = [
  'zoneFromId',
  'zoneToId',
  'sizeBand',
  'baseFee',
  'perKg',
  'minFee',
  'maxWeightKg',
  'effectiveFrom',
  'effectiveTo',
]

function RuleDialog({
  rule,
  zones,
  onClose,
}: {
  rule?: PricingRule
  zones: Zone[]
  onClose: () => void
}) {
  const qc = useQueryClient()
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: {
      zoneFromId: rule?.zoneFromId ?? '',
      zoneToId: rule?.zoneToId ?? '',
      sizeBand: rule?.sizeBand ?? 'SMALL',
      baseFee: rule ? String(rule.baseFee) : '',
      perKg: rule ? String(rule.perKg) : '',
      minFee: rule ? String(rule.minFee) : '',
      maxWeightKg: rule ? String(rule.maxWeightKg) : '',
      effectiveFrom: rule?.effectiveFrom ?? '',
      effectiveTo: rule?.effectiveTo ?? '',
      active: rule?.active ?? true,
    },
  })
  const save = useMutation({
    mutationFn: (v: Form) => {
      const body = {
        ...v,
        baseFee: Number(v.baseFee),
        perKg: Number(v.perKg),
        minFee: Number(v.minFee),
        maxWeightKg: Number(v.maxWeightKg),
        effectiveTo: v.effectiveTo || null,
      }
      return rule ? pricingApi.updateRule(rule.id, body) : pricingApi.createRule(body)
    },
    onSuccess: () => {
      toast.success(rule ? 'Rule updated' : 'Rule created')
      onClose()
    },
    onError: (e) => {
      const extra = applyViolations(e, setError, FIELDS)
      toast.error([describeError(e).message, ...extra].join(' '))
    },
    onSettled: () => void qc.invalidateQueries({ queryKey: qk.pricing.rules }),
  })
  const select = (p: object, reg: object) => (
    <select
      {...p}
      {...reg}
      className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
    >
      <option value="">Select…</option>
      {zones.map((z) => (
        <option key={z.id} value={z.id}>
          {z.name}
        </option>
      ))}
    </select>
  )
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={rule ? 'Edit pricing rule' : 'New pricing rule'}
    >
      <form
        onSubmit={(e) => void handleSubmit((v) => save.mutate(v))(e)}
        noValidate
        className="grid max-h-[70vh] gap-4 overflow-y-auto sm:grid-cols-2"
      >
        <FormField label="From zone" error={errors.zoneFromId?.message}>
          {(p) => select(p, register('zoneFromId'))}
        </FormField>
        <FormField label="To zone" error={errors.zoneToId?.message}>
          {(p) => select(p, register('zoneToId'))}
        </FormField>
        <FormField label="Size band" error={errors.sizeBand?.message}>
          {(p) => (
            <select
              {...p}
              {...register('sizeBand')}
              className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm"
            >
              <option value="SMALL">Small</option>
              <option value="MEDIUM">Medium</option>
              <option value="LARGE">Large</option>
            </select>
          )}
        </FormField>
        <FormField label="Max weight (kg)" error={errors.maxWeightKg?.message}>
          {(p) => <Input inputMode="decimal" {...p} {...register('maxWeightKg')} />}
        </FormField>
        <FormField label="Base fee (LKR)" error={errors.baseFee?.message}>
          {(p) => <Input inputMode="decimal" {...p} {...register('baseFee')} />}
        </FormField>
        <FormField label="Per kg (LKR)" error={errors.perKg?.message}>
          {(p) => <Input inputMode="decimal" {...p} {...register('perKg')} />}
        </FormField>
        <FormField label="Minimum fee (LKR)" error={errors.minFee?.message}>
          {(p) => <Input inputMode="decimal" {...p} {...register('minFee')} />}
        </FormField>
        <div />
        <FormField label="Effective from" error={errors.effectiveFrom?.message}>
          {(p) => <Input type="date" {...p} {...register('effectiveFrom')} />}
        </FormField>
        <FormField label="Effective to (optional)" error={errors.effectiveTo?.message}>
          {(p) => <Input type="date" {...p} {...register('effectiveTo')} />}
        </FormField>
        <label className="flex items-center gap-2 text-sm sm:col-span-2">
          <input type="checkbox" {...register('active')} /> Active
        </label>
        <div className="flex justify-end gap-2 sm:col-span-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save rule'}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

export function PricingPage() {
  const [size, setSize] = useState('')
  const [editing, setEditing] = useState<PricingRule | 'new'>()
  const rules = useQuery({ queryKey: qk.pricing.rules, queryFn: () => pricingApi.rules(true) })
  const zones = useQuery({
    queryKey: qk.pricing.zonesAdmin,
    queryFn: () => pricingApi.zones(true),
    staleTime: 60_000,
  })
  const names = useMemo(() => new Map((zones.data ?? []).map((z) => [z.id, z.name])), [zones.data])
  const shown = (rules.data ?? []).filter((r) => !size || r.sizeBand === size)

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Pricing rules"
        actions={
          <Button disabled={!zones.data} onClick={() => setEditing('new')}>
            New rule
          </Button>
        }
      />
      <div className="flex flex-col gap-1">
        <Label htmlFor="size-filter" className="text-xs">
          Size band
        </Label>
        <select
          id="size-filter"
          value={size}
          onChange={(e) => setSize(e.target.value)}
          className="h-10 w-48 rounded-md border border-input bg-background px-3 text-sm"
        >
          <option value="">All sizes</option>
          <option value="SMALL">Small</option>
          <option value="MEDIUM">Medium</option>
          <option value="LARGE">Large</option>
        </select>
      </div>
      {rules.isPending ? (
        <Skeleton className="h-48 w-full" />
      ) : rules.isError ? (
        <ErrorState error={rules.error} onRetry={() => void rules.refetch()} />
      ) : shown.length === 0 ? (
        <EmptyState title="No pricing rules" />
      ) : (
        <div className="overflow-x-auto rounded-lg border">
          <table className="w-full text-sm">
            <thead className="bg-muted text-left">
              <tr>
                {['Route', 'Size', 'Base', 'Per kg', 'Min', 'Max kg', 'Effective', ''].map((h) => (
                  <th key={h} scope="col" className="p-3 font-medium">
                    {h || <span className="sr-only">Actions</span>}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {shown.map((r) => (
                <tr key={r.id} className={`border-t ${r.active ? '' : 'opacity-50'}`}>
                  <th scope="row" className="p-3 text-left font-medium">
                    {names.get(r.zoneFromId) ?? '—'} → {names.get(r.zoneToId) ?? '—'}
                  </th>
                  <td className="p-3">{r.sizeBand}</td>
                  <td className="p-3">
                    <MoneyLKR value={r.baseFee} />
                  </td>
                  <td className="p-3">
                    <MoneyLKR value={r.perKg} />
                  </td>
                  <td className="p-3">
                    <MoneyLKR value={r.minFee} />
                  </td>
                  <td className="p-3">{r.maxWeightKg}</td>
                  <td className="p-3">
                    {r.effectiveFrom}
                    {r.effectiveTo ? ` → ${r.effectiveTo}` : ' →'}
                  </td>
                  <td className="p-3 text-right">
                    <Button
                      size="sm"
                      variant="outline"
                      aria-label={`Edit rule ${names.get(r.zoneFromId)} to ${names.get(r.zoneToId)} ${r.sizeBand}`}
                      onClick={() => setEditing(r)}
                    >
                      Edit
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {editing && zones.data && (
        <RuleDialog
          rule={editing === 'new' ? undefined : editing}
          zones={zones.data}
          onClose={() => setEditing(undefined)}
        />
      )}
    </div>
  )
}
