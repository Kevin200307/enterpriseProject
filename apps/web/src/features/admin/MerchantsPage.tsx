import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { adminApi } from '@/api/endpoints/admin'
import { describeError } from '@/api/errorMessages'
import { qk } from '@/api/queryKeys'
import type { Merchant, MerchantStatus } from '@/api/types'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { PageHeader } from '@/components/common/PageHeader'
import { Pagination } from '@/components/common/Pagination'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { formatDateTime } from '@/lib/format'
import { cn } from '@/lib/cn'

const TABS: { status: MerchantStatus; label: string }[] = [
  { status: 'PENDING', label: 'Pending' },
  { status: 'APPROVED', label: 'Approved' },
  { status: 'SUSPENDED', label: 'Suspended' },
  { status: 'REJECTED', label: 'Rejected' },
]

type Action = 'approve' | 'reject' | 'suspend'
const ACTIONS: Record<MerchantStatus, { action: Action; label: string; destructive?: boolean }[]> =
  {
    PENDING: [
      { action: 'approve', label: 'Approve' },
      { action: 'reject', label: 'Reject', destructive: true },
    ],
    APPROVED: [{ action: 'suspend', label: 'Suspend', destructive: true }],
    SUSPENDED: [{ action: 'approve', label: 'Reactivate' }],
    REJECTED: [],
  }

export function MerchantsPage() {
  const qc = useQueryClient()
  const [params, setParams] = useSearchParams()
  const status = (TABS.find((t) => t.status === params.get('status'))?.status ??
    'PENDING') as MerchantStatus
  const page = Math.max(0, Number(params.get('page')) || 0)
  const [deciding, setDeciding] = useState<{
    m: Merchant
    action: Action
    label: string
    destructive?: boolean
  }>()
  const [note, setNote] = useState('')

  const list = useQuery({
    queryKey: qk.admin.merchants({ status, page }),
    queryFn: () => adminApi.merchants({ status, page }),
    placeholderData: (prev) => prev,
  })
  const decide = useMutation({
    mutationFn: () =>
      adminApi.decideMerchant(deciding!.m.id, deciding!.action, note.trim() || undefined),
    onSuccess: () => toast.success(`${deciding?.m.orgName}: ${deciding?.label.toLowerCase()} done`),
    onError: (e) => toast.error(describeError(e).message),
    onSettled: () => {
      setDeciding(undefined)
      setNote('')
      void qc.invalidateQueries({ queryKey: qk.admin.all })
    },
  })

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Merchants" />
      <div role="tablist" aria-label="Merchant status" className="flex gap-1 border-b">
        {TABS.map((t) => (
          <button
            key={t.status}
            role="tab"
            type="button"
            aria-selected={status === t.status}
            onClick={() => setParams({ status: t.status })}
            className={cn(
              '-mb-px border-b-2 px-4 py-2 text-sm font-medium focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
              status === t.status
                ? 'border-primary text-primary'
                : 'border-transparent text-muted-foreground hover:text-foreground',
            )}
          >
            {t.label}
          </button>
        ))}
      </div>

      {list.isPending ? (
        <Skeleton className="h-48 w-full" />
      ) : list.isError ? (
        <ErrorState error={list.error} onRetry={() => void list.refetch()} />
      ) : list.data.content.length === 0 ? (
        <EmptyState title={`No ${status.toLowerCase()} merchants`} />
      ) : (
        <>
          <ul className="flex flex-col gap-3">
            {list.data.content.map((m) => (
              <li
                key={m.id}
                className="flex flex-wrap items-center justify-between gap-3 rounded-lg border p-4"
              >
                <div className="min-w-0">
                  <p className="font-semibold">{m.orgName}</p>
                  <p className="text-sm text-muted-foreground">
                    Reg. {m.businessRegNo} · {m.contactEmail} · {m.contactPhone}
                  </p>
                  {m.approvedAt && (
                    <p className="text-xs text-muted-foreground">
                      Decided {formatDateTime(m.approvedAt)}
                      {m.approvedBy ? ` by ${m.approvedBy}` : ''}
                    </p>
                  )}
                </div>
                <div className="flex gap-2">
                  {ACTIONS[m.status].map((a) => (
                    <Button
                      key={a.action}
                      size="sm"
                      variant={a.destructive ? 'destructive' : 'default'}
                      aria-label={`${a.label} ${m.orgName}`}
                      onClick={() => setDeciding({ m, ...a })}
                    >
                      {a.label}
                    </Button>
                  ))}
                </div>
              </li>
            ))}
          </ul>
          <Pagination
            page={list.data.number}
            totalPages={list.data.totalPages}
            onChange={(n) => setParams({ status, page: String(n) })}
          />
        </>
      )}

      <ConfirmDialog
        open={!!deciding}
        title={`${deciding?.label} ${deciding?.m.orgName}?`}
        confirmLabel={deciding?.label ?? ''}
        destructive={deciding?.destructive}
        busy={decide.isPending}
        onCancel={() => {
          setDeciding(undefined)
          setNote('')
        }}
        onConfirm={() => decide.mutate()}
      >
        <div className="flex flex-col gap-2">
          <Label htmlFor="decision-note">Note (optional)</Label>
          <Input id="decision-note" value={note} onChange={(e) => setNote(e.target.value)} />
        </div>
      </ConfirmDialog>
    </div>
  )
}
