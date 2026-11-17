[CmdletBinding()]
param(
    [ValidatePattern('^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$')][string]$Tag = "dev",
    [ValidateSet('gateway','identity-service','parcel-service','pricing-service','dispatch-service','merchant-service','tracking-service','notification-service')]
    [string[]]$Services = @('gateway','identity-service','parcel-service','pricing-service','dispatch-service','merchant-service','tracking-service','notification-service'),
    [switch]$SkipMavenBuild
)

$ErrorActionPreference = "Stop"
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "../../.."))
foreach ($command in @("java", "javac", "docker")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) { throw "Required command '$command' was not found." }
}
$wrapper = if ($env:OS -eq "Windows_NT") { Join-Path $repository "mvnw.cmd" } else { Join-Path $repository "mvnw" }
if (-not $SkipMavenBuild) {
    & $wrapper -B -s (Join-Path $repository "infra/kubernetes/maven-settings.xml") `
        -f (Join-Path $repository "pom.xml") -DskipTests package
    if ($LASTEXITCODE -ne 0) { throw "Host Java build failed; no images will be packaged." }
}

$temporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$stagingRoot = Join-Path $temporaryRoot ("swiftdrop-java-images-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $stagingRoot | Out-Null
try {
    foreach ($service in $Services) {
        $staging = Join-Path $stagingRoot $service
        $runner = Join-Path $staging "migration-runner"
        New-Item -ItemType Directory -Path $runner -Force | Out-Null
        [IO.File]::WriteAllText((Join-Path $runner ".keep"), "")
        $artifacts = @(Get-ChildItem -Path (Join-Path $repository "services/$service/target") -Filter '*.jar' -File)
        if ($artifacts.Count -ne 1) { throw "Expected exactly one executable JAR for $service." }
        $jar = Join-Path $staging "application.jar"
        Copy-Item -LiteralPath $artifacts[0].FullName -Destination $jar
        $application = Join-Path $staging "application"
        & java -Djarmode=tools -jar $jar extract --destination $application
        if ($LASTEXITCODE -ne 0) { throw "Could not extract the $service application." }
        if ($service -notin @("gateway", "tracking-service")) {
            & javac -cp (Join-Path $application 'lib/*') -d $runner `
                (Join-Path $repository 'infra/kubernetes/migrations/DatabaseMigration.java')
            if ($LASTEXITCODE -ne 0) { throw "Could not compile the migration runner for $service." }
        }
        # Keep only the extracted layout in the build context.
        Remove-Item -LiteralPath $jar -Force
        docker build --file (Join-Path $repository "infra/kubernetes/images/java-local.Dockerfile") `
            --tag "swiftdrop/${service}:$Tag" $staging
        if ($LASTEXITCODE -ne 0) { throw "Local runtime image build failed for $service." }
    }
} finally {
    $resolvedStaging = [IO.Path]::GetFullPath($stagingRoot)
    if ($resolvedStaging.StartsWith($temporaryRoot, [StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path -Leaf $resolvedStaging).StartsWith('swiftdrop-java-images-')) {
        Remove-Item -LiteralPath $resolvedStaging -Recurse -Force
    }
}
