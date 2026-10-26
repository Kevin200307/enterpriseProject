/** Client-side mirror of the server rule: https only. (The server also blocks private/loopback targets.) */
export function validateWebhookUrl(v: string): string | undefined {
  try {
    const u = new URL(v.trim())
    return u.protocol === 'https:' ? undefined : 'The URL must start with https://'
  } catch {
    return 'Enter a full URL, like https://example.com/hooks/swiftdrop'
  }
}

export const SNIPPETS = {
  'Node.js': `import crypto from 'node:crypto'

export function verify(rawBody, header, secret, toleranceSec = 300) {
  const parts = Object.fromEntries(header.split(',').map((p) => p.split('=')))
  if (Math.abs(Date.now() / 1000 - Number(parts.t)) > toleranceSec) return false
  const expected = crypto.createHmac('sha256', secret).update(\`\${parts.t}.\${rawBody}\`).digest('hex')
  return crypto.timingSafeEqual(Buffer.from(expected), Buffer.from(parts.v1))
}`,
  Python: `import hmac, hashlib, time

def verify(raw_body: bytes, header: str, secret: str, tolerance=300) -> bool:
    parts = dict(p.split("=", 1) for p in header.split(","))
    if abs(time.time() - int(parts["t"])) > tolerance:
        return False
    signed = parts["t"].encode() + b"." + raw_body
    expected = hmac.new(secret.encode(), signed, hashlib.sha256).hexdigest()
    return hmac.compare_digest(expected, parts["v1"])`,
  Java: `static boolean verify(String rawBody, String header, String secret) throws Exception {
    Map<String, String> p = new HashMap<>();
    for (String kv : header.split(",")) { String[] a = kv.split("=", 2); p.put(a[0], a[1]); }
    if (Math.abs(Instant.now().getEpochSecond() - Long.parseLong(p.get("t"))) > 300) return false;
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
    String expected = HexFormat.of().formatHex(mac.doFinal((p.get("t") + "." + rawBody).getBytes(UTF_8)));
    return MessageDigest.isEqual(expected.getBytes(UTF_8), p.get("v1").getBytes(UTF_8));
}`,
}
