import { delay, http, HttpResponse } from 'msw'
import { statusMeta } from '@/lib/status'
import { estimate, parcels, zones } from '../db'
import { base, body, caller, empty, guard, problem } from '../util'
import { canSee } from './parcels'

const conversations = new Map<
  string,
  { owner: string; messages: { role: 'user' | 'assistant'; content: string }[] }
>()
const CITIES = zones.flatMap((z) => z.cities)

type Ev = [string, object]

/** A tiny scripted assistant: it calls the same "tools" the real one has, against the mock data, as the signed-in user. */
function answer(
  text: string,
  sub: string,
  canSeeFor: (p: (typeof parcels)[number]) => boolean,
): { events: Ev[]; reply: string } {
  const events: Ev[] = []
  const tn = /SD-[A-HJ-NP-Z2-9]{8}/i.exec(text)?.[0].toUpperCase()
  if (tn) {
    events.push(['tool', { name: 'getParcelByTrackingNumber', arguments: { trackingNumber: tn } }])
    const p = parcels.find((x) => x.trackingNumber === tn)
    if (!p) return { events, reply: `I couldn't find a parcel with tracking number ${tn}.` }
    const seeAll = canSeeFor(p)
    return {
      events,
      reply: `**${tn}** is **${statusMeta(p.status).label.toLowerCase()}**, travelling from ${p.pickup.city} to ${p.dropoff.city}.${seeAll ? ` Your fee was LKR ${Number(p.fee).toFixed(2)}.` : ' (You can only see the public view of this parcel.)'}`,
    }
  }
  const found = CITIES.filter((c) => new RegExp(`\\b${c}\\b`, 'i').test(text)).sort(
    (a, b) =>
      text.toLowerCase().indexOf(a.toLowerCase()) - text.toLowerCase().indexOf(b.toLowerCase()),
  )
  if (/(how much|price|cost|fee|estimate)/i.test(text) && found.length >= 2) {
    const kg = Number(/(\d+(?:\.\d+)?)\s*kg/i.exec(text)?.[1] ?? 1)
    const express = /express/i.test(text)
    const args = {
      fromCity: found[0]!,
      toCity: found[1]!,
      size: 'SMALL' as const,
      weightKg: kg,
      express,
    }
    events.push(['tool', { name: 'estimateFee', arguments: args }])
    const est = estimate(args)
    return 'error' in est
      ? { events, reply: "I couldn't price that: check the cities and weight." }
      : {
          events,
          reply: `Sending **${kg} kg** from ${args.fromCity} to ${args.toCity}${express ? ' (express)' : ''} costs **LKR ${Number(est.total).toFixed(2)}** — base ${est.baseFee}, weight ${est.weightSurcharge}, express ${est.expressSurcharge}.`,
        }
  }
  if (/(my parcels|in transit|my deliveries|latest)/i.test(text)) {
    const status = /in transit/i.test(text) ? 'IN_TRANSIT' : undefined
    events.push(['tool', { name: 'listMyParcels', arguments: { status, limit: 5 } }])
    const mine = parcels
      .filter((p) => p.customerId === sub && (!status || p.status === status))
      .slice(0, 5)
    return {
      events,
      reply: mine.length
        ? `You have ${mine.length} matching parcel${mine.length > 1 ? 's' : ''}:\n\n${mine.map((p) => `- **${p.trackingNumber}** — ${statusMeta(p.status).label}, ${p.pickup.city} → ${p.dropoff.city}`).join('\n')}`
        : "You don't have any matching parcels.",
    }
  }
  return {
    events,
    reply:
      'I can help with three things: **where a parcel is** (give me its SD- tracking number), **what a delivery costs** (e.g. "how much to send 2 kg from Colombo to Kandy?"), and **listing your parcels**.',
  }
}

export const assistantHandlers = [
  http.post(`${base}/api/v1/assistant/chat/stream`, async ({ request }) => {
    await delay(150)
    const me = caller(request)
    if (!me) return problem(401, 'UNAUTHENTICATED', 'Authentication required')
    const { message, conversationId } = await body<{ message: string; conversationId?: string }>(
      request,
    )
    if (String(message).length > 2000)
      return problem(400, 'VALIDATION_FAILED', 'Message is too long')
    let id = conversationId
    if (id && conversations.get(id)?.owner !== me.sub)
      return problem(404, 'RESOURCE_NOT_FOUND', 'Resource not found')
    id ??= crypto.randomUUID()
    const conv = conversations.get(id) ?? { owner: me.sub, messages: [] }
    conversations.set(id, conv)
    const { events, reply } = answer(message, me.sub, (p) => canSee(p, me))
    conv.messages.push({ role: 'user', content: message }, { role: 'assistant', content: reply })
    const words = reply.split(/(?<=\s)/)
    const enc = new TextEncoder()
    return new HttpResponse(
      new ReadableStream({
        async start(c) {
          const send = (e: string, d: object) =>
            c.enqueue(enc.encode(`event: ${e}\ndata: ${JSON.stringify(d)}\n\n`))
          for (const [e, d] of events) {
            send(e, d)
            await new Promise((r) => setTimeout(r, 350))
          }
          for (const w of words) {
            send('token', { text: w })
            await new Promise((r) => setTimeout(r, 35))
          }
          send('done', { conversationId: id })
          c.close()
        },
      }),
      { headers: { 'Content-Type': 'text/event-stream' } },
    )
  }),
  http.get(
    `${base}/api/v1/assistant/conversations/:id`,
    guard(null, ({ params }, me) => {
      const id = String(params.id)
      const conv = conversations.get(id)
      return conv && conv.owner === me.sub
        ? HttpResponse.json({ conversationId: id, messages: conv.messages })
        : problem(404, 'RESOURCE_NOT_FOUND', 'Resource not found')
    }),
  ),
  http.delete(
    `${base}/api/v1/assistant/conversations/:id`,
    guard(null, ({ params }, me) => {
      const id = String(params.id)
      if (conversations.get(id)?.owner === me.sub) conversations.delete(id)
      return empty()
    }),
  ),
]
