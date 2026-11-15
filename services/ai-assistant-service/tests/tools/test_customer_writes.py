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

    async def details_by_tracking(self, tracking_number: str, token: str) -> Any:
        return {"id": str(uuid4()), "trackingNumber": tracking_number, "status": "CREATED"}

    async def cancel(self, parcel_id: str, token: str) -> Any:
        return {"id": parcel_id, "trackingNumber": "SD-12AB34CD", "status": "CANCELLED"}


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
        FakeParcelClient(),
        FakePricingClient(),
        store,
        confirmations,  # type: ignore[arg-type]
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


async def test_prepare_confirm_and_cancel_eligible_delivery() -> None:
    store = InMemoryOperationStore()
    confirmations = ConfirmationService(store)
    tools = CustomerWriteTools(
        FakeParcelClient(),
        FakePricingClient(),
        store,
        confirmations,  # type: ignore[arg-type]
    )
    principal = AssistantPrincipal("owner", frozenset({"CUSTOMER"}), "fixture")
    prepared = await tools.prepare_cancellation(uuid4(), principal, "SD-12AB34CD")
    operation_id = UUID(str(prepared["operationId"]))
    await confirmations.confirm(operation_id, principal)

    cancelled = await tools.cancel_delivery(operation_id, principal)
    assert cancelled["status"] == "CANCELLED"


async def test_non_created_delivery_is_not_prepared_for_cancellation() -> None:
    class InTransitParcelClient(FakeParcelClient):
        async def details_by_tracking(self, tracking_number: str, token: str) -> Any:
            return {"id": str(uuid4()), "status": "IN_TRANSIT"}

    store = InMemoryOperationStore()
    confirmations = ConfirmationService(store)
    tools = CustomerWriteTools(
        InTransitParcelClient(),  # type: ignore[arg-type]
        FakePricingClient(),  # type: ignore[arg-type]
        store,
        confirmations,
    )
    principal = AssistantPrincipal("owner", frozenset({"CUSTOMER"}), "fixture")
    result = await tools.prepare_cancellation(uuid4(), principal, "SD-12AB34CD")
    assert "Only deliveries in CREATED status" in str(result["error"])
