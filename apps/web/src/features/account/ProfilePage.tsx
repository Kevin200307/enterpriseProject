import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, Check, ChevronRight, LogOut, Monitor, Moon, Search, Sun } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { usersApi } from '@/api/endpoints/auth'
import { useAuth } from '@/auth/authContext'
import { useSession } from '@/auth/session'
import { describeError } from '@/api/errorMessages'
import { applyViolations } from '@/api/formErrors'
import { qk } from '@/api/queryKeys'
import { ErrorState } from '@/components/common/ErrorState'
import { FormField } from '@/components/common/FormField'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/cn'
import { profileSchema, type ProfileInput } from '@/features/auth/schemas'
import {
  addressSchema,
  emptyAddress,
  type AddressInput,
} from '@/features/customer/bookingSchema'
import { readSavedDetails, saveSavedDetails } from '@/features/customer/savedDetails'
import { useTheme, type ThemePreference } from '@/theme/ThemeProvider'

/** Reads as text rather than an empty box while the form is locked. */
const readOnlyLook = cn(
  'read-only:border-transparent read-only:bg-muted read-only:text-muted-foreground',
)

export function ProfilePage() {
  const qc = useQueryClient()
  const { logout } = useAuth()
  const { roles } = useSession()
  const { preference, setPreference } = useTheme()
  const me = useQuery({ queryKey: qk.me, queryFn: usersApi.me })
  // The details read as a summary until asked for; editing is opt-in so a stray tap on a phone
  // cannot change a name.
  const [editing, setEditing] = useState(false)
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isDirty },
  } = useForm<ProfileInput>({ resolver: zodResolver(profileSchema) })
  const [editingDetails, setEditingDetails] = useState(false)
  const {
    register: registerDetails,
    handleSubmit: handleDetailsSubmit,
    reset: resetDetails,
    formState: { errors: detailErrors, isDirty: detailsDirty },
  } = useForm<AddressInput>({
    resolver: zodResolver(addressSchema),
    defaultValues: emptyAddress,
  })

  useEffect(() => {
    if (me.data) reset({ fullName: me.data.fullName, phone: me.data.phone })
  }, [me.data, reset])

  useEffect(() => {
    if (me.data) resetDetails(readSavedDetails(me.data.email))
  }, [me.data, resetDetails])

  const save = useMutation({
    mutationFn: usersApi.updateMe,
    onSuccess: (data) => {
      qc.setQueryData(qk.me, data)
      setEditing(false)
      toast.success('Profile updated')
    },
    onError: (e) => {
      const extra = applyViolations(e, setError, ['fullName', 'phone'])
      toast.error([describeError(e).message, ...extra].join(' '))
    },
  })
  const saveDetails = (details: AddressInput) => {
    if (!me.data) return
    saveSavedDetails(me.data.email, details)
    setEditingDetails(false)
    toast.success('Delivery details saved')
  }

  if (me.isPending) return <Skeleton className="h-64 max-w-md" />
  if (me.isError) return <ErrorState error={me.error} onRetry={() => void me.refetch()} />

  return (
    <div className="flex max-w-md flex-col gap-4">
      {/* Identity card — the dark surface the design language uses for headline info */}
      <div className="flex items-center gap-4 rounded-3xl bg-sidebar p-5 text-white">
        <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-2xl bg-accent text-xl font-bold text-accent-foreground">
          {(me.data.fullName || me.data.email).charAt(0).toUpperCase()}
        </span>
        <div className="min-w-0">
          <p className="truncate text-lg font-semibold">{me.data.fullName || 'Your profile'}</p>
          <p className="truncate text-sm text-white/60">{me.data.email}</p>
          {roles.length > 0 && (
            <div className="mt-2 flex flex-wrap gap-1.5">
              {roles.map((r) => (
                <span
                  key={r}
                  className="rounded-full bg-white/10 px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-white/80"
                >
                  {r.toLowerCase()}
                </span>
              ))}
            </div>
          )}
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Your details</CardTitle>
        </CardHeader>
        <CardContent>
          <form
            onSubmit={(e) => void handleSubmit((v) => save.mutate(v))(e)}
            noValidate
            className="flex flex-col gap-4"
          >
            <FormField label="Full name" error={errors.fullName?.message}>
              {(p) => (
                <Input
                  readOnly={!editing}
                  className={readOnlyLook}
                  {...p}
                  {...register('fullName')}
                />
              )}
            </FormField>
            <FormField label="Phone" error={errors.phone?.message}>
              {(p) => (
                <Input
                  type="tel"
                  readOnly={!editing}
                  className={readOnlyLook}
                  {...p}
                  {...register('phone')}
                />
              )}
            </FormField>
            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                className="flex-1"
                onClick={() => {
                  if (editing) reset({ fullName: me.data.fullName, phone: me.data.phone })
                  setEditing((e) => !e)
                }}
              >
                {editing ? 'Cancel' : 'Edit profile'}
              </Button>
              <Button
                type="submit"
                className="flex-1"
                disabled={!editing || !isDirty || save.isPending}
              >
                {save.isPending ? 'Saving…' : 'Save changes'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      {roles.includes('CUSTOMER') && (
        <Card>
          <CardHeader>
            <CardTitle>Saved delivery details</CardTitle>
            <p className="text-sm text-muted-foreground">
              Save your pickup contact and address to prefill new deliveries.
            </p>
          </CardHeader>
          <CardContent>
            <form
              onSubmit={(e) => void handleDetailsSubmit(saveDetails)(e)}
              noValidate
              className="flex flex-col gap-4"
            >
              <FormField label="Contact name" error={detailErrors.contactName?.message}>
                {(p) => (
                  <Input
                    readOnly={!editingDetails}
                    className={readOnlyLook}
                    {...p}
                    {...registerDetails('contactName')}
                  />
                )}
              </FormField>
              <FormField label="Delivery phone" error={detailErrors.phone?.message}>
                {(p) => (
                  <Input
                    type="tel"
                    readOnly={!editingDetails}
                    className={readOnlyLook}
                    {...p}
                    {...registerDetails('phone')}
                  />
                )}
              </FormField>
              <FormField label="Address line 1" error={detailErrors.line1?.message}>
                {(p) => (
                  <Input
                    readOnly={!editingDetails}
                    className={readOnlyLook}
                    {...p}
                    {...registerDetails('line1')}
                  />
                )}
              </FormField>
              <FormField label="Address line 2 (optional)" error={detailErrors.line2?.message}>
                {(p) => (
                  <Input
                    readOnly={!editingDetails}
                    className={readOnlyLook}
                    {...p}
                    {...registerDetails('line2')}
                  />
                )}
              </FormField>
              <div className="grid gap-4 sm:grid-cols-2">
                <FormField label="City" error={detailErrors.city?.message}>
                  {(p) => (
                    <Input
                      readOnly={!editingDetails}
                      className={readOnlyLook}
                      {...p}
                      {...registerDetails('city')}
                    />
                  )}
                </FormField>
                <FormField label="Postal code" error={detailErrors.postalCode?.message}>
                  {(p) => (
                    <Input
                      inputMode="numeric"
                      readOnly={!editingDetails}
                      className={readOnlyLook}
                      {...p}
                      {...registerDetails('postalCode')}
                    />
                  )}
                </FormField>
              </div>
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="outline"
                  className="flex-1"
                  onClick={() => {
                    if (editingDetails) resetDetails(readSavedDetails(me.data.email))
                    setEditingDetails((current) => !current)
                  }}
                >
                  {editingDetails ? 'Cancel' : 'Edit details'}
                </Button>
                <Button
                  type="submit"
                  className="flex-1"
                  disabled={!editingDetails || !detailsDirty}
                >
                  Save details
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}

      <nav aria-label="Account" className="overflow-hidden rounded-2xl border bg-card">
        <Link
          to="/account/notifications"
          className="flex items-center gap-3 px-4 py-3.5 text-sm font-medium transition-colors hover:bg-secondary"
        >
          <Bell className="h-4 w-4 text-muted-foreground" aria-hidden />
          Notifications
          <ChevronRight className="ml-auto h-4 w-4 text-muted-foreground" aria-hidden />
        </Link>
        <Link
          to="/track"
          className="flex items-center gap-3 border-t px-4 py-3.5 text-sm font-medium transition-colors hover:bg-secondary"
        >
          <Search className="h-4 w-4 text-muted-foreground" aria-hidden />
          Track a parcel
          <ChevronRight className="ml-auto h-4 w-4 text-muted-foreground" aria-hidden />
        </Link>
      </nav>

      <Card>
        <CardHeader>
          <CardTitle>Appearance</CardTitle>
          <p className="text-sm text-muted-foreground">
            Choose how SwiftDrop looks on this device.
          </p>
        </CardHeader>
        <CardContent className="grid grid-cols-3 gap-2">
          {([
            ['light', 'Light', Sun],
            ['dark', 'Dark', Moon],
            ['system', 'System', Monitor],
          ] as const).map(([value, label, Icon]) => (
            <button
              key={value}
              type="button"
              aria-pressed={preference === value}
              onClick={() => setPreference(value as ThemePreference)}
              className={cn(
                'relative flex min-w-0 flex-col items-center justify-center gap-1 rounded-xl border p-3 text-center transition-colors',
                value === 'light' && 'bg-white text-gray-900 hover:bg-gray-50',
                value === 'dark' && 'bg-black text-white hover:bg-gray-900',
                value === 'system' &&
                  'bg-[linear-gradient(to_right,#fff_0%,#fff_50%,#000_50%,#000_100%)] text-gray-900 hover:opacity-90',
                preference === value
                  ? 'border-accent'
                  : 'border-border',
              )}
            >
              <Icon
                className={cn(
                  'h-5 w-5 shrink-0 text-current opacity-70',
                  value === 'system' && 'rounded bg-white/90 p-0.5 text-gray-700 opacity-100',
                )}
                aria-hidden
              />
              <span
                className={cn(
                  'min-w-0 flex-1',
                  value === 'system' && 'rounded bg-white/90 px-1 text-gray-900 shadow-sm',
                )}
              >
                <span className="block text-sm font-medium">{label}</span>
              </span>
              {preference === value && (
                <Check className="absolute right-2 top-2 h-4 w-4 text-primary" aria-hidden />
              )}
            </button>
          ))}
        </CardContent>
      </Card>

      <Button
        variant="outline"
        className="h-12 w-full justify-center gap-2 rounded-2xl text-destructive hover:bg-destructive/5 hover:text-destructive active:!border-destructive active:!bg-destructive active:!text-destructive-foreground"
        onClick={() => void logout()}
      >
        <LogOut className="h-4 w-4" aria-hidden />
        Sign out
      </Button>
    </div>
  )
}
