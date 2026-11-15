from typing import Any

from app.clients.base import DownstreamClient


class MerchantClient(DownstreamClient):
    def __init__(self, base_url: str, timeout_seconds: float) -> None:
        super().__init__("merchant-service", base_url, timeout_seconds)

    async def me(self, token: str) -> Any:
        return await self.request_json("GET", "/api/v1/merchants/me", token)

    async def orders(self, token: str, query: str | None = None) -> Any:
        return await self.request_json(
            "GET",
            "/api/v1/merchants/orders",
            token,
            params={"q": query, "page": 0, "size": 20},
        )

    async def webhook_deliveries(self, token: str) -> Any:
        return await self.request_json(
            "GET",
            "/api/v1/merchants/me/webhook/deliveries",
            token,
            params={"page": 0, "size": 20},
        )

    async def list_all(self, token: str, status: str | None = None) -> Any:
        return await self.request_json(
            "GET", "/api/v1/merchants", token, params={"status": status, "page": 0, "size": 20}
        )
