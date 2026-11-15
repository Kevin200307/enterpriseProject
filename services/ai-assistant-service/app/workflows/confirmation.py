from dataclasses import replace
from datetime import UTC, datetime
from uuid import UUID

from app.security.principal import AssistantPrincipal
from app.workflows.operations import (
    InMemoryOperationStore,
    OperationStatus,
    PendingOperation,
    payload_hash,
)


class ConfirmationError(ValueError):
    pass


class ConfirmationService:
    def __init__(self, store: InMemoryOperationStore) -> None:
        self._store = store

    async def confirm(
        self, operation_id: UUID, principal: AssistantPrincipal
    ) -> PendingOperation:
        operation = await self._require_owned(operation_id, principal)
        now = datetime.now(UTC)
        if operation.expires_at <= now:
            await self._store.update(replace(operation, status=OperationStatus.EXPIRED))
            raise ConfirmationError("The pending operation has expired")
        if operation.status != OperationStatus.PENDING_CONFIRMATION:
            raise ConfirmationError("The operation is no longer awaiting confirmation")
        if not principal.has_role(operation.required_role):
            raise ConfirmationError("The operation is no longer authorized")
        if payload_hash(operation.request_payload) != operation.request_hash:
            raise ConfirmationError("The pending operation was altered")
        confirmed = replace(
            operation,
            status=OperationStatus.CONFIRMED,
            confirmed_at=now,
        )
        await self._store.update(confirmed)
        return confirmed

    async def reject(self, operation_id: UUID, principal: AssistantPrincipal) -> PendingOperation:
        operation = await self._require_owned(operation_id, principal)
        if operation.status != OperationStatus.PENDING_CONFIRMATION:
            raise ConfirmationError("The operation is no longer awaiting confirmation")
        rejected = replace(operation, status=OperationStatus.REJECTED)
        await self._store.update(rejected)
        return rejected

    async def claim_for_execution(
        self, operation_id: UUID, principal: AssistantPrincipal
    ) -> PendingOperation:
        await self._require_owned(operation_id, principal)
        claimed = await self._store.transition(
            operation_id,
            OperationStatus.CONFIRMED,
            OperationStatus.EXECUTING,
        )
        if claimed is None:
            raise ConfirmationError("The operation has already been claimed")
        return claimed

    async def _require_owned(
        self, operation_id: UUID, principal: AssistantPrincipal
    ) -> PendingOperation:
        operation = await self._store.get(operation_id)
        if operation is None or operation.owner_id != principal.subject:
            raise ConfirmationError("Pending operation not found")
        return operation
