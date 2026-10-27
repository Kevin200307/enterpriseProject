import type { FieldErrors, UseFormRegister } from 'react-hook-form'
import { FormField } from '@/components/common/FormField'
import { Input } from '@/components/ui/input'
import type { BookingForm } from './bookingSchema'

type Group = 'pickup' | 'dropoff'

export function AddressFields({
  group,
  register,
  errors,
  cityListId,
}: {
  group: Group
  register: UseFormRegister<BookingForm>
  errors: FieldErrors<BookingForm>
  cityListId: string
}) {
  const e = errors[group]
  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <FormField label="Contact name" error={e?.contactName?.message}>
        {(p) => <Input autoComplete="off" {...p} {...register(`${group}.contactName`)} />}
      </FormField>
      <FormField label="Phone" error={e?.phone?.message}>
        {(p) => <Input type="tel" autoComplete="off" {...p} {...register(`${group}.phone`)} />}
      </FormField>
      <div className="sm:col-span-2">
        <FormField label="Address line 1" error={e?.line1?.message}>
          {(p) => <Input autoComplete="off" {...p} {...register(`${group}.line1`)} />}
        </FormField>
      </div>
      <div className="sm:col-span-2">
        <FormField label="Address line 2 (optional)" error={e?.line2?.message}>
          {(p) => <Input autoComplete="off" {...p} {...register(`${group}.line2`)} />}
        </FormField>
      </div>
      <FormField label="City" error={e?.city?.message}>
        {(p) => (
          <Input list={cityListId} autoComplete="off" {...p} {...register(`${group}.city`)} />
        )}
      </FormField>
      <FormField label="Postal code" error={e?.postalCode?.message}>
        {(p) => (
          <Input
            inputMode="numeric"
            autoComplete="off"
            {...p}
            {...register(`${group}.postalCode`)}
          />
        )}
      </FormField>
    </div>
  )
}
