import { RotateCcw, SendHorizontal, Square, Wrench, X } from 'lucide-react'
import { lazy, Suspense, useEffect, useRef, useState } from 'react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/cn'
import { MAX_INPUT, useChat, type ChatMessage } from './useChat'

const Markdown = lazy(() => import('./Markdown'))

const SUGGESTIONS = [
  'How much to send 2 kg from Colombo to Kandy?',
  'Show my parcels in transit',
  'Where is my latest parcel?',
]

function Bubble({ m }: { m: ChatMessage }) {
  const mine = m.role === 'user'
  return (
    <li className={cn('flex flex-col gap-1', mine ? 'items-end' : 'items-start')}>
      <div
        className={cn(
          'max-w-[85%] rounded-2xl px-3.5 py-2 text-sm',
          mine ? 'bg-primary text-primary-foreground' : 'bg-muted',
        )}
      >
        {mine ? (
          <p className="whitespace-pre-wrap break-words">{m.text}</p>
        ) : m.text ? (
          <Suspense fallback={<p className="whitespace-pre-wrap">{m.text}</p>}>
            <Markdown>{m.text}</Markdown>
          </Suspense>
        ) : m.streaming && !m.notice ? (
          <span className="text-muted-foreground">Thinking…</span>
        ) : null}
        {m.notice && <p className="mt-1 text-xs italic opacity-80">{m.notice}</p>}
      </div>
      {m.tools.length > 0 && (
        <ul className="flex flex-wrap gap-1.5" aria-label="Tools used">
          {m.tools.map((t, i) => (
            <li key={i}>
              <details className="rounded-md border bg-background text-xs">
                <summary className="flex cursor-pointer items-center gap-1 px-2 py-1">
                  <Wrench className="h-3 w-3" aria-hidden /> {t.name}
                </summary>
                <pre className="max-w-64 overflow-x-auto border-t p-2">
                  {JSON.stringify(t.arguments ?? {}, null, 2)}
                </pre>
              </details>
            </li>
          ))}
        </ul>
      )}
    </li>
  )
}

export function ChatPanel({
  onClose,
  onUnavailable,
}: {
  onClose: () => void
  onUnavailable: () => void
}) {
  const { messages, busy, send, stop, reset } = useChat({ active: true, onUnavailable })
  const [draft, setDraft] = useState('')
  const input = useRef<HTMLTextAreaElement>(null)
  const log = useRef<HTMLUListElement>(null)

  useEffect(() => input.current?.focus(), [])
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && onClose()
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])
  useEffect(() => {
    log.current?.scrollTo?.({ top: log.current.scrollHeight })
  }, [messages])

  const tooLong = draft.length > MAX_INPUT
  const submit = () => {
    if (!draft.trim() || tooLong || busy) return
    const text = draft
    setDraft('')
    void send(text)
  }

  return (
    <section
      role="dialog"
      aria-label="SwiftDrop assistant"
      className="fixed inset-0 z-50 flex flex-col bg-background shadow-2xl sm:inset-auto sm:bottom-20 sm:right-4 sm:h-[34rem] sm:w-96 sm:rounded-xl sm:border"
    >
      <header className="flex items-center justify-between border-b px-4 py-3">
        <h2 className="font-semibold">Assistant</h2>
        <div className="flex gap-1">
          <Button
            variant="ghost"
            size="icon"
            aria-label="New chat"
            disabled={messages.length === 0}
            onClick={() => void reset()}
          >
            <RotateCcw className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="icon" aria-label="Close assistant" onClick={onClose}>
            <X className="h-4 w-4" />
          </Button>
        </div>
      </header>

      {messages.length === 0 ? (
        <div className="flex flex-1 flex-col justify-center gap-2 p-4">
          <p className="text-sm text-muted-foreground">
            Ask about a parcel, a price, or your deliveries.
          </p>
          {SUGGESTIONS.map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => void send(s)}
              className="rounded-lg border p-3 text-left text-sm hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              {s}
            </button>
          ))}
        </div>
      ) : (
        <ul
          ref={log}
          role="log"
          aria-live="polite"
          aria-label="Conversation"
          className="flex flex-1 flex-col gap-3 overflow-y-auto p-4"
        >
          {messages.map((m) => (
            <Bubble key={m.id} m={m} />
          ))}
        </ul>
      )}

      <form
        onSubmit={(e) => {
          e.preventDefault()
          submit()
        }}
        className="flex flex-col gap-1 border-t p-3"
      >
        <div className="flex items-end gap-2">
          <label htmlFor="chat-input" className="sr-only">
            Message
          </label>
          <textarea
            id="chat-input"
            ref={input}
            value={draft}
            rows={2}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault()
                submit()
              }
            }}
            className="min-h-11 flex-1 resize-none rounded-md border border-input bg-background px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            placeholder="Ask SwiftDrop…"
          />
          {busy ? (
            <Button type="button" variant="outline" size="icon" aria-label="Stop" onClick={stop}>
              <Square className="h-4 w-4" />
            </Button>
          ) : (
            <Button type="submit" size="icon" aria-label="Send" disabled={!draft.trim() || tooLong}>
              <SendHorizontal className="h-4 w-4" />
            </Button>
          )}
        </div>
        <p
          className={cn(
            'text-right text-xs',
            tooLong ? 'text-destructive' : 'text-muted-foreground',
          )}
          aria-live="polite"
        >
          {draft.length}/{MAX_INPUT}
        </p>
      </form>
    </section>
  )
}
