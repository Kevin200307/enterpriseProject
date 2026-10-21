# Gateway acceptance tests

Run the complete public-edge acceptance contract from the repository root:

```bash
mvn -pl services/gateway -am \
  -Dtest=GatewayAcceptanceSuite \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

The suite covers routing, JWT enforcement, identity-header sanitization, CORS,
browser security headers, distributed rate limiting, request correlation,
problem responses, WebSocket upgrades, SSE streaming, aggregated OpenAPI
documents, and dashboard degradation.

Docker must be running for the Redis-backed rate-limit acceptance test. JUnit
skips that test when Docker is unavailable; all remaining acceptance tests use
in-process deterministic upstream stubs and require no external services.
