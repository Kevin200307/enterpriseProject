from decimal import Decimal
from typing import Any

from app.clients.base import DownstreamError
from app.clients.pricing import PricingClient
from app.tools.models import EstimateArguments

ESTIMATE_FIELDS = {
    "zoneFrom",
    "zoneTo",
    "baseFee",
    "weightSurcharge",
    "expressSurcharge",
    "total",
    "currency",
    "calculatedAt",
}


async def estimate_fee(
    arguments: EstimateArguments,
    *,
    token: str,
    pricing_client: PricingClient,
) -> dict[str, Any]:
    request: dict[str, object] = {
        "fromCity": arguments.fromCity.strip(),
        "toCity": arguments.toCity.strip(),
        "size": arguments.size,
        "weightKg": str(arguments.weightKg),
        "express": arguments.express,
    }
    try:
        result = await pricing_client.estimate(request, token)
    except DownstreamError:
        return {"error": "Pricing is temporarily unavailable."}
    if not isinstance(result, dict):
        return {"error": "Pricing is temporarily unavailable."}
    return {key: value for key, value in result.items() if key in ESTIMATE_FIELDS}


def decimal_json(value: Decimal) -> str:
    return format(value, "f")
