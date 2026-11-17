[CmdletBinding()]
param(
    [string]$Namespace = "swiftdrop",
    [string]$SecretName = "swiftdrop-secrets"
)

$ErrorActionPreference = "Stop"
foreach ($command in @("kubectl")) {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "Required command '$command' was not found in PATH."
    }
}
$opensslCommand = Get-Command openssl -ErrorAction SilentlyContinue
$gitCommand = Get-Command git -ErrorAction SilentlyContinue
$opensslPath = if ($opensslCommand) { $opensslCommand.Source } elseif ($gitCommand) {
    Join-Path (Split-Path -Parent (Split-Path -Parent $gitCommand.Source)) "usr/bin/openssl.exe"
} else { "" }
if ($Namespace -ne "swiftdrop" -or $SecretName -ne "swiftdrop-secrets") {
    throw "The local bootstrap is restricted to swiftdrop/swiftdrop-secrets."
}

$context = "kind-swiftdrop"
$currentContext = kubectl config current-context
if ($LASTEXITCODE -ne 0 -or $currentContext -ne $context) {
    throw "Select the local kind-swiftdrop context before creating local credentials."
}
$existing = kubectl --context $context get secret $SecretName --namespace $Namespace --ignore-not-found -o name
if ($LASTEXITCODE -ne 0) { throw "Could not check the local Secret." }
if ($existing) {
    Write-Host "Local Secret already exists; preserving database passwords and JWT keys."
    return
}
if (-not $opensslPath -or -not (Test-Path -LiteralPath $opensslPath)) {
    throw "OpenSSL was not found in PATH or the Git installation."
}

$temporaryRoot = [System.IO.Path]::GetTempPath()
$temporaryDirectory = Join-Path $temporaryRoot ("swiftdrop-k8s-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $temporaryDirectory | Out-Null
try {
    $privateKey = Join-Path $temporaryDirectory "private.pem"
    $publicKey = Join-Path $temporaryDirectory "public.pem"
    $previousErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $opensslPath genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out $privateKey 2>$null
    $privateKeyExitCode = $LASTEXITCODE
    & $opensslPath pkey -in $privateKey -pubout -out $publicKey 2>$null
    $publicKeyExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorAction
    if ($privateKeyExitCode -ne 0) { throw "Could not generate the local JWT private key." }
    if ($publicKeyExitCode -ne 0) { throw "Could not generate the local JWT public key." }

    $password = [guid]::NewGuid().ToString("N")
    $assistantPassword = [guid]::NewGuid().ToString("N")
    $arguments = @(
        "create", "secret", "generic", $SecretName,
        "--context", $context,
        "--namespace", $Namespace,
        "--from-literal=POSTGRES_ADMIN_PASSWORD=$password",
        "--from-literal=IDENTITY_DB_URL=jdbc:postgresql://postgres:5432/identity",
        "--from-literal=IDENTITY_DB_USERNAME=identity",
        "--from-literal=IDENTITY_DB_PASSWORD=$password",
        "--from-literal=PARCEL_DB_URL=jdbc:postgresql://postgres:5432/parcel",
        "--from-literal=PARCEL_DB_USERNAME=parcel",
        "--from-literal=PARCEL_DB_PASSWORD=$password",
        "--from-literal=PRICING_DB_URL=jdbc:postgresql://postgres:5432/pricing",
        "--from-literal=PRICING_DB_USERNAME=pricing",
        "--from-literal=PRICING_DB_PASSWORD=$password",
        "--from-literal=DISPATCH_DB_URL=jdbc:postgresql://postgres:5432/dispatch",
        "--from-literal=DISPATCH_DB_USERNAME=dispatch",
        "--from-literal=DISPATCH_DB_PASSWORD=$password",
        "--from-literal=MERCHANT_DB_URL=jdbc:postgresql://postgres:5432/merchant",
        "--from-literal=MERCHANT_DB_USERNAME=merchant",
        "--from-literal=MERCHANT_DB_PASSWORD=$password",
        "--from-literal=NOTIFICATION_DB_URL=jdbc:postgresql://postgres:5432/notification",
        "--from-literal=NOTIFICATION_DB_USERNAME=notification",
        "--from-literal=NOTIFICATION_DB_PASSWORD=$password",
        "--from-literal=ASSISTANT_DB_PASSWORD=$assistantPassword",
        "--from-literal=ASSISTANT_DATABASE_URL=postgresql+asyncpg://assistant:$assistantPassword@postgres:5432/assistant",
        "--from-literal=CHECKPOINT_DATABASE_URL=postgresql://assistant:$assistantPassword@postgres:5432/assistant",
        "--from-literal=REDIS_PASSWORD=$password",
        "--from-literal=REDIS_URL=redis://:$password@redis:6379/0",
        "--from-literal=RABBITMQ_USERNAME=swiftdrop",
        "--from-literal=RABBITMQ_PASSWORD=$password",
        "--from-literal=DISPATCH_CLIENT_SECRET=$password",
        "--from-literal=GOOGLE_CLIENT_ID=local-kubernetes-client",
        "--from-literal=GOOGLE_CLIENT_SECRET=$password",
        "--from-literal=GOOGLE_API_KEY=",
        "--from-literal=MAIL_USERNAME=",
        "--from-literal=MAIL_PASSWORD=",
        "--from-literal=MINIO_ACCESS_KEY=swiftdrop-local",
        "--from-literal=MINIO_SECRET_KEY=$password",
        "--from-file=IDENTITY_JWT_PRIVATE_KEY=$privateKey",
        "--from-file=IDENTITY_JWT_PUBLIC_KEY=$publicKey",
        "--dry-run=client", "-o", "yaml"
    )
    $manifest = & kubectl @arguments
    if ($LASTEXITCODE -ne 0) { throw "Could not generate the local Secret manifest." }
    $manifest | kubectl --context $context create -f -
    if ($LASTEXITCODE -ne 0) { throw "Could not apply the local Secret." }
} finally {
    $resolved = [System.IO.Path]::GetFullPath($temporaryDirectory)
    if ($resolved.StartsWith($temporaryRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
