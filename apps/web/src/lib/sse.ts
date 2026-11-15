export interface SseEvent {
  event: string
  data: string
}

/**
 * Parses a Server-Sent Events byte stream (we need fetch + POST, which EventSource cannot do).
 * Handles events split across chunks, CRLF, multi-line `data:`, and `:` comment/heartbeat lines.
 */
export async function* parseSse(body: ReadableStream<Uint8Array>): AsyncGenerator<SseEvent> {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  const toEvent = (block: string): SseEvent | null => {
    let event = 'message'
    const data: string[] = []
    for (const line of block.split('\n')) {
      if (!line || line.startsWith(':')) continue
      const i = line.indexOf(':')
      const field = i === -1 ? line : line.slice(0, i)
      const value = i === -1 ? '' : line.slice(i + 1).replace(/^ /, '')
      if (field === 'event') event = value
      else if (field === 'data') data.push(value)
    }
    return data.length || event !== 'message' ? { event, data: data.join('\n') } : null
  }

  try {
    for (;;) {
      const { value, done } = await reader.read()
      if (done) break
      buffer = (buffer + decoder.decode(value, { stream: true })).replace(/\r\n/g, '\n')
      let end: number
      while ((end = buffer.indexOf('\n\n')) !== -1) {
        const ev = toEvent(buffer.slice(0, end))
        buffer = buffer.slice(end + 2)
        if (ev) yield ev
      }
    }
    const last = toEvent((buffer + decoder.decode()).replace(/\r\n/g, '\n'))
    if (last) yield last
  } finally {
    reader.releaseLock()
  }
}
