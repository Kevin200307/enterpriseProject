from typing import Any

from app.clients.base import DownstreamError
from app.clients.merchant import MerchantClient


async def merchant_overview(client: MerchantClient, token: str) -> dict[str, Any]:
    try:
        value = await client.me(token)
    except DownstreamError:
        return {"error": "Merchant status is temporarily unavailable."}
    if not isinstance(value, dict):
        return {"error": "Merchant status is temporarily unavailable."}
    allowed = {"id", "businessName", "status", "createdAt", "updatedAt"}
    return {key: item for key, item in value.items() if key in allowed}


async def merchant_orders(
    client: MerchantClient, token: str, query: str | None = None
) -> dict[str, Any]:
    try:
        value = await client.orders(token, query)
    except DownstreamError:
        return {"error": "Merchant orders are temporarily unavailable."}
    return value if isinstance(value, dict) else {"error": "Invalid merchant order response."}


async def webhook_failures(client: MerchantClient, token: str) -> dict[str, Any]:
    try:
        value = await client.webhook_deliveries(token)
    except DownstreamError:
        return {"error": "Webhook delivery history is temporarily unavailable."}
    if not isinstance(value, dict):
        return {"error": "Invalid webhook delivery response."}
    content = value.get("content", [])
    failures = [
        item
        for item in content
        if isinstance(item, dict) and item.get("state") == "FAILED"
    ]
    return {"deliveries": failures, "count": len(failures)}
