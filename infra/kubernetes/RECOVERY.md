# Kubernetes operations and recovery

## Rollback

Inspect the release and roll back only within the SwiftDrop namespace:

```powershell
helm history swiftdrop -n swiftdrop
helm rollback swiftdrop <revision> -n swiftdrop --wait --timeout 15m
```

An atomic deployment rolls itself back when migrations or readiness fail. Preserve failed Job
logs before retrying. Never repair a failed schema by manually editing production tables.

Helm rollback restores workload specifications; it does not undo database schema changes. Keep
migrations compatible with both the new application and the previous release. Successful hooks
are deleted; failed migration Jobs remain for up to one day. Correct the migration or deployment
configuration and rerun the release rather than enabling schema changes in every replica.

The first local deployment has a dependency-only bootstrap revision followed by the application
revision. If initial migrations fail, the dependency revision remains available for diagnosis and
`deploy-local.ps1` can retry without recreating credentials. Do not roll an existing application
back to its bootstrap revision unless deliberately removing application workloads.

## Disable the assistant

Set `config.assistantEnabled=false` and deploy the chart. Core delivery APIs remain available.
For a provider incident, also remove/disable the Gemini secret through the secret manager. Do not
delete conversation or checkpoint data during incident mitigation.

## Database restore

1. Stop writes or scale the owning service to zero.
2. Verify the backup and target database/service ownership.
3. Restore into a new database instance where possible.
4. Run the owning migration Job against the restored copy.
5. Perform service-specific smoke tests.
6. Change the external secret reference and restart only the owning workload.
7. Preserve the old database until recovery is confirmed.

Local PostgreSQL is disposable and not a backup model. Production databases require managed
point-in-time recovery, encryption, tested restore procedures, and separate service credentials.

## Credential rotation

Rotate credentials in the external secret provider, wait for synchronization, and restart only
consumers of the changed key. Rotate JWT signing keys using an overlap period where both old and
new public keys are published. Revoke compromised user sessions in identity-service.

## Certificates

Monitor certificate expiry through cert-manager or the cloud ingress provider. Validate renewal
in staging. Do not replace a TLS Secret manually while its controller owns it.

## Pod and node failures

Use Deployments/StatefulSets to recreate pods; do not edit live pods. Respect PodDisruptionBudgets
during node maintenance. If a PVC is involved, confirm attachment and database integrity before
force-removing anything.

## Incident evidence

Capture release revision, image digests, events, pod status, redacted logs, metrics, migration Job
logs, request IDs, and relevant audit records. Never copy Secrets, JWTs, customer addresses,
phone numbers, or raw assistant prompts into incident tickets.
