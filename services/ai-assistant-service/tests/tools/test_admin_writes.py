from typing import Any
from uuid import UUID, uuid4

from app.security.principal import AssistantPrincipal
from app.tools.admin_writes import AdminWriteTools
from app.workflows.confirmation import ConfirmationService
from app.workflows.operations import InMemoryOperationStore


class FakeIdentity:
    async def update_status(self, user_id: str, status: str, token: str) -> Any:
        return {"id": user_id, "status": status}

    async def update_roles(self, user_id: str, roles: list[str], token: str) -> Any:
        return {"id": user_id, "roles": roles}


class FakeMerchants:
    async def decide(
        self, merchant_id: str, decision: str, token: str, reason: str | None = None
    ) -> Any:
        return {"id": merchant_id, "status": decision, "reason": reason}


class FakeNotifications:
    async def resend(self, notification_id: str, token: str) -> Any:
        return {"id": notification_id, "status": "SENT"}


def make_tools() -> tuple[AdminWriteTools, ConfirmationService]:
    store = InMemoryOperationStore()
    confirmation = ConfirmationService(store)
    return (
        AdminWriteTools(
            FakeIdentity(),  # type: ignore[arg-type]
            FakeMerchants(),  # type: ignore[arg-type]
            FakeNotifications(),  # type: ignore[arg-type]
            store,
            confirmation,
        ),
        confirmation,
    )


async def test_admin_action_requires_confirmation() -> None:
    tools, confirmation = make_tools()
    admin = AssistantPrincipal("admin", frozenset({"ADMIN"}), "token")
    prepared = await tools.prepare_user_status(uuid4(), admin, "user-1", "SUSPENDED")
    operation_id = UUID(str(prepared["operationId"]))
    await confirmation.confirm(operation_id, admin)
    assert (await tools.execute(operation_id, admin))["status"] == "SUSPENDED"


async def test_admin_merchant_rejection_requires_reason() -> None:
    tools, _ = make_tools()
    admin = AssistantPrincipal("admin", frozenset({"ADMIN"}), "token")
    result = await tools.prepare_merchant_decision(uuid4(), admin, "merchant-1", "REJECT")
    assert "reason" in str(result["error"])


async def test_non_admin_cannot_prepare_admin_action() -> None:
    tools, _ = make_tools()
    customer = AssistantPrincipal("customer", frozenset({"CUSTOMER"}), "token")
    result = await tools.prepare_notification_resend(uuid4(), customer, "notification-1")
    assert "error" in result
