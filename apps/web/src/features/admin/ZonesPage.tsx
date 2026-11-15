import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { z } from 'zod'
import { isApiError } from '@/api/ApiError'
import { pricingApi } from '@/api/endpoints/pricing'
import { describeError } from '@/api/errorMessages'
import { applyViolations } from '@/api/formErrors'
import { qk } from '@/api/queryKeys'
import type { Zone } from '@/api/types'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { FormField } from '@/components/common/FormField'
import { PageHeader } from '@/components/common/PageHeader'
import { TagInput } from '@/components/common/TagInput'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'

const schema = z.object({
  name: z.string().trim().min(1, 'Name is required').max(80),
  code: z
    .string()
    .trim()
    .min(2, 'Code is required')
    .max(8)
    .regex(/^[A-Za-z0-9]+$/, 'Letters and digits only'),
  cities: z.array(z.string()).min(1, 'Add at least one city'),
  active: z.boolean(),
})
type Form = z.infer<typeof schema>

function ZoneDialog({ zone, onClose }: { zone?: Zone; onClose: () => void }) {
  const qc = useQueryClient()
  const {
    register,
    handleSubmit,
    control,
    setError,
    formState: { errors },
  } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: zone?.name ?? '',
      code: zone?.code ?? '',
      cities: zone?.cities ?? [],
      active: zone?.active ?? true,
    },
  })
  const save = useMutation({
    mutationFn: (v: Form) => {
      const body = { ...v, code: v.code.toUpperCase() }
      return zone ? pricingApi.updateZone(zone.id, body) : pricingApi.createZone(body)
    },
    onSuccess: () => {
      toast.success(zone ? 'Zone updated' : 'Zone created')
      onClose()
    },
    onError: (e) => {
      const extra = applyViolations(e, setError, ['name', 'code', 'cities'])
      toast.error([describeError(e).message, ...extra].join(' '))
    },
    onSettled: () => void qc.invalidateQueries({ queryKey: qk.pricing.all }),
  })
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={zone ? `Edit ${zone.name}` : 'New zone'}
    >
      <form
        onSubmit={(e) => void handleSubmit((v) => save.mutate(v))(e)}
        noValidate
        className="flex flex-col gap-4"
      >
        <FormField label="Name" error={errors.name?.message}>
          {(p) => <Input {...p} {...register('name')} />}
        </FormField>
        <FormField label="Code" error={errors.code?.message} hint="Short code, e.g. CMB">
          {(p) => <Input {...p} {...register('code')} />}
        </FormField>
        <FormField label="Cities" error={errors.cities?.message}>
          {(p) => (
            <Controller
              control={control}
              name="cities"
              render={({ field }) => (
                <TagInput id={p.id} label="Cities" value={field.value} onChange={field.onChange} />
              )}
            />
          )}
        </FormField>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" {...register('active')} /> Active
        </label>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save zone'}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

function DeleteDialog({ zone, onClose }: { zone: Zone; onClose: () => void }) {
  const qc = useQueryClient()
  const [inUse, setInUse] = useState(false)
  const refresh = () => void qc.invalidateQueries({ queryKey: qk.pricing.all })
  const del = useMutation({
    mutationFn: () => pricingApi.deleteZone(zone.id),
    onSuccess: () => {
      toast.success('Zone deleted')
      refresh()
      onClose()
    },
    onError: (e) =>
      isApiError(e) && e.errorCode === 'CONFLICT'
        ? setInUse(true)
        : toast.error(describeError(e).message),
  })
  const deactivate = useMutation({
    mutationFn: () =>
      pricingApi.updateZone(zone.id, {
        name: zone.name,
        code: zone.code,
        cities: zone.cities,
        active: false,
      }),
    onSuccess: () => {
      toast.success('Zone deactivated')
      refresh()
      onClose()
    },
    onError: (e) => toast.error(describeError(e).message),
  })
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={`Delete ${zone.name}?`}
      description={
        inUse
          ? 'This zone is used by an active pricing rule, so it can’t be deleted.'
          : 'This cannot be undone.'
      }
    >
      <div className="flex justify-end gap-2">
        <Button variant="outline" onClick={onClose}>
          Cancel
        </Button>
        {inUse ? (
          <Button disabled={deactivate.isPending} onClick={() => deactivate.mutate()}>
            Deactivate instead
          </Button>
        ) : (
          <Button variant="destructive" disabled={del.isPending} onClick={() => del.mutate()}>
            Delete
          </Button>
        )}
      </div>
    </Dialog>
  )
}

export function ZonesPage() {
  const [includeInactive, setIncludeInactive] = useState(false)
  const [editing, setEditing] = useState<Zone | 'new'>()
  const [deleting, setDeleting] = useState<Zone>()
  const q = useQuery({
    queryKey: [...qk.pricing.zonesAdmin, includeInactive],
    queryFn: () => pricingApi.zones(includeInactive),
  })

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Zones"
        actions={<Button onClick={() => setEditing('new')}>New zone</Button>}
      />
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={includeInactive}
          onChange={(e) => setIncludeInactive(e.target.checked)}
        />{' '}
        Include inactive zones
      </label>
      {q.isPending ? (
        <Skeleton className="h-48 w-full" />
      ) : q.isError ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : q.data.length === 0 ? (
        <EmptyState title="No zones yet" description="Create a zone to start pricing deliveries." />
      ) : (
        <ul className="flex flex-col gap-3">
          {q.data.map((z) => (
            <li
              key={z.id}
              className={`flex flex-wrap items-start justify-between gap-3 rounded-lg border p-4 ${z.active ? '' : 'opacity-60'}`}
            >
              <div className="min-w-0">
                <p className="font-semibold">
                  {z.name} <span className="font-mono text-xs text-muted-foreground">{z.code}</span>
                  {!z.active && (
                    <span className="ml-2 text-xs text-muted-foreground">(inactive)</span>
                  )}
                </p>
                <p className="mt-1 text-sm text-muted-foreground">{z.cities.join(', ')}</p>
              </div>
              <div className="flex gap-2">
                <Button
                  size="sm"
                  variant="outline"
                  aria-label={`Edit ${z.name}`}
                  onClick={() => setEditing(z)}
                >
                  Edit
                </Button>
                <Button
                  size="sm"
                  variant="destructive"
                  aria-label={`Delete ${z.name}`}
                  onClick={() => setDeleting(z)}
                >
                  Delete
                </Button>
              </div>
            </li>
          ))}
        </ul>
      )}
      {editing && (
        <ZoneDialog
          zone={editing === 'new' ? undefined : editing}
          onClose={() => setEditing(undefined)}
        />
      )}
      {deleting && <DeleteDialog zone={deleting} onClose={() => setDeleting(undefined)} />}
    </div>
  )
}
