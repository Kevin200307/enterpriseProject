$ErrorActionPreference = 'Stop'

$modules = @(
    'packages/common',
    'services/identity-service',
    'services/parcel-service',
    'services/dispatch-service',
    'services/merchant-service',
    'services/tracking-service',
    'services/notification-service',
    'services/pricing-service',
    'services/gateway'
) -join ','

& mvn -U -pl $modules -am test
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
