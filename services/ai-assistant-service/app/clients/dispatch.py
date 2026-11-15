from typing import Any

from app.clients.base import DownstreamClient


class DispatchClient(DownstreamClient):
    def __init__(self, base_url: str, timeout_seconds: float) -> None:
        super().__init__("dispatch-service", base_url, timeout_seconds)

    async def my_assignments(self, token: str) -> Any:
        return await self.request_json("GET", "/api/v1/couriers/me/assignments", token)

    async def queue(self, token: str, zone: str | None = None) -> Any:
        return await self.request_json(
            "GET", "/api/v1/dispatch/queue", token, params={"zone": zone}
        )

    async def workload(self, token: str) -> Any:
        return await self.request_json("GET", "/api/v1/dispatch/workload", token)

    async def suggestions(self, parcel_id: str, token: str) -> Any:
        return await self.request_json(
            "GET", "/api/v1/dispatch/suggest", token, params={"parcelId": parcel_id}
        )
