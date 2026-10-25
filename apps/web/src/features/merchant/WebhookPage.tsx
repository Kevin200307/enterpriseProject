import { useMutation, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { merchantApi } from '@/api/endpoints/merchant'
import { describeError } from '@/api/errorMessages'
import { qk } from '@/api/queryKeys'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { CopyOnceModal } from '@/components/common/CopyOnceModal'
import { FormField } from '@/components/common/FormField'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { cn } from '@/lib/cn'
import { MerchantGate } from './MerchantGate'
import { SNIPPETS, validateWebhookUrl } from './snippets'

export function WebhookPage() {
  const me = useQuery({ queryKey: qk.merchant.me, queryFn: merchantApi.me, retry: false })
  const [url, setUrl] = useState<string>()
  const [error, setError] = useState<string>()
  const [confirm, setConfirm] = useState(false)
  const [secret, setSecret] = useState<string>()
  const [lang, setLang] = useState<keyof typeof SNIPPETS>('Node.js')
  const value = url ?? me.data?.webhookUrl ?? ''

  const save = useMutation({
    mutationFn: () => merchantApi.setWebhook(value.trim()),
    gcTime: 0,
    onSuccess: (r, _v, _c, ctx) => {
      setSecret(r.secret)
      setConfirm(false)
      ctx.client.getMutationCache().clear()
      void me.refetch()
    },
    onError: (e) => {
      setConfirm(false)
      setError(describeError(e).message)
    },
  })

  return (
    <MerchantGate error={me.error}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="Webhook"
          description="We POST a signed event to this URL every time one of your parcels changes status."
        />
        <Card>
          <CardContent className="pt-6">
            <form
              noValidate
              className="flex flex-col gap-4"
              onSubmit={(e) => {
                e.preventDefault()
                const problem = validateWebhookUrl(value)
                setError(problem)
                if (!problem) setConfirm(true)
              }}
            >
              <FormField
                label="Endpoint URL"
                error={error}
                hint={me.data?.webhookUrl ? undefined : 'No webhook configured yet.'}
              >
                {(p) => (
                  <Input
                    type="url"
                    {...p}
                    value={value}
                    onChange={(e) => setUrl(e.target.value)}
                    placeholder="https://example.com/hooks/swiftdrop"
                  />
                )}
              </FormField>
              <Button type="submit" className="self-start" disabled={save.isPending}>
                Save webhook
              </Button>
            </form>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Verifying signatures</CardTitle>
            <p className="text-sm text-muted-foreground">
              Each request carries{' '}
              <code className="font-mono">
                X-SwiftDrop-Signature: t=&lt;unix&gt;,v1=&lt;hex&gt;
              </code>
              , an HMAC-SHA256 of <code className="font-mono">t.body</code> using your secret.
              Reject mismatches and old timestamps.
            </p>
          </CardHeader>
          <CardContent className="flex flex-col gap-3">
            <div role="tablist" aria-label="Language" className="flex gap-1 border-b">
              {(Object.keys(SNIPPETS) as (keyof typeof SNIPPETS)[]).map((l) => (
                <button
                  key={l}
                  role="tab"
                  type="button"
                  aria-selected={lang === l}
                  onClick={() => setLang(l)}
                  className={cn(
                    '-mb-px border-b-2 px-3 py-1.5 text-sm font-medium',
                    lang === l
                      ? 'border-primary text-primary'
                      : 'border-transparent text-muted-foreground',
                  )}
                >
                  {l}
                </button>
              ))}
            </div>
            <pre role="tabpanel" className="overflow-x-auto rounded-md bg-muted p-4 text-xs">
              <code>{SNIPPETS[lang]}</code>
            </pre>
          </CardContent>
        </Card>

        <ConfirmDialog
          open={confirm}
          title="Save webhook?"
          description={
            me.data?.webhookUrl
              ? 'Saving generates a NEW signing secret. Your current secret stops working, so update your server right away.'
              : 'A signing secret is generated and shown once.'
          }
          confirmLabel="Save and show secret"
          busy={save.isPending}
          onCancel={() => setConfirm(false)}
          onConfirm={() => save.mutate()}
        />
        <CopyOnceModal
          secret={secret}
          title="Your webhook signing secret"
          label="signing secret"
          onClose={() => setSecret(undefined)}
        />
      </div>
    </MerchantGate>
  )
}
