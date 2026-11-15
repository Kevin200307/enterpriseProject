from typing import Any

from app.security.principal import AssistantPrincipal
from app.tools.merchant import merchant_overview, webhook_failures
from app.tools.registry import allowed_tools


class FakeMerchantClient:
    async def me(self, token: str) -> Any:
        return {"id": "m1", "businessName": "Shop", "status": "APPROVED", "secret": "no"}

    async def webhook_deliveries(self, token: str) -> Any:
        return {"content": [{"id": "1", "state": "FAILED"}, {"id": "2", "state": "SENT"}]}


async def test_merchant_reads_are_scoped_and_minimized() -> None:
    client = FakeMerchantClient()
    overview = await merchant_overview(client, "fixture")  # type: ignore[arg-type]
    failures = await webhook_failures(client, "fixture")  # type: ignore[arg-type]
    assert overview == {"id": "m1", "businessName": "Shop", "status": "APPROVED"}
    assert failures["count"] == 1


def test_merchant_tools_require_merchant_role() -> None:
    merchant = AssistantPrincipal("m", frozenset({"MERCHANT"}), "fixture")
    customer = AssistantPrincipal("c", frozenset({"CUSTOMER"}), "fixture")
    merchant_names = {tool.name for tool in allowed_tools(merchant)}
    customer_names = {tool.name for tool in allowed_tools(customer)}
    assert {"merchantOverview", "merchantOrders", "webhookFailures"} <= merchant_names
    assert "merchantOverview" not in customer_names
