[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$ClusterName = "swiftdrop"
)

$ErrorActionPreference = "Stop"
$expectedName = "swiftdrop"
if ($ClusterName -ne $expectedName) {
    throw "This script can delete only the '$expectedName' Kind cluster."
}
if (-not (Get-Command kind -ErrorAction SilentlyContinue)) {
    throw "Required command 'kind' was not found in PATH."
}

$clusters = @(kind get clusters)
if ($clusters -notcontains $ClusterName) {
    Write-Host "Kind cluster '$ClusterName' does not exist."
    exit 0
}
if ($PSCmdlet.ShouldProcess("Kind cluster '$ClusterName'", "Delete")) {
    kind delete cluster --name $ClusterName
}
