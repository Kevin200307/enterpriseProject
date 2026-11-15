import asyncio
import hashlib
import json
from dataclasses import dataclass, replace
from datetime import UTC, datetime, timedelta
from enum import StrEnum
from uuid import UUID, uuid4


class OperationStatus(StrEnum):
    PENDING_CONFIRMATION = "PENDING_CONFIRMATION"
    CONFIRMED = "CONFIRMED"
    EXECUTING = "EXECUTING"
    COMPLETED = "COMPLETED"
    REJECTED = "REJECTED"
    EXPIRED = "EXPIRED"
    FAILED = "FAILED"


@dataclass(frozen=True, slots=True)
class PendingOperation:
    id: UUID
    conversation_id: UUID
    owner_id: str
    operation_type: str
    request_payload: dict[str, object]
    request_hash: str
    required_role: str
    idempotency_key: str
    status: OperationStatus
    created_at: datetime
    expires_at: datetime
    confirmed_at: datetime | None = None
    result: dict[str, object] | None = None


def payload_hash(payload: dict[str, object]) -> str:
    encoded = json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
    return hashlib.sha256(encoded).hexdigest()


class InMemoryOperationStore:
    def __init__(self) -> None:
        self._operations: dict[UUID, PendingOperation] = {}
        self._lock = asyncio.Lock()

    async def prepare(
        self,
        conversation_id: UUID,
        owner_id: str,
        operation_type: str,
        request_payload: dict[str, object],
        required_role: str,
        *,
        ttl: timedelta = timedelta(minutes=10),
    ) -> PendingOperation:
        now = datetime.now(UTC)
        operation = PendingOperation(
            id=uuid4(),
            conversation_id=conversation_id,
            owner_id=owner_id,
            operation_type=operation_type,
            request_payload=request_payload,
            request_hash=payload_hash(request_payload),
            required_role=required_role,
            idempotency_key=str(uuid4()),
            status=OperationStatus.PENDING_CONFIRMATION,
            created_at=now,
            expires_at=now + ttl,
        )
        async with self._lock:
            self._operations[operation.id] = operation
        return operation

    async def get(self, operation_id: UUID) -> PendingOperation | None:
        async with self._lock:
            return self._operations.get(operation_id)

    async def update(self, operation: PendingOperation) -> None:
        async with self._lock:
            self._operations[operation.id] = operation

    async def transition(
        self,
        operation_id: UUID,
        expected: OperationStatus,
        target: OperationStatus,
    ) -> PendingOperation | None:
        async with self._lock:
            current = self._operations.get(operation_id)
            if current is None or current.status != expected:
                return None
            updated = replace(current, status=target)
            self._operations[operation_id] = updated
            return updated
