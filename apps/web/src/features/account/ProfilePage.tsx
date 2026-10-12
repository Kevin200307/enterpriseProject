import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Bell, ChevronRight, LogOut, Search } from 'lucide-react'
import { useEffect } from 'react'
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
import { profileSchema, type ProfileInput } from '@/features/auth/schemas'

export function ProfilePage() {
  const qc = useQueryClient()
  const { logout } = useAuth()
  const { roles } = useSession()
  const me = useQuery({ queryKey: qk.me, queryFn: usersApi.me })
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isDirty },
  } = useForm<ProfileInput>({ resolver: zodResolver(profileSchema) })

  useEffect(() => {
    if (me.data) reset({ fullName: me.data.fullName, phone: me.data.phone })
  }, [me.data, reset])

  const save = useMutation({
    mutationFn: usersApi.updateMe,
    onSuccess: (data) => {
      qc.setQueryData(qk.me, data)
      toast.success('Profile updated')
    },
    onError: (e) => {
      const extra = applyViolations(e, setError, ['fullName', 'phone'])
      toast.error([describeError(e).message, ...extra].join(' '))
    },
  })

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
            {(p) => <Input {...p} {...register('fullName')} />}
          </FormField>
          <FormField label="Phone" error={errors.phone?.message}>
            {(p) => <Input type="tel" {...p} {...register('phone')} />}
          </FormField>
          <Button type="submit" disabled={!isDirty || save.isPending}>
            {save.isPending ? 'Saving…' : 'Save changes'}
          </Button>
        </form>
      </CardContent>
      </Card>

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

      <Button
        variant="outline"
        className="h-12 w-full justify-center gap-2 rounded-2xl text-destructive hover:bg-destructive/5 hover:text-destructive"
        onClick={() => void logout()}
      >
        <LogOut className="h-4 w-4" aria-hidden />
        Sign out
      </Button>
    </div>
  )
}
