import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useForm } from 'react-hook-form'
import { toast } from 'sonner'
import { usersApi } from '@/api/endpoints/auth'
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
    <Card className="max-w-md">
      <CardHeader>
        <CardTitle>Your profile</CardTitle>
        <p className="text-sm text-muted-foreground">{me.data.email}</p>
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
  )
}
