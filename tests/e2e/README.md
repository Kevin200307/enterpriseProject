# Gateway end-to-end harness

Run `./tests/e2e/run.ps1` from the repository root. It creates a dedicated Compose
project, local PostgreSQL databases, Redis, RabbitMQ, MinIO and the application
services, runs the black-box Gateway workflow twice, checks a controlled pricing
outage, and removes containers and volumes afterward. Use `-Keep` for diagnosis.

The mandatory path covers registration/login, idempotent customer booking, public
tracking, notification visibility and cancellation. CI may set
`E2E_DISPATCHER_TOKEN`, `E2E_COURIER_TOKEN`, and `E2E_COURIER_ID` to extend the same
run through assignment, failed delivery and reattempt using pre-seeded actors.
Merchant webhook delivery is asserted by the merchant integration suite because the
production SSRF guard correctly rejects webhook destinations on the private Compose
network; a CI HTTPS sink with a public address can exercise that adapter separately.

The Compose run needs the RSA identity key variables documented in
`infra/.env.example`. It never reads or writes the shared Neon databases.
