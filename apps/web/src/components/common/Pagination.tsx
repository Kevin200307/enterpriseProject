import { Button } from '@/components/ui/button'

export function Pagination({
  page,
  totalPages,
  onChange,
}: {
  page: number
  totalPages: number
  onChange: (page: number) => void
}) {
  if (totalPages <= 1) return null
  return (
    <nav aria-label="Pagination" className="flex items-center justify-between gap-3">
      <Button variant="outline" size="sm" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        Previous
      </Button>
      <span className="text-sm text-muted-foreground">
        Page {page + 1} of {totalPages}
      </span>
      <Button
        variant="outline"
        size="sm"
        disabled={page >= totalPages - 1}
        onClick={() => onChange(page + 1)}
      >
        Next
      </Button>
    </nav>
  )
}
