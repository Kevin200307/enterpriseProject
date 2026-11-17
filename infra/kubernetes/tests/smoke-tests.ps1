[CmdletBinding()]
param(
    [string]$BaseUrl = "http://localhost:8088",
    [string]$HostName = "swiftdrop.local",
    [string]$BearerToken = "",
    [switch]$Workflow
)

$ErrorActionPreference = "Stop"
$headers = @{Host = $HostName}
$web = Invoke-WebRequest -UseBasicParsing -TimeoutSec 30 -Uri "$BaseUrl/healthz" -Headers $headers
if ($web.StatusCode -ne 200 -or $web.Content -notmatch "ok") {
    throw "Frontend health check failed."
}

try {
    Invoke-WebRequest -UseBasicParsing -TimeoutSec 30 -Uri "$BaseUrl/api/v1/assistant/conversations/00000000-0000-0000-0000-000000000000" -Headers $headers
    throw "Protected assistant endpoint unexpectedly accepted an anonymous request."
} catch {
    $status = $_.Exception.Response.StatusCode.value__
    if ($status -notin @(401, 403)) { throw }
}

function Invoke-SmokeJson([string]$Method, [string]$Path, $Body, [hashtable]$ExtraHeaders = @{}) {
    $requestHeaders = $headers.Clone()
    foreach ($entry in $ExtraHeaders.GetEnumerator()) { $requestHeaders[$entry.Key] = $entry.Value }
    $request = @{ Method = $Method; Uri = "$BaseUrl$Path"; Headers = $requestHeaders; TimeoutSec = 30 }
    if ($null -ne $Body) {
        $request.ContentType = "application/json"
        $request.Body = $Body | ConvertTo-Json -Depth 10 -Compress
    }
    try {
        Invoke-RestMethod @request
    } catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { "no HTTP response" }
        throw "Smoke request $Method $Path failed ($status)."
    }
}

if ($Workflow) {
    if (-not ([uri]$BaseUrl).IsLoopback -or $HostName -ne "swiftdrop.local") {
        throw "Synthetic workflow writes are restricted to the local SwiftDrop ingress."
    }
    $runId = [guid]::NewGuid().ToString("N")
    $email = "smoke-$runId@swiftdrop.local"
    $password = [guid]::NewGuid().ToString("N") + "Aa1!"
    Invoke-SmokeJson POST "/api/v1/auth/register" @{
        email = $email; password = $password; fullName = "Kubernetes Smoke Test"; phone = "+94770000000"
    } | Out-Null
    $login = Invoke-SmokeJson POST "/api/v1/auth/login" @{ email = $email; password = $password }
    if (-not $login.accessToken) { throw "Login did not issue an access token." }
    $BearerToken = $login.accessToken
    $auth = @{ Authorization = "Bearer $BearerToken"; "Idempotency-Key" = "smoke-$runId" }
    $quote = Invoke-SmokeJson POST "/api/v1/pricing/estimate" @{
        fromCity = "Colombo"; toCity = "Colombo"; size = "SMALL"; weightKg = 1.0; express = $false
    } $auth
    if ($quote.total -le 0 -or $quote.currency -ne "LKR") { throw "Pricing estimate failed." }
    $address = @{
        contactName = "Smoke Test"; phone = "+94770000000"; line1 = "1 Test Road"
        city = "Colombo"; postalCode = "00100"; zone = $quote.zoneFrom
    }
    $booking = @{ pickup = $address; dropoff = $address; size = "SMALL"; weightKg = 1.0; express = $false }
    $parcel = Invoke-SmokeJson POST "/api/v1/parcels" $booking $auth
    $duplicate = Invoke-SmokeJson POST "/api/v1/parcels" $booking $auth
    if (-not $parcel.id -or $duplicate.id -ne $parcel.id) { throw "Booking idempotency failed." }
    $tracking = $null
    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        try {
            $tracking = Invoke-SmokeJson GET "/api/v1/tracking/$($parcel.trackingNumber)" $null
            break
        } catch {
            if ($_.Exception.Response.StatusCode.value__ -ne 404 -or $attempt -eq 19) { throw }
            Start-Sleep -Seconds 1
        }
    }
    if ($tracking.trackingNumber -ne $parcel.trackingNumber) { throw "Public tracking failed." }
    $delivered = @()
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $notifications = Invoke-SmokeJson GET "/api/v1/notifications/me?page=0&size=20" $null $auth
        if ($null -eq $notifications.content) { throw "Notification query failed." }
        $delivered = @($notifications.content | Where-Object {
            $_.trackingNumber -eq $parcel.trackingNumber -and $_.channel -eq "EMAIL" -and $_.status -eq "SENT"
        })
        if ($delivered.Count -gt 0) { break }
        Start-Sleep -Seconds 1
    }
    if ($delivered.Count -eq 0) { throw "Parcel booking email was not delivered through local mail." }
    $cancelled = Invoke-SmokeJson POST "/api/v1/parcels/$($parcel.id)/cancel" $null $auth
    if ($cancelled.status -ne "CANCELLED") { throw "Parcel cancellation failed." }
    Write-Host "Registration, login, pricing, idempotent booking, tracking, notifications, and cancellation passed."
}

if ($BearerToken) {
    $authorized = $headers.Clone()
    $authorized.Authorization = "Bearer $BearerToken"
    $body = '{"message":"What can you help me with?"}'
    $response = Invoke-WebRequest -UseBasicParsing -TimeoutSec 60 -Method Post -Uri "$BaseUrl/api/v1/assistant/chat/stream" `
        -Headers $authorized -ContentType "application/json" -Body $body
    if ($response.StatusCode -ne 200 -or $response.Content -notmatch "event: done" -or $response.Content -match "event: error") {
        throw "Authenticated assistant SSE smoke test failed."
    }
    if ($response.Headers['Content-Type'] -notmatch 'text/event-stream') { throw "Assistant did not return SSE." }
    $done = [regex]::Match($response.Content, 'event: done\r?\ndata: ([^\r\n]+)')
    $conversationId = ($done.Groups[1].Value | ConvertFrom-Json).conversationId
    $conversation = Invoke-SmokeJson GET "/api/v1/assistant/conversations/$conversationId" $null $authorized
    if ($conversation.messages.Count -lt 2) { throw "Assistant conversation was not persisted." }
}

Write-Host "Ingress, anonymous authorization, and optional assistant SSE smoke tests passed."
