// Fails if the production build contains demo logins or the mock backend. Run after `npm run build`.
import { readdirSync, readFileSync, existsSync, statSync } from 'node:fs'
import { join } from 'node:path'

const FORBIDDEN = [/swiftdrop\.demo/, /Demo1234/, /setupWorker/, /mockServiceWorker/]
const walk = (dir) =>
  readdirSync(dir).flatMap((f) =>
    statSync(join(dir, f)).isDirectory() ? walk(join(dir, f)) : [join(dir, f)],
  )

if (process.env.DEMO_BUILD === 'true') {
  console.warn('verify-dist: DEMO_BUILD set — this artefact is MEANT to contain the mock backend')
  process.exit(0)
}

if (!existsSync('dist')) {
  console.error('dist/ not found: run `npm run build` first')
  process.exit(2)
}
const hits = walk('dist').flatMap((file) => {
  const text = readFileSync(file, 'utf8')
  return FORBIDDEN.filter((re) => re.test(text)).map((re) => `${file}  matches ${re}`)
})
if (hits.length) {
  console.error('Demo/mock code leaked into the production build:\n' + hits.join('\n'))
  process.exit(1)
}
console.log('dist is clean: no demo logins or mock backend')
