[CmdletBinding()]
param([Parameter(Mandatory)][string[]]$DnsServers)

$ErrorActionPreference = "Stop"
$lines = foreach ($server in $DnsServers) {
    $address = $null
    if (-not [ipaddress]::TryParse($server, [ref]$address)) { throw "DNS servers must be IP addresses." }
    "nameserver $address"
}
$nodes = @(kind get nodes --name swiftdrop)
if ($LASTEXITCODE -ne 0 -or $nodes.Count -eq 0) { throw "The local SwiftDrop Kind cluster was not found." }
foreach ($node in $nodes) {
    $labelsJson = docker inspect --format '{{json .Config.Labels}}' $node
    if ($LASTEXITCODE -ne 0) { throw "Could not inspect the Kind node." }
    $labels = $labelsJson | ConvertFrom-Json
    if ($labels.'io.x-k8s.kind.cluster' -ne "swiftdrop") { throw "Refusing to update an unrelated container." }
    ($lines -join "`n") | docker exec -i $node sh -c 'cat > /etc/resolv.conf'
    if ($LASTEXITCODE -ne 0) { throw "Could not configure DNS on $node." }
}
kubectl --context kind-swiftdrop --namespace kube-system rollout restart deployment/coredns
if ($LASTEXITCODE -ne 0) { throw "Could not restart SwiftDrop CoreDNS." }
Write-Host "Updated DNS only on SwiftDrop Kind nodes."
