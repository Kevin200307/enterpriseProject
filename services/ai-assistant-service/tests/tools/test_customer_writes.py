from decimal import Decimal
from typing import Any
from uuid import UUID, uuid4

from app.models.delivery import DeliveryAddress, DeliveryDraft
from app.security.principal import AssistantPrincipal
from app.tools.customer_writes import CustomerWriteTools
from app.workflows.confirmation import ConfirmationService
from app.workflows.operations import InMemoryOperationStore


class FakeParcelClient:
    async def book(self, request: dict[str, object], token: str, idempotency_key: str) -> Any:
        assert idempotency_key
        return {
            "id": str(uuid4()),
            "trackingNumber": "SD-12AB34CD",
            "status": "CREATED",
            "fee": 800,
            "currency": "LKR",
        }


class FakePricingClient:
    async def estimate(self, request: dict[str, object], token: str) -> Any:
        return {"total": 800, "currency": "LKR"}


def address(city: str) -> DeliveryAddress:
    return DeliveryAddress(
        contactName="Test",
        phone="+94771234567",
        line1="1 Main Street",
        city=city,
        postalCode="10400",
        zone="WESTERN",
    )


async def test_prepare_confirm_and_create_delivery() -> None:
    store = InMemoryOperationStore()
    confirmations = ConfirmationService(store)
    tools = CustomerWriteTools(
        FakeParcelClient(), FakePricingClient(), store, confirmations  # type: ignore[arg-type]
    )
    principal = AssistantPrincipal("owner", frozenset({"CUSTOMER"}), "fixture")
    draft = DeliveryDraft(
        pickup=address("Moratuwa"),
        dropoff=address("Colombo"),
        size="MEDIUM",
        weightKg=Decimal("3"),
        express=True,
    )
    prepared = await tools.prepare_delivery(uuid4(), principal, draft)
    operation_id = prepared["operationId"]
    assert prepared["confirmationRequired"] is True

    await confirmations.confirm(UUID(str(operation_id)), principal)
    created = await tools.create_delivery(UUID(str(operation_id)), principal)
    assert created["trackingNumber"] == "SD-12AB34CD"
