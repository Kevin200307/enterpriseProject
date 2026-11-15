from typing import Any

from app.security.principal import AssistantPrincipal
from app.tools.admin_reads import AdminReadTools


class FakeIdentity:
    async def users(self, token: str, role: str | None, status: str | None) -> Any:
        return {"content": [{"id": "user-1", "role": role, "status": status}]}


class FakeMerchants:
    async def list_all(self, token: str, status: str | None) -> Any:
        return {"content": [{"id": "merchant-1", "status": status}]}


class FakePricing:
    async def rules(self, token: str) -> Any:
        return [{"id": "rule-1"}]

    async def zones(self, token: str) -> Any:
        return [{"id": "zone-1"}]


class FakeNotifications:
    async def failures(self, token: str) -> Any:
        return {"content": [{"id": "notification-1", "status": "FAILED"}]}


def tools() -> AdminReadTools:
    return AdminReadTools(
        FakeIdentity(),
        FakeMerchants(),
        FakePricing(),
        FakeNotifications(),  # type: ignore[arg-type]
    )


async def test_admin_can_read_operational_data() -> None:
    admin = AssistantPrincipal("admin", frozenset({"ADMIN"}), "token")
    assert (await tools().users(admin, "COURIER", "ACTIVE"))["users"]
    assert (await tools().merchants(admin, "PENDING"))["merchants"]
    assert (await tools().pricing(admin))["rules"]
    assert (await tools().notification_failures(admin))["notificationFailures"]


async def test_non_admin_cannot_read_admin_data() -> None:
    customer = AssistantPrincipal("customer", frozenset({"CUSTOMER"}), "token")
    assert "error" in await tools().users(customer)
    assert "error" in await tools().notification_failures(customer)
