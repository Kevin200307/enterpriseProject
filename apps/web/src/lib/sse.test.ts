import { describe, expect, it } from 'vitest'
import { parseSse, type SseEvent } from './sse'

const stream = (chunks: string[]) =>
  new ReadableStream<Uint8Array>({
    start(c) {
      const enc = new TextEncoder()
      chunks.forEach((x) => c.enqueue(enc.encode(x)))
      c.close()
    },
  })

async function collect(chunks: string[]) {
  const out: SseEvent[] = []
  for await (const e of parseSse(stream(chunks))) out.push(e)
  return out
}

describe('parseSse', () => {
  it('parses named events', async () => {
    expect(
      await collect(['event: token\ndata: {"text":"Hi"}\n\nevent: done\ndata: {}\n\n']),
    ).toEqual([
      { event: 'token', data: '{"text":"Hi"}' },
      { event: 'done', data: '{}' },
    ])
  })

  it('reassembles events split anywhere across chunks, even mid-field', async () => {
    const whole = 'event: token\ndata: {"text":"hello"}\n\n'
    const split = [whole.slice(0, 3), whole.slice(3, 17), whole.slice(17, 30), whole.slice(30)]
    expect(await collect(split)).toEqual([{ event: 'token', data: '{"text":"hello"}' }])
  })

  it('handles CRLF line endings, including a CR/LF pair split across chunks', async () => {
    expect(
      await collect(['event: token\r\ndata: a\r', '\n\r\nevent: done\r\ndata: b\r\n\r\n']),
    ).toEqual([
      { event: 'token', data: 'a' },
      { event: 'done', data: 'b' },
    ])
  })

  it('joins multi-line data and defaults the event name to "message"', async () => {
    expect(await collect(['data: line1\ndata: line2\n\n'])).toEqual([
      { event: 'message', data: 'line1\nline2' },
    ])
  })

  it('ignores comment/heartbeat lines and empty blocks', async () => {
    expect(
      await collect([': keep-alive\n\n', ': ping\nevent: token\ndata: x\n\n', '\n\n']),
    ).toEqual([{ event: 'token', data: 'x' }])
  })

  it('flushes a final event that is missing its trailing blank line', async () => {
    expect(await collect(['event: done\ndata: {"conversationId":"c1"}'])).toEqual([
      { event: 'done', data: '{"conversationId":"c1"}' },
    ])
  })

  it('keeps multi-byte characters intact when a chunk splits them', async () => {
    const bytes = new TextEncoder().encode('data: ශ්‍රී ලංකා\n\n')
    const s = new ReadableStream<Uint8Array>({
      start(c) {
        c.enqueue(bytes.slice(0, 8))
        c.enqueue(bytes.slice(8))
        c.close()
      },
    })
    const out: SseEvent[] = []
    for await (const e of parseSse(s)) out.push(e)
    expect(out[0]?.data).toBe('ශ්‍රී ලංකා')
  })
})
