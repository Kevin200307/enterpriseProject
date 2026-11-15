from typing import Any

from app.security.principal import AssistantPrincipal
from app.tools.courier import courier_assignments, update_assigned_parcel_status


class FakeDispatch:
    async def my_assignments(self, token: str) -> Any:
        return [{"parcelId": "p1"}]


class FakeParcels:
    async def update_status(
        self, parcel_id: str, status: str, token: str, note: str | None = None
    ) -> Any:
        return {"id": parcel_id, "status": status}


async def test_courier_can_read_own_assignments_and_update_supported_status() -> None:
    courier = AssistantPrincipal("c", frozenset({"COURIER"}), "fixture")
    assignments = await courier_assignments(FakeDispatch(), courier)  # type: ignore[arg-type]
    updated = await update_assigned_parcel_status(
        FakeParcels(), courier, "p1", "IN_TRANSIT"  # type: ignore[arg-type]
    )
    assert assignments["assignments"] == [{"parcelId": "p1"}]
    assert updated["status"] == "IN_TRANSIT"


async def test_delivered_requires_proof_flow() -> None:
    courier = AssistantPrincipal("c", frozenset({"COURIER"}), "fixture")
    result = await update_assigned_parcel_status(
        FakeParcels(), courier, "p1", "DELIVERED"  # type: ignore[arg-type]
    )
    assert "proof-of-delivery" in str(result["error"])
