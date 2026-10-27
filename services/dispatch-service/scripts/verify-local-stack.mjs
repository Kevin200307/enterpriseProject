// Isolated gateway journey with real identity, parcel and dispatch JVMs, PostgreSQL,
// Redis and RabbitMQ. Pricing is the only HTTP stub. Run after packaging the four services.
import { spawn, execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { generateKeyPairSync, randomUUID, randomBytes } from 'node:crypto';
import { createServer } from 'node:http';
import { createServer as tcpServer } from 'node:net';
import { createWriteStream, mkdirSync, writeFileSync, copyFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import assert from 'node:assert/strict';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
const logs = path.join(root, 'services/dispatch-service/target/live-smoke');
mkdirSync(logs, { recursive: true });
const exec = promisify(execFile);
// Windows' Oracle javapath launcher may create a separate JVM process. Start the
// actual JDK executable so the test owns the service process throughout cleanup.
const runtime = await exec('java', ['-XshowSettings:properties', '-version'], { windowsHide: true });
const javaHome = runtime.stderr.match(/^\s*java.home\s*=\s*(.+)$/m)?.[1].trim();
assert.ok(javaHome, 'Could not resolve the Java runtime directory');
const javaExecutable = path.join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
const containers = [], children = [], streams = [], checks = [];
const prefix = `dispatch-smoke-${randomBytes(4).toString('hex')}`;
const password = randomBytes(24).toString('hex');
const secret = randomBytes(32).toString('hex');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const docker = async (...args) => (await exec('docker', args, { windowsHide: true, timeout: 180000 })).stdout.trim();
const port = async () => {
  const server = tcpServer(); await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const result = server.address().port; await new Promise(resolve => server.close(resolve)); return result;
};
async function container(suffix, image, internalPort, environment = {}, extraPorts = []) {
  const name = `${prefix}-${suffix}`;
  await docker('run', '-d', '--rm', '--name', name, '-p', `127.0.0.1::${internalPort}`,
    ...extraPorts.flatMap(p => ['-p', `127.0.0.1::${p}`]),
    ...Object.entries(environment).flatMap(([key, value]) => ['-e', `${key}=${value}`]), image);
  containers.push(name);
  const result = JSON.parse(await docker('inspect', name));
  return { name, port: Number(result[0].NetworkSettings.Ports[`${internalPort}/tcp`][0].HostPort), ports: result[0].NetworkSettings.Ports };
}
async function waitFor(check, name, milliseconds = 90000) {
  const end = Date.now() + milliseconds;
  while (Date.now() < end) {
    if (children.some(child => child.exitCode !== null)) throw new Error(`A service exited; inspect ${logs}`);
    try { if (await check()) return; } catch {}
    await delay(200);
  }
  throw new Error(`Timed out waiting for ${name}; inspect ${logs}`);
}
async function launch(name, environment) {
  const log = createWriteStream(path.join(logs, `${name}.log`)); streams.push(log);
  const jar = path.join(logs, `${name}.jar`);
  copyFileSync(path.join(root, `services/${name}/target/${name}-0.0.1-SNAPSHOT.jar`), jar);
  const child = spawn(javaExecutable, ['-Xmx256m', '-jar', jar], {
    cwd: root, windowsHide: true, env: { ...process.env, ...environment }, stdio: ['ignore', 'pipe', 'pipe'],
  });
  children.push(child); child.stdout.pipe(log); child.stderr.pipe(log);
  await waitFor(async () => {
    if (child.exitCode !== null) throw new Error(`${name} exited`);
    const response = await fetch(`http://127.0.0.1:${environment.SERVER_PORT}/actuator/health`, { signal: AbortSignal.timeout(2000) });
    return response.ok;
  }, name);
  console.log(`Started ${name}`);
}
let pricing;
try {
  const pg = await container('postgres', 'postgres:16-alpine', 5432, { POSTGRES_PASSWORD: password });
  const rabbit = await container('rabbit', 'rabbitmq:3.13-management-alpine', 5672,
    { RABBITMQ_DEFAULT_USER: 'smoke', RABBITMQ_DEFAULT_PASS: password }, [15672]);
  const redis = await container('redis', 'redis:7-alpine', 6379);
  await waitFor(async () => (await docker('exec', pg.name, 'pg_isready', '-U', 'postgres')).includes('accepting'), 'PostgreSQL');
  for (const database of ['identity_db', 'parcel_db', 'dispatch_db'])
    await docker('exec', pg.name, 'psql', '-U', 'postgres', '-c', `CREATE DATABASE ${database}`);
  await waitFor(async () => (await docker('exec', rabbit.name, 'rabbitmq-diagnostics', '-q', 'ping')).includes('succeeded'), 'RabbitMQ');
  const identityPort = await port(), parcelPort = await port(), dispatchPort = await port(), gatewayPort = await port();
  const identityUrl = `http://127.0.0.1:${identityPort}`;
  const keys = generateKeyPairSync('rsa', { modulusLength: 2048,
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' }, publicKeyEncoding: { type: 'spki', format: 'pem' } });
  pricing = createServer((req, res) => {
    req.resume(); res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify(req.url.includes('health') ? { status: 'UP' } : { fee: 450, currency: 'LKR' }));
  });
  await new Promise(resolve => pricing.listen(0, '127.0.0.1', resolve));
  const pricingUrl = `http://127.0.0.1:${pricing.address().port}`;
  const common = {
    SPRING_DATASOURCE_USERNAME: 'postgres', SPRING_DATASOURCE_PASSWORD: password,
    IDENTITY_ISSUER_URI: identityUrl, IDENTITY_JWKS_URI: `${identityUrl}/.well-known/jwks.json`,
    JWT_ISSUER_URI: identityUrl, JWT_JWK_SET_URI: `${identityUrl}/.well-known/jwks.json`, JWT_AUDIENCE: 'swiftdrop',
    DISPATCH_CLIENT_SECRET: secret, SPRING_RABBITMQ_HOST: '127.0.0.1', SPRING_RABBITMQ_PORT: String(rabbit.port),
    SPRING_RABBITMQ_USERNAME: 'smoke', SPRING_RABBITMQ_PASSWORD: password,
    SPRING_DATA_REDIS_HOST: '127.0.0.1', SPRING_DATA_REDIS_PORT: String(redis.port), SPRING_DATA_REDIS_PASSWORD: '',
    LOGGING_LEVEL_ROOT: 'WARN',
  };
  const database = name => `jdbc:postgresql://127.0.0.1:${pg.port}/${name}_db`;
  await launch('identity-service', { ...common, SERVER_PORT: String(identityPort), SPRING_DATASOURCE_URL: database('identity'),
    IDENTITY_JWT_PRIVATE_KEY: keys.privateKey, IDENTITY_JWT_PUBLIC_KEY: keys.publicKey });
  await launch('dispatch-service', { ...common, SERVER_PORT: String(dispatchPort), SPRING_DATASOURCE_URL: database('dispatch'),
    IDENTITY_SERVICE_URL: identityUrl, PARCEL_SERVICE_URL: `http://127.0.0.1:${parcelPort}` });
  // A separate durable subscriber stands in for legacy status consumers. The parcel outbox
  // requires a broker route for both the legacy status event and the new dispatch snapshot.
  const management = `http://127.0.0.1:${rabbit.ports['15672/tcp'][0].HostPort}`;
  const brokerHeaders = { Authorization: `Basic ${Buffer.from(`smoke:${password}`).toString('base64')}`, 'Content-Type': 'application/json' };
  await fetch(`${management}/api/queues/%2F/smoke.status`, { method: 'PUT', headers: brokerHeaders,
    body: JSON.stringify({ durable: true, arguments: {} }) }).then(r => assert.ok(r.ok));
  await fetch(`${management}/api/bindings/%2F/e/parcel.events/q/smoke.status`, { method: 'POST', headers: brokerHeaders,
    body: JSON.stringify({ routing_key: 'parcel.status.changed.v1', arguments: {} }) }).then(r => assert.ok(r.ok));
  await launch('parcel-service', { ...common, SERVER_PORT: String(parcelPort), SPRING_DATASOURCE_URL: database('parcel'),
    PARCEL_DB_URL: database('parcel'), PARCEL_DB_USERNAME: 'postgres', PARCEL_DB_PASSWORD: password,
    PRICING_SERVICE_URL: pricingUrl, MINIO_ACCESS_KEY: 'smoke-access', MINIO_SECRET_KEY: password });
  await launch('gateway', { ...common, SERVER_PORT: String(gatewayPort), IDENTITY_SERVICE_URL: identityUrl,
    PARCEL_SERVICE_URL: `http://127.0.0.1:${parcelPort}`, DISPATCH_SERVICE_URL: `http://127.0.0.1:${dispatchPort}`,
    PRICING_SERVICE_URL: pricingUrl, MERCHANT_SERVICE_URL: pricingUrl, TRACKING_SERVICE_URL: pricingUrl,
    NOTIFICATION_SERVICE_URL: pricingUrl, AI_ASSISTANT_SERVICE_URL: pricingUrl });
  const base = `http://127.0.0.1:${gatewayPort}`;
  async function api(method, route, token, body, expected = 200, extra = {}) {
    const response = await fetch(base + route, { method, headers: { 'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}), ...extra },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }), signal: AbortSignal.timeout(15000) });
    const text = await response.text();
    assert.equal(response.status, expected, `${method} ${route}: expected ${expected}, received ${response.status}: ${text}`);
    checks.push(`${method} ${route.split('?')[0]} ${response.status}`);
    return text ? JSON.parse(text) : undefined;
  }
  async function account(name, role) {
    const email = `${name}@smoke.test`, password = 'SmokePassword123!';
    const user = await api('POST', '/api/v1/auth/register', null, { email, password, fullName: `Smoke ${name}`, phone: '+94771234567' }, 201);
    if (role !== 'CUSTOMER') await docker('exec', pg.name, 'psql', '-U', 'postgres', '-d', 'identity_db', '-c',
      `INSERT INTO user_roles(user_id,role_id) VALUES ('${user.id}','${role}')`);
    const login = await api('POST', '/api/v1/auth/login', null, { email, password });
    return { id: user.id, token: login.accessToken };
  }
  const dispatcher = await account('dispatcher', 'DISPATCHER'), courier = await account('courier', 'COURIER');
  const other = await account('other', 'COURIER'), customer = await account('customer', 'CUSTOMER');
  const profile = await api('POST', '/api/v1/couriers', dispatcher.token,
    { userId: courier.id, vehicleType: 'BIKE', homeZoneCode: 'COLOMBO', phone: '+94771234567', maxDailyLoad: 5 }, 201);
  await api('PATCH', `/api/v1/couriers/${profile.id}`, dispatcher.token, { maxDailyLoad: 6 });
  await api('GET', '/api/v1/couriers?zone=COLOMBO&vehicle=BIKE&active=true', dispatcher.token);
  const address = { contactName: 'Sender', phone: '+94771234567', line1: '1 Main', city: 'Colombo', postalCode: '00100', zone: 'COLOMBO' };
  const parcel = await api('POST', '/api/v1/parcels', customer.token, { pickup: address,
    dropoff: { ...address, contactName: 'Recipient', phone: '+94777654321', city: 'Kandy', zone: 'KANDY' }, size: 'SMALL', weightKg: 1.25 },
    201, { 'Idempotency-Key': randomUUID() });
  await waitFor(async () => (await api('GET', '/api/v1/dispatch/queue', dispatcher.token)).some(p => p.parcelId === parcel.id), 'created parcel projection', 10000);
  assert.equal((await api('GET', `/api/v1/dispatch/suggest?parcelId=${parcel.id}`, dispatcher.token))[0].courierId, profile.id);
  await api('POST', '/api/v1/dispatch/assignments', dispatcher.token, { parcelId: parcel.id, courierId: profile.id });
  await api('POST', '/api/v1/dispatch/assignments', dispatcher.token, { parcelId: parcel.id, courierId: profile.id });
  assert.equal((await api('GET', `/api/v1/parcels/${parcel.id}`, dispatcher.token)).assigneeId, courier.id);
  assert.equal((await api('GET', '/api/v1/dispatch/queue', dispatcher.token)).length, 0);
  const mine = await api('GET', '/api/v1/couriers/me/assignments', courier.token);
  assert.equal(mine[0].parcelId, parcel.id); assert.notEqual(mine[0].dropoff.phone, '+94777654321');
  const day = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Colombo', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
  await api('GET', `/api/v1/couriers/${profile.id}/manifest?date=${day}`, courier.token);
  await api('GET', `/api/v1/couriers/${profile.id}/manifest?date=${day}`, other.token, undefined, 404);
  await api('GET', '/api/v1/dispatch/workload', dispatcher.token);
  await api('GET', `/api/v1/dispatch/stats/daily?from=${day}&to=${day}`, dispatcher.token);
  await api('GET', '/api/v1/dispatch/queue', customer.token, undefined, 403);
  await api('GET', '/api/v1/dispatch/queue', null, undefined, 401);
  await api('DELETE', `/api/v1/dispatch/assignments/${mine[0].id}`, dispatcher.token, { reason: 'Smoke journey complete' });
  assert.equal((await api('GET', `/api/v1/parcels/${parcel.id}`, dispatcher.token)).assigneeId, null);
  assert.equal((await api('GET', '/api/v1/couriers/me/assignments', courier.token)).length, 0);
  writeFileSync(path.join(logs, 'report.json'), JSON.stringify({ passed: true, checks, trackingNumber: parcel.trackingNumber }, null, 2));
  console.log(`PASS: ${checks.length} live HTTP checks through gateway; real identity login, events, assignment, courier manifest and unassignment. Pricing was stubbed.`);
} catch (error) {
  writeFileSync(path.join(logs, 'report.json'), JSON.stringify({ passed: false, checks, error: error.message }, null, 2));
  console.error(error.message); process.exitCode = 1;
} finally {
  for (const child of children.reverse()) {
    if (child.exitCode !== null) continue;
    if (process.platform === 'win32') {
      try { await exec('taskkill', ['/pid', String(child.pid), '/t', '/f'], { windowsHide: true }); } catch { child.kill(); }
    } else child.kill();
  }
  if (pricing) { pricing.closeAllConnections(); pricing.close(); }
  await delay(500);
  for (const stream of streams) stream.end();
  for (const name of containers.reverse()) { try { await docker('rm', '-f', name); } catch {} }
}
