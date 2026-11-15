[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8088",
    [string]$HostName = "swiftdrop.local",
    [string]$BearerToken = ""
)

$ErrorActionPreference = "Stop"
$headers = @{Host = $HostName}
$web = Invoke-WebRequest -Uri "$BaseUrl/healthz" -Headers $headers
if ($web.StatusCode -ne 200 -or $web.Content -notmatch "ok") {
    throw "Frontend health check failed."
}

try {
    Invoke-WebRequest -Uri "$BaseUrl/api/v1/assistant/conversations/00000000-0000-0000-0000-000000000000" -Headers $headers
    throw "Protected assistant endpoint unexpectedly accepted an anonymous request."
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    if ($status -notin @(401, 403)) { throw }
}

if ($BearerToken) {
    $authorized = $headers.Clone()
    $authorized.Authorization = "Bearer $BearerToken"
    $body = '{"message":"What can you help me with?"}'
    $response = Invoke-WebRequest -Method Post -Uri "$BaseUrl/api/v1/assistant/chat/stream" `
        -Headers $authorized -ContentType "application/json" -Body $body
    if ($response.StatusCode -ne 200 -or $response.Content -notmatch "event: done") {
        throw "Authenticated assistant SSE smoke test failed."
    }
}

Write-Host "Ingress, anonymous authorization, and optional assistant SSE smoke tests passed."
