from typing import Any
from uuid import UUID, uuid4

from app.security.principal import AssistantPrincipal
from app.tools.dispatcher import DispatcherTools
from app.workflows.confirmation import ConfirmationService
from app.workflows.operations import InMemoryOperationStore


class FakeDispatchClient:
    async def queue(self, token: str, zone: str | None = None) -> Any:
        return [{"parcelId": "parcel-1", "zone": zone}]

    async def workload(self, token: str) -> Any:
        return [{"courierId": "courier-1", "active": 1}]

    async def suggestions(self, parcel_id: str, token: str) -> Any:
        return [{"courierId": "courier-1"}]

    async def assign(
        self, parcel_id: str, courier_id: str, token: str, idempotency_key: str
    ) -> Any:
        assert idempotency_key
        return {"parcelId": parcel_id, "courierId": courier_id, "status": "ASSIGNED"}

    async def unassign(
        self, assignment_id: str, reason: str, token: str, idempotency_key: str
    ) -> Any:
        assert reason and idempotency_key
        return {"id": assignment_id, "status": "UNASSIGNED"}


def make_tools() -> tuple[DispatcherTools, ConfirmationService]:
    store = InMemoryOperationStore()
    confirmations = ConfirmationService(store)
    return (
        DispatcherTools(FakeDispatchClient(), store, confirmations),  # type: ignore[arg-type]
        confirmations,
    )


async def test_dispatcher_reads_queue_workload_and_suggestions() -> None:
    tools, _ = make_tools()
    principal = AssistantPrincipal("dispatcher", frozenset({"DISPATCHER"}), "token")
    assert (await tools.queue(principal, "COLOMBO"))["queue"]
    assert (await tools.workload(principal))["workload"]
    assert (await tools.suggestions(principal, "parcel-1"))["suggestions"]


async def test_assignment_and_unassignment_require_confirmation() -> None:
    tools, confirmations = make_tools()
    principal = AssistantPrincipal("dispatcher", frozenset({"DISPATCHER"}), "token")
    prepared = await tools.prepare_assignment(uuid4(), principal, "parcel-1", "courier-1")
    operation_id = UUID(str(prepared["operationId"]))
    await confirmations.confirm(operation_id, principal)
    assert (await tools.assign(operation_id, principal))["status"] == "ASSIGNED"

    prepared = await tools.prepare_unassignment(uuid4(), principal, "assignment-1", "Shift ended")
    operation_id = UUID(str(prepared["operationId"]))
    await confirmations.confirm(operation_id, principal)
    assert (await tools.unassign(operation_id, principal))["status"] == "UNASSIGNED"


async def test_customer_cannot_access_dispatch_tools() -> None:
    tools, _ = make_tools()
    customer = AssistantPrincipal("customer", frozenset({"CUSTOMER"}), "token")
    assert "error" in await tools.queue(customer)
    assert "error" in await tools.prepare_assignment(uuid4(), customer, "parcel-1", "courier-1")
