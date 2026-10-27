# Pricing Service

Authenticated zone discovery and deterministic delivery estimates for SwiftDrop. Browser traffic must use the gateway's `/api/v1/pricing/**` route; do not call the service directly from the web app.

## Configuration

The service uses PostgreSQL, Flyway migrations, and Hibernate schema validation. Configure:

| Variable | Default | Purpose |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/pricing` | Service database URL |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `pricing` | Database credentials |
| `IDENTITY_JWKS_URI` | `http://identity-service:8080/.well-known/jwks.json` | RS256 key set |
| `IDENTITY_ISSUER_URI` | `http://identity-service:8080` | Required JWT issuer |
| `JWT_AUDIENCE` | `swiftdrop` | Required JWT audience |
| `PRICING_INCLUDED_WEIGHT_KG` | `1.00` | Weight included in base fee |
| `PRICING_EXPRESS_PERCENT` | `15.00` | Express percentage applied after base and weight fees |

Compose reads database settings from `PRICING_DB_URL`, `PRICING_DB_USERNAME`, and `PRICING_DB_PASSWORD` in `infra/.env`. Health probes are public; API calls require a valid RS256 bearer token. Zone and rule mutations and inactive-data queries require `ADMIN`.

## Migrations and seeded coverage

- `V1__pricing_schema.sql` creates normalized `zones`, `zone_cities`, and `pricing_rules` tables, foreign keys, money/weight checks, city uniqueness enforcement, and overlapping effective-period protection.
- `V2__seed_pricing.sql` installs eight stable zone IDs and three size bands for all 64 ordered zone pairs (192 deterministic active rules).
- Hibernate runs with `ddl-auto: validate`; schema changes belong in new Flyway migrations.

## API

All paths below are under `/api/v1/pricing`.

| Method and path | Access | Behavior |
| --- | --- | --- |
| `GET /zones?includeInactive=false` | Authenticated; ADMIN for inactive | List zones with `{id,name,code,cities,active}` |
| `GET /zones/resolve?city=Negombo` | Authenticated | Resolve a city case-insensitively; unmapped/inactive city returns 404 |
| `POST /zones` | ADMIN | Create a zone |
| `PUT /zones/{id}` | ADMIN | Replace zone details |
| `DELETE /zones/{id}` | ADMIN | Safely deactivate; referenced rules cause 409 |
| `GET /rules?includeInactive=false` | Authenticated; ADMIN for inactive | List pricing rules |
| `POST /rules` | ADMIN | Create a rule |
| `PUT /rules/{id}` | ADMIN | Update a rule; optional `version` supports optimistic conflict detection |
| `POST /estimate` | Authenticated | Produce a fee breakdown |

Example estimate request:

```json
{"fromCity":"Colombo","toCity":"Galle","size":"MEDIUM","weightKg":2.5,"express":true}
```

Example response:

```json
{"zoneFrom":"CMB","zoneTo":"GAL","baseFee":1000.00,"weightSurcharge":105.00,"expressSurcharge":165.75,"total":1270.75,"currency":"LKR","ruleId":"...","calculatedAt":"2026-10-27T09:00:00Z"}
```

All monetary contributions use `BigDecimal`, scale 2, and `HALF_UP` rounding. Base fee is never below the configured rule's `minFee`; the included weight has no surcharge, excess weight is charged per kg, then express is added as a percentage of base plus weight charges. Estimates are keyed by normalized cities, size, weight, express, and rule ID/version/effective range. Errors are not cached; configuration writes evict estimates.

Errors use RFC 7807 `application/problem+json` and shared error codes, including `VALIDATION_FAILED`, `UNAUTHENTICATED`, `ACCESS_DENIED`, `RESOURCE_NOT_FOUND`, and `CONFLICT`.

## Verification

From the repository root, run `mvnw.cmd -pl services/pricing-service -am verify`. Migration/repository tests use Testcontainers PostgreSQL and are skipped automatically when Docker is unavailable. OpenAPI is available at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`.

## Integration handoff blocker

The current parcel-service client still sends `{zoneFrom, zoneTo, size, weightKg}` and expects `{fee, currency}`. This service implements the agreed web contract `{fromCity, toCity, size, weightKg, express}` and returns a breakdown with `total` and `ruleId`. Do not merge the cross-service booking integration until the parcel owner confirms and updates the exact request, response, and persisted `ruleId` snapshot contract.