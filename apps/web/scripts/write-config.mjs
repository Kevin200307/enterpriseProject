// Writes dist/config.js from the deploy's environment, the same contract the Docker image gets
// from docker/40-runtime-config.sh — so one build artefact works against any backend.
import { writeFileSync } from 'node:fs'

const demo = process.env.VITE_DEMO_MODE === 'true'
const api = (process.env.API_BASE_URL ?? (demo ? '__DEMO_ORIGIN__' : 'http://localhost:8081')).replace(
  /\/$/,
  '',
)
const ws = process.env.WS_URL ?? (demo ? '__DEMO_ORIGIN__/ws' : `${api}/ws`)
const assistant = !/^(false|0)$/i.test(process.env.ASSISTANT_ENABLED ?? 'true')

// Same guard as the shell version: nothing that could break out of the string literals.
for (const v of [api, ws]) {
  if (/['"\\<>;]/.test(v)) {
    console.error(`write-config: refusing unsafe value: ${v}`)
    process.exit(1)
  }
}

const apiExpression = api === '__DEMO_ORIGIN__' ? 'window.location.origin' : `'${api}'`
const wsExpression =
  ws === '__DEMO_ORIGIN__/ws'
    ? 'window.location.origin + \'/ws\''
    : `'${ws}'`

writeFileSync(
  'dist/config.js',
  `window.__SWIFTDROP__ = {\n  apiBaseUrl: ${apiExpression},\n  wsUrl: ${wsExpression},\n  assistantEnabled: ${assistant},\n}\n`,
)
console.log(`write-config: api=${api} ws=${ws} assistant=${assistant}`)
