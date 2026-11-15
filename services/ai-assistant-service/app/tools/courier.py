from typing import Any

from app.clients.base import DownstreamError
from app.clients.dispatch import DispatchClient
from app.clients.parcel import ParcelClient
from app.security.principal import AssistantPrincipal

ALLOWED_COURIER_TRANSITIONS = {"PICKED_UP", "IN_TRANSIT", "OUT_FOR_DELIVERY", "FAILED"}


async def courier_assignments(
    client: DispatchClient, principal: AssistantPrincipal
) -> dict[str, Any]:
    if not principal.has_role("COURIER"):
        return {"error": "Courier permission is required."}
    try:
        value = await client.my_assignments(principal.token)
    except DownstreamError:
        return {"error": "Courier assignments are temporarily unavailable."}
    return {"assignments": value if isinstance(value, list) else []}


async def update_assigned_parcel_status(
    client: ParcelClient,
    principal: AssistantPrincipal,
    parcel_id: str,
    status: str,
    note: str | None = None,
) -> dict[str, Any]:
    if not principal.has_role("COURIER"):
        return {"error": "Courier permission is required."}
    if status == "DELIVERED":
        return {"error": "Use the proof-of-delivery flow before marking a parcel delivered."}
    if status not in ALLOWED_COURIER_TRANSITIONS:
        return {"error": "That courier status transition is not supported."}
    try:
        value = await client.update_status(parcel_id, status, principal.token, note)
    except DownstreamError:
        return {"error": "The status update was rejected or is temporarily unavailable."}
    return value if isinstance(value, dict) else {"error": "Invalid status response."}
