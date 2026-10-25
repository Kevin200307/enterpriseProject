import { useCallback, useEffect, useRef, useState } from 'react'
import { isApiError } from '@/api/ApiError'
import { assistantApi } from '@/api/endpoints/assistant'
import { describeError } from '@/api/errorMessages'
import type { ToolCall } from '@/api/types'
import { parseSse } from '@/lib/sse'

export const MAX_INPUT = 2000
const CONV_KEY = 'sd-conv'

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  text: string
  tools: ToolCall[]
  /** Set when this assistant turn ended in an error or was stopped. */
  notice?: string
  streaming?: boolean
}

const store = {
  get: () => {
    try {
      return sessionStorage.getItem(CONV_KEY) ?? undefined
    } catch {
      return undefined
    }
  },
  set: (id: string | undefined) => {
    try {
      if (id) sessionStorage.setItem(CONV_KEY, id)
      else sessionStorage.removeItem(CONV_KEY)
    } catch {
      // storage blocked: the conversation simply won't survive a reload
    }
  },
}

let seq = 0
const nextId = () => `m${++seq}`

/** Maps a failed request to what the user should read. `unavailable` means the assistant is switched off. */
export function chatFailure(e: unknown): { notice: string; unavailable: boolean } {
  if (isApiError(e) && e.errorCode === 'RATE_LIMITED') {
    return {
      notice: "You've reached the hourly message limit. Please try again later.",
      unavailable: false,
    }
  }
  if (isApiError(e) && e.status === 503) {
    return { notice: 'The assistant is unavailable right now.', unavailable: true }
  }
  return { notice: describeError(e).message, unavailable: false }
}

export function useChat({ active, onUnavailable }: { active: boolean; onUnavailable: () => void }) {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [busy, setBusy] = useState(false)
  const conversationId = useRef<string | undefined>(store.get())
  const abort = useRef<AbortController>()
  const rehydrated = useRef(false)

  // Rehydrate once, the first time the panel opens.
  useEffect(() => {
    if (!active || rehydrated.current) return
    rehydrated.current = true
    const id = conversationId.current
    if (!id) return
    assistantApi
      .conversation(id)
      .then((c) =>
        setMessages(
          c.messages.map((m) => ({ id: nextId(), role: m.role, text: m.content, tools: [] })),
        ),
      )
      .catch(() => {
        // another user's or expired conversation (404): start fresh
        conversationId.current = undefined
        store.set(undefined)
      })
  }, [active])

  useEffect(() => () => abort.current?.abort(), [])

  const patchLast = (fn: (m: ChatMessage) => ChatMessage) =>
    setMessages((all) => all.map((m, i) => (i === all.length - 1 ? fn(m) : m)))

  const send = useCallback(
    async (raw: string) => {
      const text = raw.trim()
      if (!text || text.length > MAX_INPUT || abort.current) return
      const ctrl = new AbortController()
      abort.current = ctrl
      setBusy(true)
      setMessages((all) => [
        ...all,
        { id: nextId(), role: 'user', text, tools: [] },
        { id: nextId(), role: 'assistant', text: '', tools: [], streaming: true },
      ])
      try {
        const res = await assistantApi.stream(text, conversationId.current, ctrl.signal)
        if (!res.body) throw new Error('empty stream')
        for await (const ev of parseSse(res.body)) {
          const data = ev.data ? (JSON.parse(ev.data) as Record<string, unknown>) : {}
          if (ev.event === 'token')
            patchLast((m) => ({ ...m, text: m.text + String(data.text ?? '') }))
          else if (ev.event === 'tool')
            patchLast((m) => ({
              ...m,
              tools: [
                ...m.tools,
                { name: String(data.name), arguments: data.arguments as ToolCall['arguments'] },
              ],
            }))
          else if (ev.event === 'done') {
            if (typeof data.conversationId === 'string') {
              conversationId.current = data.conversationId
              store.set(data.conversationId)
            }
          } else if (ev.event === 'error')
            patchLast((m) => ({ ...m, notice: String(data.message ?? 'Something went wrong.') }))
        }
        patchLast((m) => ({ ...m, streaming: false }))
      } catch (e) {
        if (e instanceof DOMException && e.name === 'AbortError') {
          patchLast((m) => ({ ...m, streaming: false, notice: 'Stopped.' }))
        } else {
          const f = chatFailure(e)
          patchLast((m) => ({ ...m, streaming: false, notice: f.notice }))
          if (f.unavailable) onUnavailable()
        }
      } finally {
        abort.current = undefined
        setBusy(false)
      }
    },
    [onUnavailable],
  )

  const stop = useCallback(() => abort.current?.abort(), [])

  const reset = useCallback(async () => {
    abort.current?.abort()
    const id = conversationId.current
    conversationId.current = undefined
    store.set(undefined)
    setMessages([])
    if (id) await assistantApi.clear(id).catch(() => undefined)
  }, [])

  return { messages, busy, send, stop, reset }
}
