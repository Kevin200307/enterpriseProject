from typing import Any

from app.clients.base import DownstreamClient


class NotificationClient(DownstreamClient):
    def __init__(self, base_url: str, timeout_seconds: float) -> None:
        super().__init__("notification-service", base_url, timeout_seconds)

    async def failures(self, token: str) -> Any:
        return await self.request_json(
            "GET",
            "/api/v1/notifications",
            token,
            params={"status": "FAILED", "page": 0, "size": 20},
        )

    async def resend(self, notification_id: str, token: str) -> Any:
        return await self.request_json(
            "POST", f"/api/v1/notifications/{notification_id}/resend", token
        )
