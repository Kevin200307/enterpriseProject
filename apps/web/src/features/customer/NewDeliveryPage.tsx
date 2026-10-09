import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { describeError } from '@/api/errorMessages'
import { applyViolations } from '@/api/formErrors'
import { pricingApi } from '@/api/endpoints/pricing'
import { parcelsApi } from '@/api/endpoints/parcels'
import { usersApi } from '@/api/endpoints/auth'
import { qk } from '@/api/queryKeys'
import type { EstimateInput, Parcel } from '@/api/types'
import { CopyField } from '@/components/common/CopyField'
import { FormField } from '@/components/common/FormField'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { useDebounced } from '@/hooks/useDebounced'
import { shareLink, trackingUrl } from '@/lib/share'
import { AddressFields } from './AddressFields'
import {
  BOOKING_FIELDS,
  SIZES,
  WEIGHT_RE,
  bookingDefaults,
  bookingSchema,
  type BookingForm,
} from './bookingSchema'
import { EstimatePanel } from './EstimatePanel'

export function NewDeliveryPage() {
  const cityListId = useId()
  const qc = useQueryClient()
  const [created, setCreated] = useState<Parcel>()
  const [formError, setFormError] = useState<string>()
  const {
    register,
    handleSubmit,
    control,
    setValue,
    setError,
    reset,
    formState: { errors },
  } = useForm<BookingForm>({ resolver: zodResolver(bookingSchema), defaultValues: bookingDefaults })

  const zones = useQuery({
    queryKey: qk.pricing.zones,
    queryFn: pricingApi.zones,
    staleTime: 5 * 60_000,
  })
  const cities = [...new Set((zones.data ?? []).flatMap((z) => z.cities))].sort()

  // --- live estimate -------------------------------------------------------
  const [fromCity, toCity, size, weight, express] = useWatch({
    control,
    name: ['pickup.city', 'dropoff.city', 'size', 'weightKg', 'express'],
  })
  const wanted = {
    fromCity: fromCity.trim(),
    toCity: toCity.trim(),
    size,
    weightKg: Number(weight),
    express,
  }
  const isReady =
    !!wanted.fromCity && !!wanted.toCity && WEIGHT_RE.test(weight.trim()) && wanted.weightKg > 0
  const input = useDebounced(wanted, 400)
  const settled = JSON.stringify(input) === JSON.stringify(wanted)
  const estimate = useQuery({
    queryKey: qk.pricing.estimate(input),
    queryFn: ({ signal }) => pricingApi.estimate(input as EstimateInput, signal),
    enabled: isReady && settled,
    retry: false,
  })
  // While the form is incomplete the button stays enabled so validation errors can surface; once it is
  // complete we never book against a missing, stale or failed price.
  const blocked = isReady && (!settled || estimate.isFetching || !!estimate.error)

  // --- submit --------------------------------------------------------------
  const book = useMutation({
    mutationFn: (v: BookingForm) =>
      parcelsApi.create({
        ...v,
        weightKg: Number(v.weightKg),
        pickup: clean(v.pickup),
        dropoff: clean(v.dropoff),
      }),
    onSuccess: (parcel) => {
      setCreated(parcel)
      void qc.invalidateQueries({ queryKey: qk.parcels.all })
    },
    onError: (e) => {
      const extra = applyViolations(e, setError, BOOKING_FIELDS)
      setFormError([describeError(e).message, ...extra].join(' '))
    },
  })

  async function fillMyDetails() {
    try {
      const me = await qc.fetchQuery({ queryKey: qk.me, queryFn: usersApi.me, staleTime: 60_000 })
      setValue('pickup.contactName', me.fullName, { shouldDirty: true })
      setValue('pickup.phone', me.phone, { shouldDirty: true })
    } catch (e) {
      toast.error(describeError(e).message)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="New delivery"
        description="Tell us where it's going and we'll price it instantly."
      />
      <datalist id={cityListId}>
        {cities.map((c) => (
          <option key={c} value={c} />
        ))}
      </datalist>
      <form
        onSubmit={(e) => {
          setFormError(undefined)
          void handleSubmit((v) => {
            if (estimate.data) book.mutate(v)
          })(e)
        }}
        noValidate
        className="grid gap-6 lg:grid-cols-[1fr_320px]"
      >
        <div className="flex flex-col gap-6">
          <Card>
            <CardHeader className="flex-row items-center justify-between">
              <CardTitle>Pickup</CardTitle>
              <Button type="button" variant="ghost" size="sm" onClick={() => void fillMyDetails()}>
                Use my details
              </Button>
            </CardHeader>
            <CardContent>
              <AddressFields
                group="pickup"
                register={register}
                errors={errors}
                cityListId={cityListId}
              />
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>Drop-off</CardTitle>
            </CardHeader>
            <CardContent>
              <AddressFields
                group="dropoff"
                register={register}
                errors={errors}
                cityListId={cityListId}
              />
            </CardContent>
          </Card>
          <Card>
            <CardHeader>
              <CardTitle>Parcel</CardTitle>
            </CardHeader>
            <CardContent className="flex flex-col gap-4">
              <fieldset className="grid gap-2 sm:grid-cols-3">
                <legend className="mb-2 text-sm font-medium">Size</legend>
                {SIZES.map((s) => (
                  <label
                    key={s.value}
                    className="flex cursor-pointer flex-col gap-0.5 rounded-md border p-3 text-sm has-[:checked]:border-primary has-[:checked]:bg-primary/5 has-[:focus-visible]:ring-2 has-[:focus-visible]:ring-ring"
                  >
                    <span className="flex items-center gap-2 font-medium">
                      <input type="radio" value={s.value} {...register('size')} />
                      {s.label}
                    </span>
                    <span className="text-xs text-muted-foreground">{s.hint}</span>
                  </label>
                ))}
              </fieldset>
              <FormField label="Weight (kg)" error={errors.weightKg?.message}>
                {(p) => (
                  <Input
                    inputMode="decimal"
                    placeholder="e.g. 1.5"
                    autoComplete="off"
                    {...p}
                    {...register('weightKg')}
                  />
                )}
              </FormField>
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" {...register('express')} />
                <span>
                  <span className="font-medium">Express delivery</span>
                  <span className="text-muted-foreground">
                    {' '}
                    — priority handling for an extra charge
                  </span>
                </span>
              </label>
            </CardContent>
          </Card>
        </div>

        <div className="flex flex-col gap-4 lg:sticky lg:top-4 lg:self-start">
          <EstimatePanel
            ready={isReady}
            loading={isReady && (!settled || estimate.isFetching)}
            data={isReady && settled ? estimate.data : undefined}
            error={isReady && settled ? estimate.error : undefined}
            toCity={wanted.toCity}
          />
          {formError && (
            <p role="alert" className="text-sm text-destructive">
              {formError}
            </p>
          )}
          <Button type="submit" size="lg" disabled={blocked || book.isPending}>
            {book.isPending ? 'Booking…' : 'Book delivery'}
          </Button>
        </div>
      </form>

      <Dialog
        open={!!created}
        onOpenChange={(o) => !o && setCreated(undefined)}
        title="Delivery booked"
        description="Share this tracking number with the recipient."
      >
        {created && (
          <div className="flex flex-col gap-4">
            <CopyField value={created.trackingNumber} label="tracking number" />
            <div className="flex flex-wrap gap-2">
              <Button
                variant="outline"
                onClick={async () => {
                  const r = await shareLink(trackingUrl(created.trackingNumber))
                  if (r === 'copied') toast.success('Tracking link copied')
                }}
              >
                Share tracking link
              </Button>
              <Button asChild>
                <Link to={`/deliveries/${created.id}`}>View delivery</Link>
              </Button>
              <Button
                variant="ghost"
                onClick={() => {
                  setCreated(undefined)
                  reset(bookingDefaults)
                }}
              >
                Book another
              </Button>
            </div>
          </div>
        )}
      </Dialog>
    </div>
  )
}

function clean(a: BookingForm['pickup']) {
  return { ...a, line2: a.line2 || undefined }
}
