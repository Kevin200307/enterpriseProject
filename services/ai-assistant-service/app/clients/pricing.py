from typing import Any

import httpx

from app.clients.base import DownstreamClient


class PricingClient(DownstreamClient):
    def __init__(
        self,
        base_url: str,
        timeout_seconds: float,
        *,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        super().__init__("pricing-service", base_url, timeout_seconds, transport=transport)

    async def estimate(self, request: dict[str, object], token: str) -> Any:
        return await self.request_json(
            "POST",
            "/api/v1/pricing/estimate",
            token,
            json_body=request,
        )
