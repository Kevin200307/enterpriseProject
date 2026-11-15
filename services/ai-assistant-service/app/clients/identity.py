from typing import Any

from app.clients.base import DownstreamClient


class IdentityClient(DownstreamClient):
    def __init__(self, base_url: str, timeout_seconds: float) -> None:
        super().__init__("identity-service", base_url, timeout_seconds)

    async def users(self, token: str, role: str | None = None, status: str | None = None) -> Any:
        return await self.request_json(
            "GET",
            "/api/v1/users",
            token,
            params={"role": role, "status": status, "page": 0, "size": 20},
        )

    async def update_status(self, user_id: str, status: str, token: str) -> Any:
        return await self.request_json(
            "PATCH", f"/api/v1/users/{user_id}/status", token, json_body={"status": status}
        )

    async def update_roles(self, user_id: str, roles: list[str], token: str) -> Any:
        return await self.request_json(
            "PATCH", f"/api/v1/users/{user_id}/roles", token, json_body={"roles": roles}
        )
