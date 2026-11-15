from typing import Any
from uuid import UUID

from app.clients.base import DownstreamError
from app.clients.dispatch import DispatchClient
from app.security.principal import AssistantPrincipal
from app.workflows.confirmation import ConfirmationService
from app.workflows.operations import InMemoryOperationStore


class DispatcherTools:
    def __init__(
        self,
        client: DispatchClient,
        operations: InMemoryOperationStore,
        confirmations: ConfirmationService,
    ) -> None:
        self._client = client
        self._operations = operations
        self._confirmations = confirmations

    @staticmethod
    def _allowed(principal: AssistantPrincipal) -> bool:
        return principal.has_role("DISPATCHER") or principal.has_role("ADMIN")

    async def queue(self, principal: AssistantPrincipal, zone: str | None = None) -> dict[str, Any]:
        if not self._allowed(principal):
            return {"error": "Dispatcher permission is required."}
        try:
            value = await self._client.queue(principal.token, zone)
        except DownstreamError:
            return {"error": "The dispatch queue is temporarily unavailable."}
        return {"queue": value if isinstance(value, list) else []}

    async def workload(self, principal: AssistantPrincipal) -> dict[str, Any]:
        if not self._allowed(principal):
            return {"error": "Dispatcher permission is required."}
        try:
            value = await self._client.workload(principal.token)
        except DownstreamError:
            return {"error": "Courier workload is temporarily unavailable."}
        return {"workload": value if isinstance(value, list) else []}

    async def suggestions(self, principal: AssistantPrincipal, parcel_id: str) -> dict[str, Any]:
        if not self._allowed(principal):
            return {"error": "Dispatcher permission is required."}
        try:
            value = await self._client.suggestions(parcel_id, principal.token)
        except DownstreamError:
            return {"error": "Courier suggestions are temporarily unavailable."}
        return {"suggestions": value if isinstance(value, list) else []}

    async def prepare_assignment(
        self,
        conversation_id: UUID,
        principal: AssistantPrincipal,
        parcel_id: str,
        courier_id: str,
    ) -> dict[str, object]:
        if not self._allowed(principal):
            return {"error": "Dispatcher permission is required."}
        operation = await self._operations.prepare(
            conversation_id,
            principal.subject,
            "ASSIGN_COURIER",
            {"parcelId": parcel_id, "courierId": courier_id},
            "DISPATCHER" if principal.has_role("DISPATCHER") else "ADMIN",
        )
        return {
            "operationId": str(operation.id),
            "confirmationRequired": True,
            "expiresAt": operation.expires_at.isoformat(),
            "summary": f"Assign parcel {parcel_id} to courier {courier_id}",
        }

    async def assign(self, operation_id: UUID, principal: AssistantPrincipal) -> dict[str, object]:
        operation = await self._confirmations.claim_for_execution(operation_id, principal)
        if operation.operation_type != "ASSIGN_COURIER":
            return {"error": "The confirmed operation is not an assignment."}
        parcel_id = operation.request_payload.get("parcelId")
        courier_id = operation.request_payload.get("courierId")
        if not isinstance(parcel_id, str) or not isinstance(courier_id, str):
            return {"error": "The assignment request is invalid."}
        try:
            value = await self._client.assign(
                parcel_id, courier_id, principal.token, operation.idempotency_key
            )
        except DownstreamError:
            return {"error": "The assignment was rejected or is temporarily unavailable."}
        return value if isinstance(value, dict) else {"error": "Invalid assignment response."}

    async def prepare_unassignment(
        self,
        conversation_id: UUID,
        principal: AssistantPrincipal,
        assignment_id: str,
        reason: str,
    ) -> dict[str, object]:
        if not self._allowed(principal):
            return {"error": "Dispatcher permission is required."}
        if not reason.strip():
            return {"error": "A reason is required to unassign a courier."}
        operation = await self._operations.prepare(
            conversation_id,
            principal.subject,
            "UNASSIGN_COURIER",
            {"assignmentId": assignment_id, "reason": reason.strip()},
            "DISPATCHER" if principal.has_role("DISPATCHER") else "ADMIN",
        )
        return {
            "operationId": str(operation.id),
            "confirmationRequired": True,
            "expiresAt": operation.expires_at.isoformat(),
            "summary": f"Unassign assignment {assignment_id}: {reason.strip()}",
        }

    async def unassign(
        self, operation_id: UUID, principal: AssistantPrincipal
    ) -> dict[str, object]:
        operation = await self._confirmations.claim_for_execution(operation_id, principal)
        if operation.operation_type != "UNASSIGN_COURIER":
            return {"error": "The confirmed operation is not an unassignment."}
        assignment_id = operation.request_payload.get("assignmentId")
        reason = operation.request_payload.get("reason")
        if not isinstance(assignment_id, str) or not isinstance(reason, str):
            return {"error": "The unassignment request is invalid."}
        try:
            value = await self._client.unassign(
                assignment_id, reason, principal.token, operation.idempotency_key
            )
        except DownstreamError:
            return {"error": "The unassignment was rejected or is temporarily unavailable."}
        return value if isinstance(value, dict) else {"error": "Invalid unassignment response."}
