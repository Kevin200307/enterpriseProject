import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { adminApi } from '@/api/endpoints/admin'
import { describeError } from '@/api/errorMessages'
import { qk } from '@/api/queryKeys'
import type { AdminUser } from '@/api/types'
import { ConfirmDialog } from '@/components/common/ConfirmDialog'
import { EmptyState } from '@/components/common/EmptyState'
import { ErrorState } from '@/components/common/ErrorState'
import { PageHeader } from '@/components/common/PageHeader'
import { Pagination } from '@/components/common/Pagination'
import { Button } from '@/components/ui/button'
import { Dialog } from '@/components/ui/dialog'
import { Label } from '@/components/ui/label'
import { Skeleton } from '@/components/ui/skeleton'
import { useAuth } from '@/auth/authContext'
import { ALL_ROLES } from '@/auth/types'

function RolesDialog({
  user,
  isSelf,
  onClose,
}: {
  user: AdminUser
  isSelf: boolean
  onClose: () => void
}) {
  const qc = useQueryClient()
  const [roles, setRoles] = useState<string[]>(user.roles)
  const save = useMutation({
    mutationFn: () => adminApi.setUserRoles(user.id, roles),
    onSuccess: () => {
      toast.success('Roles updated')
      onClose()
    },
    onError: (e) => toast.error(describeError(e).message),
    onSettled: () => void qc.invalidateQueries({ queryKey: qk.admin.all }),
  })
  const toggle = (r: string) =>
    setRoles((cur) => (cur.includes(r) ? cur.filter((x) => x !== r) : [...cur, r]))
  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={`Roles for ${user.fullName}`}
      description={user.email}
    >
      <div className="flex flex-col gap-4">
        <fieldset className="flex flex-col gap-2">
          <legend className="sr-only">Roles</legend>
          {ALL_ROLES.map((r) => {
            // Lockout guard: an admin cannot remove their own ADMIN role.
            const locked = isSelf && r === 'ADMIN'
            return (
              <label key={r} className="flex items-center gap-3 text-sm">
                <input
                  type="checkbox"
                  checked={roles.includes(r)}
                  disabled={locked}
                  onChange={() => toggle(r)}
                />
                {r}
                {locked && (
                  <span className="text-xs text-muted-foreground">
                    (you can&apos;t remove your own admin access)
                  </span>
                )}
              </label>
            )
          })}
        </fieldset>
        <div className="flex justify-end gap-2">
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button disabled={save.isPending || roles.length === 0} onClick={() => save.mutate()}>
            {save.isPending ? 'Saving…' : 'Save roles'}
          </Button>
        </div>
      </div>
    </Dialog>
  )
}

export function UsersPage() {
  const qc = useQueryClient()
  const { claims } = useAuth()
  const [params, setParams] = useSearchParams()
  const role = params.get('role') ?? ''
  const status = params.get('status') ?? ''
  const page = Math.max(0, Number(params.get('page')) || 0)
  const [editing, setEditing] = useState<AdminUser>()
  const [toggling, setToggling] = useState<AdminUser>()

  const filters = { role, status, page }
  const list = useQuery({
    queryKey: qk.admin.users(filters),
    queryFn: () => adminApi.users({ role: role || undefined, status: status || undefined, page }),
    placeholderData: (prev) => prev,
  })
  const setStatus = useMutation({
    mutationFn: (u: AdminUser) =>
      adminApi.setUserStatus(u.id, u.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'),
    onSuccess: () => toast.success('User updated'),
    onError: (e) => toast.error(describeError(e).message),
    onSettled: () => {
      setToggling(undefined)
      void qc.invalidateQueries({ queryKey: qk.admin.all })
    },
  })
  const setFilter = (key: string, v: string) => {
    const next = new URLSearchParams(params)
    if (v) next.set(key, v)
    else next.delete(key)
    next.delete('page')
    setParams(next)
  }
  const suspending = toggling?.status === 'ACTIVE'

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Users" />
      <div className="flex flex-wrap gap-3">
        {[
          { id: 'role', label: 'Role', value: role, options: ALL_ROLES as string[] },
          { id: 'status', label: 'Status', value: status, options: ['ACTIVE', 'SUSPENDED'] },
        ].map((f) => (
          <div key={f.id} className="flex flex-col gap-1">
            <Label htmlFor={`f-${f.id}`} className="text-xs">
              {f.label}
            </Label>
            <select
              id={`f-${f.id}`}
              value={f.value}
              onChange={(e) => setFilter(f.id, e.target.value)}
              className="h-10 rounded-md border border-input bg-background px-3 text-sm"
            >
              <option value="">All</option>
              {f.options.map((o) => (
                <option key={o} value={o}>
                  {o}
                </option>
              ))}
            </select>
          </div>
        ))}
      </div>

      {list.isPending ? (
        <Skeleton className="h-64 w-full" />
      ) : list.isError ? (
        <ErrorState error={list.error} onRetry={() => void list.refetch()} />
      ) : list.data.content.length === 0 ? (
        <EmptyState title="No users match" />
      ) : (
        <>
          <div className="overflow-x-auto rounded-lg border">
            <table className="w-full text-sm">
              <thead className="bg-muted text-left">
                <tr>
                  <th scope="col" className="p-3 font-medium">
                    User
                  </th>
                  <th scope="col" className="p-3 font-medium">
                    Roles
                  </th>
                  <th scope="col" className="p-3 font-medium">
                    Status
                  </th>
                  <th scope="col" className="p-3 font-medium">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {list.data.content.map((u) => (
                  <tr key={u.id} className="border-t">
                    <th scope="row" className="p-3 text-left font-normal">
                      <div className="font-medium">{u.fullName}</div>
                      <div className="text-muted-foreground">{u.email}</div>
                    </th>
                    <td className="p-3">{u.roles.join(', ')}</td>
                    <td className="p-3">{u.status === 'ACTIVE' ? 'Active' : 'Suspended'}</td>
                    <td className="flex justify-end gap-2 p-3">
                      <Button
                        size="sm"
                        variant="outline"
                        aria-label={`Edit roles for ${u.fullName}`}
                        onClick={() => setEditing(u)}
                      >
                        Roles
                      </Button>
                      <Button
                        size="sm"
                        variant={u.status === 'ACTIVE' ? 'destructive' : 'secondary'}
                        aria-label={`${u.status === 'ACTIVE' ? 'Suspend' : 'Reactivate'} ${u.fullName}`}
                        disabled={u.id === claims?.sub}
                        title={u.id === claims?.sub ? "You can't suspend yourself" : undefined}
                        onClick={() => setToggling(u)}
                      >
                        {u.status === 'ACTIVE' ? 'Suspend' : 'Reactivate'}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={list.data.number}
            totalPages={list.data.totalPages}
            onChange={(n) => {
              const next = new URLSearchParams(params)
              next.set('page', String(n))
              setParams(next)
            }}
          />
        </>
      )}

      {editing && (
        <RolesDialog
          user={editing}
          isSelf={editing.id === claims?.sub}
          onClose={() => setEditing(undefined)}
        />
      )}
      <ConfirmDialog
        open={!!toggling}
        title={suspending ? `Suspend ${toggling?.fullName}?` : `Reactivate ${toggling?.fullName}?`}
        description={
          suspending
            ? 'They are signed out everywhere immediately and cannot sign in until reactivated.'
            : 'They will be able to sign in again.'
        }
        confirmLabel={suspending ? 'Suspend' : 'Reactivate'}
        destructive={suspending}
        busy={setStatus.isPending}
        onCancel={() => setToggling(undefined)}
        onConfirm={() => toggling && setStatus.mutate(toggling)}
      />
    </div>
  )
}
