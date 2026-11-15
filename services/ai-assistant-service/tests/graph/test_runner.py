from collections.abc import AsyncIterator
from typing import Any

import pytest

from app.graph.runner import AssistantRunner
from app.security.principal import AssistantPrincipal


class FakeParcelClient:
    async def track(self, tracking_number: str, token: str) -> dict[str, Any]:
        return {"trackingNumber": tracking_number, "status": "IN_TRANSIT"}

    async def list_mine(self, token: str, *, page: int = 0, size: int = 10) -> dict[str, Any]:
        return {"content": [], "totalElements": 0, "totalPages": 0}


class FakePricingClient:
    pass


async def collect(stream: AsyncIterator[object]) -> list[object]:
    return [event async for event in stream]


@pytest.mark.asyncio
async def test_tracking_runs_tool_and_streams_result() -> None:
    runner = AssistantRunner(FakeParcelClient(), FakePricingClient())  # type: ignore[arg-type]
    principal = AssistantPrincipal("user", frozenset({"CUSTOMER"}), "fixture")
    events = await collect(runner.run("Track SD-12AB34CD", principal))
    assert [event.event for event in events] == ["tool", "token"]  # type: ignore[attr-defined]
    assert "IN_TRANSIT" in events[-1].data["text"]  # type: ignore[attr-defined,operator]


@pytest.mark.asyncio
async def test_admin_request_is_denied_for_customer() -> None:
    runner = AssistantRunner(FakeParcelClient(), FakePricingClient())  # type: ignore[arg-type]
    principal = AssistantPrincipal("user", frozenset({"CUSTOMER"}), "fixture")
    events = await collect(runner.run("Approve merchant", principal))
    assert "permission" in events[0].data["text"]  # type: ignore[attr-defined,operator]
