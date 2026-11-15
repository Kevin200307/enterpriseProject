from typing import Any

from app.clients.base import DownstreamError
from app.clients.parcel import ParcelClient
from app.tools.models import EmptyArguments


async def list_my_parcels(
    _arguments: EmptyArguments,
    *,
    token: str,
    parcel_client: ParcelClient,
) -> dict[str, Any]:
    try:
        result = await parcel_client.list_mine(token)
    except DownstreamError:
        return {"error": "Your parcel list is temporarily unavailable."}
    if not isinstance(result, dict):
        return {"error": "Your parcel list is temporarily unavailable."}
    parcels = result.get("content")
    summaries = [_summary(parcel) for parcel in parcels] if isinstance(parcels, list) else []
    return {
        "parcels": summaries,
        "totalElements": result.get("totalElements", len(summaries)),
        "totalPages": result.get("totalPages", 0),
    }


def _summary(parcel: object) -> dict[str, Any]:
    if not isinstance(parcel, dict):
        return {}
    pickup_value = parcel.get("pickup")
    dropoff_value = parcel.get("dropoff")
    pickup: dict[str, Any] = pickup_value if isinstance(pickup_value, dict) else {}
    dropoff: dict[str, Any] = dropoff_value if isinstance(dropoff_value, dict) else {}
    return {
        "trackingNumber": parcel.get("trackingNumber"),
        "status": parcel.get("status"),
        "originCity": pickup.get("city"),
        "destinationCity": dropoff.get("city"),
        "size": parcel.get("size"),
        "weightKg": parcel.get("weightKg"),
        "express": parcel.get("express"),
        "fee": parcel.get("fee"),
        "currency": parcel.get("currency"),
        "createdAt": parcel.get("createdAt"),
        "updatedAt": parcel.get("updatedAt"),
    }
