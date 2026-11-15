from typing import Any

from app.clients.base import DownstreamError, DownstreamNotFound
from app.clients.parcel import ParcelClient
from app.tools.models import TrackingArguments

TRACKING_FIELDS = {
    "trackingNumber",
    "status",
    "originCity",
    "destinationCity",
    "courierFirstName",
    "eta",
    "updatedAt",
    "timeline",
}


async def track_parcel(
    arguments: TrackingArguments,
    *,
    token: str,
    parcel_client: ParcelClient,
) -> dict[str, Any]:
    try:
        result = await parcel_client.track(arguments.trackingNumber, token)
    except DownstreamNotFound:
        return {"error": "No parcel was found for that tracking number."}
    except DownstreamError:
        return {"error": "Parcel tracking is temporarily unavailable."}
    if not isinstance(result, dict):
        return {"error": "Parcel tracking is temporarily unavailable."}
    return {key: value for key, value in result.items() if key in TRACKING_FIELDS}
