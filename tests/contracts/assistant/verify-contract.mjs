import { readFile } from 'node:fs/promises'
import { strict as assert } from 'node:assert'

const contract = JSON.parse(await readFile(new URL('./contract.json', import.meta.url), 'utf8'))
assert.equal(contract.basePath, '/api/v1/assistant')
assert.equal(contract.authentication.required, true)

const endpoints = new Map(contract.endpoints.map((value) => [`${value.method} ${value.path}`, value]))
for (const expected of [
  'POST /api/v1/assistant/chat/stream',
  'GET /api/v1/assistant/conversations/{conversationId}',
  'DELETE /api/v1/assistant/conversations/{conversationId}',
  'GET /actuator/health',
]) assert.ok(endpoints.has(expected), `missing endpoint contract: ${expected}`)

const stream = endpoints.get('POST /api/v1/assistant/chat/stream')
assert.equal(stream.response.contentType, 'text/event-stream')
assert.deepEqual(Object.keys(stream.response.events), ['token', 'tool', 'done', 'error'])
assert.deepEqual(contract.tools.map(({ name }) => name), ['trackParcel', 'estimateFee', 'listMyParcels'])
console.log('Assistant contract fixture is valid.')
