import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { toast } from 'sonner'
import { merchantApi } from '@/api/endpoints/merchant'
import { describeError } from '@/api/errorMessages'
import { qk } from '@/api/queryKeys'
import type { ApiKey } from '@/api/types'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { CopyOnceModal } from '@/components/common/CopyOnceModal'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { PageHeader } from '@/components/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDateTime } from '@/lib/format'
import { MerchantGate } from './MerchantGate'

const SCOPES = [
  { id: 'orders:write', label: 'Create orders' },
  { id: 'orders:read', label: 'Read orders' },
]

export function ApiKeysPage() {
  const qc = useQueryClient()
  const [creating, setCreating] = useState(false)
  const [label, setLabel] = useState('')
  const [scopes, setScopes] = useState<string[]>(['orders:write', 'orders:read'])
  // The plaintext key lives here only — never in the query or mutation cache.
  const [secret, setSecret] = useState<string>()
  const [revoking, setRevoking] = useState<ApiKey>()

  const list = useQuery({ queryKey: qk.merchant.keys, queryFn: merchantApi.apiKeys, retry: false })
  const create = useMutation({
    mutationFn: () => merchantApi.createApiKey(label.trim(), scopes),
    gcTime: 0,
    onSuccess: (created, _v, _c, ctx) => {
      setSecret(created.key)
      setCreating(false)
      setLabel('')
      ctx.client.getMutationCache().clear()
      void qc.invalidateQueries({ queryKey: qk.merchant.keys })
    },
    onError: (e) => toast.error(describeError(e).message),
  })
  const revoke = useMutation({
    mutationFn: (k: ApiKey) => merchantApi.revokeApiKey(k.id),
    onSuccess: () => toast.success('Key revoked'),
    onError: (e) => toast.error(describeError(e).message),
    onSettled: () => {
      setRevoking(undefined)
      void qc.invalidateQueries({ queryKey: qk.merchant.keys })
    },
  })
  const toggle = (id: string) =>
    setScopes((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]))

  return (
    <MerchantGate error={list.error}>
      <div className="flex flex-col gap-6">
        <PageHeader
          title="API keys"
          description="Use a key to create orders from your own systems."
          actions={<Button onClick={() => setCreating(true)}>Generate key</Button>}
        />
        {list.isPending ? (
          <Skeleton className="h-40 w-full" />
        ) : list.isError ? (
          <ErrorState error={list.error} onRetry={() => void list.refetch()} />
        ) : list.data.length === 0 ? (
          <EmptyState title="No API keys" description="Generate a key to start integrating." />
        ) : (
          <ul className="flex flex-col gap-3">
            {list.data.map((k) => (
              <li
                key={k.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-4"
              >
                <div className="min-w-0">
                  <p className="font-semibold">{k.label}</p>
                  <p className="font-mono text-sm text-muted-foreground">{k.prefix}…</p>
                  <p className="text-xs text-muted-foreground">
                    {k.scopes.join(', ')} ·{' '}
                    {k.lastUsedAt ? `Last used ${formatDateTime(k.lastUsedAt)}` : 'Never used'}
                    {k.expiresAt ? ` · Expires ${formatDateTime(k.expiresAt)}` : ''}
                  </p>
                </div>
                <Button
                  size="sm"
                  variant="destructive"
                  aria-label={`Revoke ${k.label}`}
                  onClick={() => setRevoking(k)}
                >
                  Revoke
                </Button>
              </li>
            ))}
          </ul>
        )}

        <Dialog open={creating} onOpenChange={setCreating} title="Generate API key">
          <form
            className="flex flex-col gap-4"
            onSubmit={(e) => {
              e.preventDefault()
              if (label.trim() && scopes.length) create.mutate()
            }}
          >
            <div className="flex flex-col gap-2">
              <Label htmlFor="key-label">Label</Label>
              <Input
                id="key-label"
                value={label}
                onChange={(e) => setLabel(e.target.value)}
                placeholder="e.g. Production server"
                autoComplete="off"
              />
            </div>
            <fieldset className="flex flex-col gap-2">
              <legend className="mb-1 text-sm font-medium">Permissions</legend>
              {SCOPES.map((s) => (
                <label key={s.id} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={scopes.includes(s.id)}
                    onChange={() => toggle(s.id)}
                  />{' '}
                  {s.label}
                </label>
              ))}
            </fieldset>
            <div className="flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={() => setCreating(false)}>
                Cancel
              </Button>
              <Button
                type="submit"
                disabled={!label.trim() || scopes.length === 0 || create.isPending}
              >
                {create.isPending ? 'Generating…' : 'Generate'}
              </Button>
            </div>
          </form>
        </Dialog>

        <CopyOnceModal
          secret={secret}
          title="Your new API key"
          label="API key"
          onClose={() => setSecret(undefined)}
        />

        <ConfirmDialog
          open={!!revoking}
          title={`Revoke ${revoking?.label}?`}
          description="Systems using this key stop working immediately. This cannot be undone."
          confirmLabel="Revoke key"
          destructive
          busy={revoke.isPending}
          onCancel={() => setRevoking(undefined)}
          onConfirm={() => revoking && revoke.mutate(revoking)}
        />
      </div>
    </MerchantGate>
  )
}
