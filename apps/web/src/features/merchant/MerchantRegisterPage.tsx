import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { isApiError } from '@/api/ApiError'
import { merchantApi } from '@/api/endpoints/merchant'
import { describeError } from '@/api/errorMessages'
import { applyViolations } from '@/api/formErrors'
import { FormField } from '@/components/common/FormField'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { phoneSchema } from '@/features/auth/schemas'

const schema = z.object({
  orgName: z.string().trim().min(1, 'Organisation name is required').max(120),
  businessRegNo: z.string().trim().min(1, 'Registration number is required').max(40),
  contactEmail: z.string().min(1, 'Contact email is required').email('Enter a valid email'),
  contactPhone: phoneSchema,
})
type Form = z.infer<typeof schema>

export function MerchantRegisterPage() {
  const navigate = useNavigate()
  const [error, setError] = useState<string>()
  const [already, setAlready] = useState(false)
  const {
    register,
    handleSubmit,
    setError: setFieldError,
    formState: { errors },
  } = useForm<Form>({ resolver: zodResolver(schema), defaultValues: { contactPhone: '+94' } })
  const apply = useMutation({
    mutationFn: merchantApi.register,
    onSuccess: () => navigate('/merchant/pending', { replace: true }),
    onError: (e) => {
      if (isApiError(e) && e.errorCode === 'CONFLICT') return setAlready(true)
      const extra = applyViolations(e, setFieldError, [
        'orgName',
        'businessRegNo',
        'contactEmail',
        'contactPhone',
      ])
      setError([describeError(e).message, ...extra].join(' '))
    },
  })
  return (
    <div className="mx-auto flex max-w-lg flex-col gap-6">
      <PageHeader
        title="Become a merchant"
        description="Send parcels in bulk and integrate with our API. An admin reviews every application."
      />
      <Card>
        <CardContent className="pt-6">
          <form
            noValidate
            onSubmit={(e) => {
              setError(undefined)
              setAlready(false)
              void handleSubmit((v) => apply.mutate(v))(e)
            }}
            className="flex flex-col gap-4"
          >
            <FormField label="Organisation name" error={errors.orgName?.message}>
              {(p) => <Input {...p} {...register('orgName')} />}
            </FormField>
            <FormField label="Business registration number" error={errors.businessRegNo?.message}>
              {(p) => <Input {...p} {...register('businessRegNo')} />}
            </FormField>
            <FormField label="Contact email" error={errors.contactEmail?.message}>
              {(p) => <Input type="email" {...p} {...register('contactEmail')} />}
            </FormField>
            <FormField label="Contact phone" error={errors.contactPhone?.message}>
              {(p) => <Input type="tel" {...p} {...register('contactPhone')} />}
            </FormField>
            {already && (
              <p role="alert" className="text-sm text-destructive">
                You already have a merchant application.{' '}
                <Link className="underline" to="/merchant/pending">
                  Check its status
                </Link>
              </p>
            )}
            {error && (
              <p role="alert" className="text-sm text-destructive">
                {error}
              </p>
            )}
            <Button type="submit" disabled={apply.isPending}>
              {apply.isPending ? 'Submitting…' : 'Submit application'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  )
}
