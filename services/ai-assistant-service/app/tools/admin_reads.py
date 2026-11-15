from typing import Any

from app.clients.base import DownstreamError
from app.clients.identity import IdentityClient
from app.clients.merchant import MerchantClient
from app.clients.notification import NotificationClient
from app.clients.pricing import PricingClient
from app.security.principal import AssistantPrincipal


class AdminReadTools:
    def __init__(
        self,
        identity: IdentityClient,
        merchants: MerchantClient,
        pricing: PricingClient,
        notifications: NotificationClient,
    ) -> None:
        self._identity = identity
        self._merchants = merchants
        self._pricing = pricing
        self._notifications = notifications

    @staticmethod
    def _allowed(principal: AssistantPrincipal) -> bool:
        return principal.has_role("ADMIN")

    async def users(
        self, principal: AssistantPrincipal, role: str | None = None, status: str | None = None
    ) -> dict[str, Any]:
        return await self._read(principal, self._identity.users, "users", role, status)

    async def merchants(
        self, principal: AssistantPrincipal, status: str | None = None
    ) -> dict[str, Any]:
        return await self._read(principal, self._merchants.list_all, "merchants", status)

    async def pricing(self, principal: AssistantPrincipal) -> dict[str, Any]:
        if not self._allowed(principal):
            return {"error": "Administrator permission is required."}
        try:
            rules = await self._pricing.rules(principal.token)
            zones = await self._pricing.zones(principal.token)
        except DownstreamError:
            return {"error": "Pricing configuration is temporarily unavailable."}
        return {"rules": rules, "zones": zones}

    async def notification_failures(self, principal: AssistantPrincipal) -> dict[str, Any]:
        return await self._read(principal, self._notifications.failures, "notificationFailures")

    async def _read(
        self,
        principal: AssistantPrincipal,
        function: Any,
        key: str,
        *arguments: object,
    ) -> dict[str, Any]:
        if not self._allowed(principal):
            return {"error": "Administrator permission is required."}
        try:
            value = await function(principal.token, *arguments)
        except DownstreamError:
            return {"error": f"Administrator {key} data is temporarily unavailable."}
        return {key: value}
