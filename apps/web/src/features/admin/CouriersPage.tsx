import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { z } from 'zod'
import { isApiError } from '@/api/ApiError'
import { couriersApi } from '@/api/endpoints/couriers'
import { adminApi } from '@/api/endpoints/admin'
import { describeError } from '@/api/errorMessages'
import { applyViolations } from '@/api/formErrors'
import { pricingApi } from '@/api/endpoints/pricing'
import { qk } from '@/api/queryKeys'
import type { Courier } from '@/api/types'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { FormField } from '@/components/common/FormField'
import { PageHeader } from '@/components/common/PageHeader'
import { Pagination } from '@/components/common/Pagination'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { phoneSchema } from '@/features/auth/schemas'

const VEHICLES = [
  { value: 'BIKE', label: 'Motorbike' },
  { value: 'THREEWHEEL', label: 'Three-wheeler' },
  { value: 'VAN', label: 'Van' },
]

const schema = z.object({
  userId: z.string().optional(),
  vehicleType: z.enum(['BIKE', 'THREEWHEEL', 'VAN']),
  homeZoneCode: z.string().min(1, 'Choose a home zone'),
  phone: phoneSchema,
  maxDailyLoad: z
    .string()
    .refine(
      (v) => /^\d+$/.test(v) && Number(v) >= 1 && Number(v) <= 200,
      'Whole number between 1 and 200',
    ),
  active: z.boolean(),
})
type Form = z.infer<typeof schema>

function CourierDialog({ courier, onClose }: { courier?: Courier; onClose: () => void }) {
  const qc = useQueryClient()
  const zones = useQuery({
    queryKey: qk.pricing.zones,
    queryFn: () => pricingApi.zones(),
    staleTime: 5 * 60_000,
  })
  // Listing users needs ADMIN; a dispatcher gets a 403 and falls back to typing the user id.
  const users = useQuery({
    queryKey: qk.admin.users({ role: 'COURIER', picker: true }),
    queryFn: () => adminApi.users({ role: 'COURIER' }),
    enabled: !courier,
    retry: false,
  })
  const [conflict, setConflict] = useState<string>()
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors },
  } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: {
      userId: courier?.userId ?? '',
      vehicleType: courier?.vehicleType ?? 'BIKE',
      homeZoneCode: courier?.homeZoneCode ?? '',
      phone: courier?.phone ?? '+94',
      maxDailyLoad: String(courier?.maxDailyLoad ?? 20),
      active: courier?.active ?? true,
    },
  })
  const save = useMutation({
    mutationFn: ({ userId, maxDailyLoad, ...v }: Form) => {
      const body = { ...v, maxDailyLoad: Number(maxDailyLoad) }
      return courier
        ? couriersApi.update(courier.id, body)
        : couriersApi.create({ ...body, userId })
    },
    onSuccess: () => {
      toast.success(courier ? 'Courier updated' : 'Courier registered')
      onClose()
    },
    onError: (e) => {
      // Deactivating with open assignments is a 409 whose detail lists the tracking numbers to reassign first.
      if (isApiError(e) && e.errorCode === 'CONFLICT') return setConflict(e.detail)
      const extra = applyViolations(e, setError, [
        'userId',
        'vehicleType',
        'homeZoneCode',
        'phone',
        'maxDailyLoad',
      ])
      toast.error([describeError(e).message, ...extra].join(' '))
    },
    onSettled: () => void qc.invalidateQueries({ queryKey: qk.admin.all }),
  })
  const selectCls = 'h-10 w-full rounded-md border border-input bg-background px-3 text-sm'
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={courier ? `Edit ${courier.name}` : 'Register courier'}
    >
      <form
        onSubmit={(e) => {
          setConflict(undefined)
          void handleSubmit((v) => {
            if (!courier && !v.userId?.trim())
              return setError('userId', { message: 'Choose a courier user' })
            save.mutate(v)
          })(e)
        }}
        noValidate
        className="flex max-h-[70vh] flex-col gap-4 overflow-y-auto"
      >
        {!courier && (
          <FormField
            label="Courier user"
            error={errors.userId?.message}
            hint={users.isError ? 'Enter the user id (only admins can list users).' : undefined}
          >
            {(p) =>
              users.data ? (
                <select {...p} {...register('userId')} className={selectCls}>
                  <option value="">Select…</option>
                  {users.data.content.map((u) => (
                    <option key={u.id} value={u.id}>
                      {u.fullName} ({u.email})
                    </option>
                  ))}
                </select>
              ) : (
                <Input {...p} {...register('userId')} />
              )
            }
          </FormField>
        )}
        <FormField label="Vehicle" error={errors.vehicleType?.message}>
          {(p) => (
            <select {...p} {...register('vehicleType')} className={selectCls}>
              {VEHICLES.map((v) => (
                <option key={v.value} value={v.value}>
                  {v.label}
                </option>
              ))}
            </select>
          )}
        </FormField>
        <FormField label="Home zone" error={errors.homeZoneCode?.message}>
          {(p) => (
            <select {...p} {...register('homeZoneCode')} className={selectCls}>
              <option value="">Select…</option>
              {zones.data?.map((z) => (
                <option key={z.id} value={z.code}>
                  {z.name}
                </option>
              ))}
            </select>
          )}
        </FormField>
        <FormField label="Phone" error={errors.phone?.message}>
          {(p) => <Input type="tel" {...p} {...register('phone')} />}
        </FormField>
        <FormField label="Max daily load" error={errors.maxDailyLoad?.message}>
          {(p) => <Input inputMode="numeric" {...p} {...register('maxDailyLoad')} />}
        </FormField>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" {...register('active')} /> Active
        </label>
        {conflict && (
          <p
            role="alert"
            className="rounded-md bg-red-50 p-3 text-sm text-red-900 dark:bg-red-950 dark:text-red-100"
          >
            {conflict}
          </p>
        )}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" disabled={save.isPending}>
            {save.isPending ? 'Saving…' : 'Save courier'}
          </Button>
        </div>
      </form>
    </Dialog>
  )
}

export function CouriersPage() {
  const [params, setParams] = useSearchParams()
  const zone = params.get('zone') ?? ''
  const vehicle = params.get('vehicle') ?? ''
  const active = params.get('active') ?? ''
  const page = Math.max(0, Number(params.get('page')) || 0)
  const [editing, setEditing] = useState<Courier | 'new'>()
  const zones = useQuery({
    queryKey: qk.pricing.zones,
    queryFn: () => pricingApi.zones(),
    staleTime: 5 * 60_000,
  })
  const q = useQuery({
    queryKey: qk.admin.couriers({ zone, vehicle, active, page }),
    queryFn: () =>
      couriersApi.list({
        zone: zone || undefined,
        vehicle: vehicle || undefined,
        active: active || undefined,
        page,
      }),
    placeholderData: (prev) => prev,
  })
  const setFilter = (k: string, v: string) => {
    const next = new URLSearchParams(params)
    if (v) next.set(k, v)
    else next.delete(k)
    next.delete('page')
    setParams(next)
  }
  const sel = 'h-10 rounded-md border border-input bg-background px-3 text-sm'

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Couriers"
        actions={<Button onClick={() => setEditing('new')}>Register courier</Button>}
      />
      <div className="flex flex-wrap gap-3">
        <div className="flex flex-col gap-1">
          <Label htmlFor="c-zone" className="text-xs">
            Zone
          </Label>
          <select
            id="c-zone"
            className={sel}
            value={zone}
            onChange={(e) => setFilter('zone', e.target.value)}
          >
            <option value="">All</option>
            {zones.data?.map((z) => (
              <option key={z.id} value={z.code}>
                {z.name}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="c-vehicle" className="text-xs">
            Vehicle
          </Label>
          <select
            id="c-vehicle"
            className={sel}
            value={vehicle}
            onChange={(e) => setFilter('vehicle', e.target.value)}
          >
            <option value="">All</option>
            {VEHICLES.map((v) => (
              <option key={v.value} value={v.value}>
                {v.label}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="c-active" className="text-xs">
            Status
          </Label>
          <select
            id="c-active"
            className={sel}
            value={active}
            onChange={(e) => setFilter('active', e.target.value)}
          >
            <option value="">All</option>
            <option value="true">Active</option>
            <option value="false">Inactive</option>
          </select>
        </div>
      </div>

      {q.isPending ? (
        <Skeleton className="h-48 w-full" />
      ) : q.isError ? (
        <ErrorState error={q.error} onRetry={() => void q.refetch()} />
      ) : q.data.content.length === 0 ? (
        <EmptyState
          title="No couriers"
          description="Register a courier to start assigning parcels."
        />
      ) : (
        <>
          <ul className="flex flex-col gap-3">
            {q.data.content.map((c) => (
              <li
                key={c.id}
                className={`flex flex-wrap items-center justify-between gap-3 rounded-lg border p-4 ${c.active ? '' : 'opacity-60'}`}
              >
                <div>
                  <p className="font-semibold">
                    {c.name}
                    {!c.active && (
                      <span className="ml-2 text-xs text-muted-foreground">(inactive)</span>
                    )}
                  </p>
                  <p className="text-sm text-muted-foreground">
                    {VEHICLES.find((v) => v.value === c.vehicleType)?.label} · {c.homeZoneCode} · up
                    to {c.maxDailyLoad}/day · {c.phone}
                  </p>
                </div>
                <Button
                  size="sm"
                  variant="outline"
                  aria-label={`Edit ${c.name}`}
                  onClick={() => setEditing(c)}
                >
                  Edit
                </Button>
              </li>
            ))}
          </ul>
          <Pagination
            page={q.data.number}
            totalPages={q.data.totalPages}
            onChange={(n) => {
              const next = new URLSearchParams(params)
              next.set('page', String(n))
              setParams(next)
            }}
          />
        </>
      )}
      {editing && (
        <CourierDialog
          courier={editing === 'new' ? undefined : editing}
          onClose={() => setEditing(undefined)}
        />
      )}
    </div>
  )
}
