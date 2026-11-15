import { ArrowLeft, RotateCcw, SendHorizontal, Square, Wrench, X } from 'lucide-react'
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
  // One line to start, growing with the message, so the placeholder sits on the send button's
  // line instead of floating above it.
  useEffect(() => {
    const el = input.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, 128)}px`
  }, [draft])
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
      className="fixed inset-0 z-50 flex flex-col overflow-hidden bg-background shadow-2xl sm:inset-auto sm:bottom-20 sm:right-4 sm:h-[34rem] sm:w-96 sm:rounded-3xl sm:border"
    >
      {/* The lime rises from the composer and fades out up the conversation. */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-0 bottom-0 z-0 h-2/5 bg-gradient-to-t from-accent/40 to-accent/0"
      />

      <header className="relative z-10 flex items-center gap-1 px-2 py-2 pt-[calc(0.5rem+env(safe-area-inset-top))] sm:pt-2">
        {/* Back, as on every other screen; the floating desktop panel keeps a close cross. */}
        <Button
          variant="ghost"
          size="icon"
          className="rounded-xl sm:hidden"
          aria-label="Back"
          onClick={onClose}
        >
          <ArrowLeft className="h-5 w-5" />
        </Button>
        <h2 className="truncate pl-2 font-semibold sm:pl-2">Assistant</h2>
        <div className="ml-auto flex gap-1">
          <Button
            variant="ghost"
            size="icon"
            className="rounded-xl"
            aria-label="New chat"
            disabled={messages.length === 0}
            onClick={() => void reset()}
          >
            <RotateCcw className="h-4 w-4" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="hidden rounded-xl sm:inline-flex"
            aria-label="Close assistant"
            onClick={onClose}
          >
            <X className="h-4 w-4" />
          </Button>
        </div>
      </header>

      {messages.length === 0 ? (
        <div className="relative z-10 flex flex-1 flex-col justify-center gap-2 p-4">
          <p className="text-sm text-muted-foreground">
            Ask about a parcel, a price, or your deliveries.
          </p>
          {SUGGESTIONS.map((s) => (
            <button
              key={s}
              type="button"
              onClick={() => void send(s)}
              className="rounded-2xl border bg-card p-3.5 text-left text-sm transition-colors hover:bg-secondary active:scale-[0.99] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
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
          className="relative z-10 flex flex-1 flex-col gap-3 overflow-y-auto p-4"
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
        className="relative z-10 flex flex-col gap-1 p-3"
        style={{ paddingBottom: 'max(0.75rem, env(safe-area-inset-bottom))' }}
      >
        {/* The ring belongs to the pill, not the bare textarea inside it. */}
        <div className="flex items-center gap-2 rounded-[1.75rem] border bg-card py-1.5 pl-4 pr-1.5 shadow-sm focus-within:ring-2 focus-within:ring-ring focus-within:ring-offset-2 ring-offset-background">
          <label htmlFor="chat-input" className="sr-only">
            Message
          </label>
          <textarea
            id="chat-input"
            ref={input}
            value={draft}
            rows={1}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault()
                submit()
              }
            }}
            className="max-h-32 min-w-0 flex-1 resize-none border-0 bg-transparent py-2 text-sm leading-6 outline-none focus-visible:outline-none focus-visible:ring-0"
            placeholder="Ask SwiftDrop…"
          />
          {busy ? (
            <Button
              type="button"
              variant="outline"
              size="icon"
              className="shrink-0 rounded-full"
              aria-label="Stop"
              onClick={stop}
            >
              <Square className="h-4 w-4" />
            </Button>
          ) : (
            <Button
              type="submit"
              size="icon"
              className="shrink-0 rounded-full bg-accent text-accent-foreground hover:brightness-95"
              aria-label="Send"
              disabled={!draft.trim() || tooLong}
            >
              <SendHorizontal className="h-4 w-4" />
            </Button>
          )}
        </div>
        <p
          className={cn(
            'px-2 text-right text-xs',
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
