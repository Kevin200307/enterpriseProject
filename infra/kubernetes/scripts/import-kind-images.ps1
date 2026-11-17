[CmdletBinding()]
param(
    [Parameter(Mandatory)][string[]]$Images,
    [string]$ClusterName = "swiftdrop",
    [ValidatePattern('^linux/(amd64|arm64)(/v8)?$')][string]$Platform
)

$ErrorActionPreference = "Stop"
if ($ClusterName -ne "swiftdrop") { throw "Images may be imported only into the 'swiftdrop' Kind cluster." }
foreach ($image in $Images) {
    docker image inspect $image | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Local image '$image' is missing; build or pull it first." }
}

$saveHelp = docker image save --help
if ($LASTEXITCODE -ne 0) { throw "Could not inspect Docker image export support." }
$serverApi = docker version --format '{{.Server.APIVersion}}'
if ($LASTEXITCODE -ne 0) { throw "Could not determine Docker's API version." }
if (($saveHelp -join "`n") -notmatch '--platform' -or [version]([string]$serverApi).Trim() -lt [version]'1.48') {
    # Older Docker engines use the classic image store and do not support platform selection.
    kind load docker-image @Images --name $ClusterName
    if ($LASTEXITCODE -ne 0) { throw "Failed to load the images into Kind." }
    return
}
if (-not $Platform) {
    $enginePlatform = docker info --format '{{.OSType}}/{{.Architecture}}'
    if ($LASTEXITCODE -ne 0) { throw "Could not determine Docker's platform." }
    $Platform = ([string]$enginePlatform).Trim().Replace('x86_64', 'amd64').Replace('aarch64', 'arm64')
    if ($Platform -notmatch '^linux/(amd64|arm64)$') { throw "Unsupported Docker platform '$Platform'." }
}

# Docker's containerd store may export references to platforms that have not been downloaded.
# Select one platform, keeping shared layers in one archive, before Kind imports it.
$archive = Join-Path ([IO.Path]::GetTempPath()) ("swiftdrop-images-" + [guid]::NewGuid() + ".tar")
try {
    Write-Host "Exporting $($Images.Count) images for $Platform."
    docker image save --platform $Platform --output $archive @Images
    if ($LASTEXITCODE -ne 0) { throw "Image archive export failed; no images were imported." }
    Write-Host "Importing the image archive into Kind cluster '$ClusterName'."
    kind load image-archive $archive --name $ClusterName
    if ($LASTEXITCODE -ne 0) { throw "Failed to import the image archive into Kind." }
} finally {
    if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive -Force }
}
