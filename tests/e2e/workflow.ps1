param(
    [Parameter(Mandatory)][string]$GatewayUrl,
    [Parameter(Mandatory)][int]$Run
)

$ErrorActionPreference = "Stop"
$password = "E2eCustomer123!"
$email = "customer-$Run@e2e.swiftdrop.local"
$headers = @{ Accept = "application/json, application/problem+json" }

function Invoke-Json([string]$Method, [string]$Path, $Body, [hashtable]$ExtraHeaders = @{}) {
    $requestHeaders = $headers.Clone()
    foreach ($entry in $ExtraHeaders.GetEnumerator()) { $requestHeaders[$entry.Key] = $entry.Value }
    $args = @{ Method = $Method; Uri = "$GatewayUrl$Path"; Headers = $requestHeaders; TimeoutSec = 20 }
    if ($null -ne $Body) {
        $args.ContentType = "application/json"
        $args.Body = ($Body | ConvertTo-Json -Depth 10 -Compress)
    }
    Invoke-RestMethod @args
}

Invoke-Json POST "/api/v1/auth/register" @{
    email = $email; password = $password; fullName = "E2E Customer $Run"; phone = "+9477000001$Run"
} | Out-Null
$login = Invoke-Json POST "/api/v1/auth/login" @{ email = $email; password = $password }
$auth = @{ Authorization = "Bearer $($login.accessToken)"; "Idempotency-Key" = "e2e-booking-$Run" }
$address = @{ contactName = "E2E User"; phone = "+94770000000"; line1 = "1 Test Road"; city = "Colombo"; postalCode = "00100" }
$dropoff = $address.Clone()
$dropoff.line1 = "2 Test Road"
$parcel = Invoke-Json POST "/api/v1/parcels" @{
    pickup = $address; dropoff = $dropoff; size = "SMALL"; weightKg = 1.0; express = $false
} $auth

# Reusing the key must return the same logical booking rather than creating another parcel.
$duplicate = Invoke-Json POST "/api/v1/parcels" @{
    pickup = $address; dropoff = $dropoff; size = "SMALL"; weightKg = 1.0; express = $false
} $auth
if ($parcel.id -ne $duplicate.id) { throw "Booking idempotency failed" }

$tracking = Invoke-Json GET "/api/v1/tracking/$($parcel.trackingNumber)" $null
if ($tracking.trackingNumber -ne $parcel.trackingNumber) { throw "Public tracking did not return the booked parcel" }

$mine = Invoke-Json GET "/api/v1/notifications/me?page=0&size=20" $null $auth
if ($null -eq $mine.content) { throw "Notification response is not pageable" }

$cancelled = Invoke-Json POST "/api/v1/parcels/$($parcel.id)/cancel" $null $auth
if ($cancelled.status -ne "CANCELLED") { throw "Cancellation did not reach CANCELLED" }

# Privileged lifecycle paths are exercised when CI supplies seeded role tokens.
if ($env:E2E_DISPATCHER_TOKEN -and $env:E2E_COURIER_TOKEN -and $env:E2E_COURIER_ID) {
    $dispatch = @{ Authorization = "Bearer $env:E2E_DISPATCHER_TOKEN" }
    $courier = @{ Authorization = "Bearer $env:E2E_COURIER_TOKEN" }
    $deliveryDropoff = $address.Clone()
    $deliveryDropoff.line1 = "3 Test Road"
    $delivery = Invoke-Json POST "/api/v1/parcels" @{
        pickup = $address; dropoff = $deliveryDropoff; size = "MEDIUM"; weightKg = 2.0; express = $true
    } (@{ Authorization = $auth.Authorization; "Idempotency-Key" = "e2e-delivery-$Run" })
    Invoke-Json POST "/api/v1/dispatch/assignments" @{ parcelId = $delivery.id; courierId = $env:E2E_COURIER_ID } $dispatch | Out-Null
    Invoke-Json PATCH "/api/v1/parcels/$($delivery.id)/status" @{ status = "PICKED_UP" } $courier | Out-Null
    Invoke-Json PATCH "/api/v1/parcels/$($delivery.id)/status" @{ status = "FAILED"; failedReason = "RECIPIENT_UNAVAILABLE" } $courier | Out-Null
    Invoke-Json PATCH "/api/v1/parcels/$($delivery.id)/status" @{ status = "OUT_FOR_DELIVERY"; note = "reattempt" } $courier | Out-Null
}
