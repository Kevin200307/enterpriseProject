param(
    [string]$GatewayUrl = 'http://localhost:8081',
    [Parameter(Mandatory = $true)][string]$DispatcherToken,
    [Parameter(Mandatory = $true)][string]$CustomerToken,
    [Parameter(Mandatory = $true)][string]$CourierToken,
    [Parameter(Mandatory = $true)][guid]$CourierUserId
)
$ErrorActionPreference = 'Stop'
function Invoke-Api([string]$Method, [string]$Path, [string]$Token, $Body = $null, $ExtraHeaders = @{}) {
    $headers = @{ Authorization = "Bearer $Token"; 'X-Request-ID' = "dispatch-smoke-$([guid]::NewGuid())" }
    foreach ($key in $ExtraHeaders.Keys) { $headers[$key] = $ExtraHeaders[$key] }
    $requestArguments = @{ Method = $Method; Uri = "$GatewayUrl$Path"; Headers = $headers; ContentType = 'application/json' }
    if ($null -ne $Body) { $requestArguments.Body = $Body | ConvertTo-Json -Depth 10 }
    Invoke-RestMethod @requestArguments
}
function Assert-True($Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
$profiles = Invoke-Api GET '/api/v1/couriers?size=100' $DispatcherToken
$courier = $profiles.content | Where-Object { $_.userId -eq $CourierUserId.ToString() } | Select-Object -First 1
if ($null -eq $courier) {
    $courier = Invoke-Api POST '/api/v1/couriers' $DispatcherToken @{
        userId = $CourierUserId.ToString(); vehicleType = 'BIKE'; homeZoneCode = 'COLOMBO'
        phone = '+94771234567'; maxDailyLoad = 20; active = $true
    }
}
$parcel = Invoke-Api POST '/api/v1/parcels' $CustomerToken @{
    pickup = @{ contactName = 'Smoke Sender'; phone = '+94771234567'; line1 = '1 Main Street'; city = 'Colombo'; postalCode = '00100'; zone = 'COLOMBO' }
    dropoff = @{ contactName = 'Smoke Recipient'; phone = '+94777654321'; line1 = '2 Lake Road'; city = 'Kandy'; postalCode = '20000'; zone = 'KANDY' }
    size = 'SMALL'; weightKg = 1.25
} @{ 'Idempotency-Key' = "dispatch-smoke-$([guid]::NewGuid())" }
$deadline = [DateTime]::UtcNow.AddSeconds(15)
do {
    $queue = Invoke-Api GET '/api/v1/dispatch/queue?zone=COLOMBO' $DispatcherToken
    if (@($queue | Where-Object { $_.parcelId -eq $parcel.id }).Count -gt 0) { break }
    Start-Sleep -Milliseconds 100
} while ([DateTime]::UtcNow -lt $deadline)
Assert-True (@($queue | Where-Object { $_.parcelId -eq $parcel.id }).Count -eq 1) 'Created parcel did not appear exactly once in queue'
$result = Invoke-Api POST '/api/v1/dispatch/assignments' $DispatcherToken @{ parcelId = $parcel.id; courierId = $courier.id }
if ($null -ne $result -and $result.state -eq 'PENDING') {
    $deadline = [DateTime]::UtcNow.AddSeconds(20)
    do {
        Start-Sleep -Milliseconds 100
        $result = Invoke-Api GET "/api/v1/dispatch/commands/$($result.id)" $DispatcherToken
    } while ($result.state -eq 'PENDING' -and [DateTime]::UtcNow -lt $deadline)
    Assert-True ($result.state -eq 'CONFIRMED') 'Assignment did not confirm'
}
$remote = Invoke-Api GET "/api/v1/parcels/$($parcel.id)" $DispatcherToken
Assert-True ($remote.assigneeId -eq $CourierUserId.ToString()) 'Parcel-service courier identity does not match'
$mine = Invoke-Api GET '/api/v1/couriers/me/assignments' $CourierToken
$assignment = $mine | Where-Object { $_.parcelId -eq $parcel.id } | Select-Object -First 1
Assert-True ($null -ne $assignment) 'Parcel missing from courier assignments'
Assert-True ($assignment.dropoff.phone -ne '+94777654321') 'Recipient phone was not masked before pickup'
$date = [TimeZoneInfo]::ConvertTimeFromUtc([DateTime]::UtcNow, [TimeZoneInfo]::FindSystemTimeZoneById('Sri Lanka Standard Time')).ToString('yyyy-MM-dd')
$null = Invoke-Api GET "/api/v1/couriers/$($courier.id)/manifest?date=$date" $CourierToken
$null = Invoke-Api GET '/api/v1/dispatch/workload' $DispatcherToken
$null = Invoke-Api GET "/api/v1/dispatch/stats/daily?from=$date&to=$date" $DispatcherToken
$null = Invoke-Api DELETE "/api/v1/dispatch/assignments/$($assignment.id)" $DispatcherToken @{ reason = 'Dispatch smoke verification completed' }
$remote = Invoke-Api GET "/api/v1/parcels/$($parcel.id)" $DispatcherToken
Assert-True ($null -eq $remote.assigneeId) 'Parcel-service did not unassign the courier'
Write-Output "PASS: created event -> dispatch queue -> assignment -> parcel confirmation -> courier manifest -> unassignment ($($parcel.trackingNumber))"
