# Run without a live cluster. Every external command used by these tests is replaced below.
$ErrorActionPreference = "Stop"
$scripts = Join-Path $PSScriptRoot "../scripts"
$global:SwiftDropScriptChecks = @{ Context = "kind-swiftdrop"; Installed = $false; Helm4 = $true; FailBootstrap = $false; PlatformExport = $true; ServerApi = '1.54'; FailExport = $false; Calls = @() }
function kubectl {
    $global:LASTEXITCODE = 0
    $global:SwiftDropScriptChecks.Calls += ,@("kubectl", $args)
    if (($args -join " ") -eq "config current-context") { return $global:SwiftDropScriptChecks.Context }
    if ($args -contains "secret") { return "secret/swiftdrop-secrets" }
    if ($args -contains "namespace" -and $args -contains "get") { return "namespace/swiftdrop" }
}
function helm {
    $global:LASTEXITCODE = 0
    $global:SwiftDropScriptChecks.Calls += ,@("helm", $args)
    if ($args -contains "--help") {
        if ($global:SwiftDropScriptChecks.Helm4) { return "--rollback-on-failure" }
        return "--atomic"
    }
    if ($args -contains "bootstrapOnly=true" -and $global:SwiftDropScriptChecks.FailBootstrap) {
        $global:LASTEXITCODE = 1
    }
    if ($args[0] -eq "list" -and $global:SwiftDropScriptChecks.Installed) { return "swiftdrop" }
}
function docker {
    $global:LASTEXITCODE = 0
    $global:SwiftDropScriptChecks.Calls += ,@("docker", $args)
    if ($args -contains '--help' -and $global:SwiftDropScriptChecks.PlatformExport) { return '--platform' }
    if ($args[0] -eq 'info') { return 'linux/x86_64' }
    if ($args[0] -eq 'version') { return $global:SwiftDropScriptChecks.ServerApi }
    if ($args -contains '--output' -and $global:SwiftDropScriptChecks.FailExport) { $global:LASTEXITCODE = 1 }
}
function kind {
    $global:LASTEXITCODE = 0
    $global:SwiftDropScriptChecks.Calls += ,@("kind", $args)
}
function Assert-Check([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}
try {
    & (Join-Path $scripts "create-local-secret.ps1")
    $writes = @($global:SwiftDropScriptChecks.Calls | Where-Object { $_[1] -contains "create" -or $_[1] -contains "apply" })
    Assert-Check ($writes.Count -eq 0) "Existing credentials must never be regenerated or overwritten."

    $global:SwiftDropScriptChecks.Calls = @()
    & (Join-Path $scripts "deploy-local.ps1") -Tag test-sha
    $upgrades = @($global:SwiftDropScriptChecks.Calls | Where-Object { $_[0] -eq "helm" -and $_[1][0] -eq "upgrade" -and $_[1] -notcontains "--help" })
    Assert-Check ($upgrades.Count -eq 2) "First deployment must bootstrap dependencies and then applications."
    Assert-Check ($upgrades[0][1] -contains "bootstrapOnly=true") "Dependencies must be deployed first."
    Assert-Check ($upgrades[1][1] -contains "bootstrapOnly=false") "Application rollout must follow bootstrap."
    Assert-Check ($upgrades[1][1] -contains "--rollback-on-failure") "Helm 4 rollback flag was not selected."
    $lists = @($global:SwiftDropScriptChecks.Calls | Where-Object { $_[0] -eq "helm" -and $_[1][0] -eq "list" })
    Assert-Check ($lists.Count -eq 1 -and $lists[0][1] -notcontains "--all") "Release lookup must use flags supported by Helm 3 and Helm 4."
    Assert-Check ($upgrades[1][1] -contains "services.parcel-service.image.tag=test-sha") "Image tag must propagate."

    $global:SwiftDropScriptChecks.Installed = $true
    $global:SwiftDropScriptChecks.Calls = @()
    & (Join-Path $scripts "deploy-local.ps1")
    $upgrades = @($global:SwiftDropScriptChecks.Calls | Where-Object { $_[0] -eq "helm" -and $_[1][0] -eq "upgrade" -and $_[1] -notcontains "--help" })
    Assert-Check ($upgrades.Count -eq 1) "Existing application releases must never be bootstrapped again."
    Assert-Check ($upgrades[0][1] -contains "bootstrapOnly=false") "An upgrade must retain application resources."

    $global:SwiftDropScriptChecks.Helm4 = $false
    $global:SwiftDropScriptChecks.Calls = @()
    & (Join-Path $scripts "deploy-local.ps1")
    $upgrades = @($global:SwiftDropScriptChecks.Calls | Where-Object { $_[0] -eq "helm" -and $_[1][0] -eq "upgrade" -and $_[1] -notcontains "--help" })
    Assert-Check ($upgrades[0][1] -contains "--atomic") "Helm 3 must use --atomic."

    $global:SwiftDropScriptChecks.Installed = $false
    $global:SwiftDropScriptChecks.FailBootstrap = $true
    $global:SwiftDropScriptChecks.Calls = @()
    $rejected = $false
    try { & (Join-Path $scripts "deploy-local.ps1") } catch { $rejected = $true }
    Assert-Check $rejected "Dependency bootstrap failure must stop deployment."
    $applicationWrites = @($global:SwiftDropScriptChecks.Calls | Where-Object { $_[1] -contains "bootstrapOnly=false" })
    Assert-Check ($applicationWrites.Count -eq 0) "Applications must not deploy after failed bootstrap."

    $global:SwiftDropScriptChecks.Context = "unrelated-cluster"
    $global:SwiftDropScriptChecks.Calls = @()
    $rejected = $false
    try { & (Join-Path $scripts "deploy-local.ps1") } catch { $rejected = $true }
    Assert-Check $rejected "An unrelated Kubernetes context must be rejected."
    Assert-Check ($global:SwiftDropScriptChecks.Calls.Count -eq 1) "Wrong-context rejection must precede cluster writes."
    $global:SwiftDropScriptChecks.Calls = @()
    & (Join-Path $scripts "load-images.ps1") -Tag test-sha
    $loads = @($global:SwiftDropScriptChecks.Calls | Where-Object { $_[0] -eq "kind" })
    Assert-Check ($loads.Count -eq 1) "Images must load as one archive to deduplicate shared runtime layers."
    Assert-Check ($loads[0][1] -contains "image-archive") "Modern Docker images must load through a platform-filtered archive."
    $exports = @($global:SwiftDropScriptChecks.Calls | Where-Object { $_[0] -eq 'docker' -and $_[1] -contains '--output' })
    Assert-Check ($exports.Count -eq 1) "All images must share one export."
    Assert-Check ($exports[0][1] -contains "swiftdrop/gateway:test-sha") "Application images must be exported."
    Assert-Check ($exports[0][1] -contains "swiftdrop/minio-client:test-sha") "Dependency images must be exported."
    Assert-Check ($exports[0][1] -contains "linux/amd64") "Docker's architecture must be normalized for platform export."
    $global:SwiftDropScriptChecks.FailExport = $true
    $global:SwiftDropScriptChecks.Calls = @()
    $rejected = $false
    try { & (Join-Path $scripts "load-images.ps1") -Tag test-sha } catch { $rejected = $true }
    Assert-Check $rejected "A failed export must stop image loading."
    Assert-Check (@($global:SwiftDropScriptChecks.Calls | Where-Object { $_[0] -eq 'kind' }).Count -eq 0) "Kind must not receive a failed archive."
    $global:SwiftDropScriptChecks.FailExport = $false
    $global:SwiftDropScriptChecks.PlatformExport = $false
    $global:SwiftDropScriptChecks.Calls = @()
    & (Join-Path $scripts "load-images.ps1") -Tag test-sha
    $loads = @($global:SwiftDropScriptChecks.Calls | Where-Object { $_[0] -eq 'kind' })
    Assert-Check ($loads.Count -eq 1 -and $loads[0][1] -contains "docker-image") "Older Docker engines must retain the batched Kind loader."
    $global:SwiftDropScriptChecks.PlatformExport = $true
    $global:SwiftDropScriptChecks.ServerApi = '1.47'
    $global:SwiftDropScriptChecks.Calls = @()
    & (Join-Path $scripts "load-images.ps1") -Tag test-sha
    $loads = @($global:SwiftDropScriptChecks.Calls | Where-Object { $_[0] -eq 'kind' })
    Assert-Check ($loads.Count -eq 1 -and $loads[0][1] -contains "docker-image") "A newer CLI connected to an older engine must use the compatible loader."
    Write-Host "Local credential, bootstrap, upgrade, context safety, and image loading checks passed."
} finally {
    Remove-Variable -Name SwiftDropScriptChecks -Scope Global
}
