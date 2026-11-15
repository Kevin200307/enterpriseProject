import { api, requestRaw } from '../client'
import type { ConversationTurn } from '../types'

export const assistantApi = {
  /** POST + streamed SSE body. Events: `token`, `tool`, `done`, `error` (see FRONTEND_PLAN C15). */
  stream: (message: string, conversationId: string | undefined, signal: AbortSignal) =>
    requestRaw('/api/v1/assistant/chat/stream', {
      method: 'POST',
      body: { message, conversationId },
      headers: { Accept: 'text/event-stream' },
      signal,
    }),
  conversation: (id: string) =>
    api.get<{ conversationId: string; messages: ConversationTurn[] }>(
      `/api/v1/assistant/conversations/${encodeURIComponent(id)}`,
    ),
  clear: (id: string) =>
    api.delete<void>(`/api/v1/assistant/conversations/${encodeURIComponent(id)}`),
}
