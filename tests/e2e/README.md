# Gateway end-to-end harness

Run `./tests/e2e/run.ps1` from the repository root. It creates a dedicated Compose
project, local PostgreSQL databases, Redis, RabbitMQ, MinIO and the application
services, runs the black-box Gateway workflow twice, checks a controlled pricing
outage, and removes containers and volumes afterward. Use `-Keep` for diagnosis.

The mandatory path covers registration/login, idempotent customer booking, public
tracking, notification visibility and cancellation. CI may set
`E2E_DISPATCHER_TOKEN`, `E2E_COURIER_TOKEN`, and `E2E_COURIER_ID` to extend the same
run through assignment, failed delivery and reattempt using pre-seeded actors.
Pass the raw JWT access tokens (without a `Bearer ` prefix) for the dispatcher and
courier; the workflow adds the authorization scheme.
Merchant webhook delivery is asserted by the merchant integration suite because the
production SSRF guard correctly rejects webhook destinations on the private Compose
network; a CI HTTPS sink with a public address can exercise that adapter separately.

The Compose run needs the RSA identity key variables documented in
`infra/.env.example`. It never reads or writes the shared Neon databases. By default,
the E2E gateway, RabbitMQ management UI, MinIO, and Mailpit use host ports `18081`,
`25673`, `29000`, `29001`, `21025`, and `28025`; override them with
`E2E_GATEWAY_HOST_PORT`, `E2E_RABBITMQ_MANAGEMENT_HOST_PORT`,
`E2E_MINIO_API_HOST_PORT`, `E2E_MINIO_CONSOLE_HOST_PORT`, `E2E_MAILPIT_SMTP_PORT`,
and `E2E_MAILPIT_UI_PORT` if any port is already in use.
