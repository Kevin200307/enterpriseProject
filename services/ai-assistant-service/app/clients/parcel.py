from typing import Any

import httpx

from app.clients.base import DownstreamClient


class ParcelClient(DownstreamClient):
    def __init__(
        self,
        base_url: str,
        timeout_seconds: float,
        *,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        super().__init__("parcel-service", base_url, timeout_seconds, transport=transport)

    async def track(self, tracking_number: str, token: str) -> Any:
        return await self.request_json(
            "GET",
            f"/api/v1/parcels/by-tracking/{tracking_number}",
            token,
        )

    async def list_mine(self, token: str, *, page: int = 0, size: int = 10) -> Any:
        return await self.request_json(
            "GET",
            "/api/v1/parcels",
            token,
            params={"page": page, "size": size},
        )
